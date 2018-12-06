package controllers.sysadmin

import controllers.UploadedFileControllerHelper.TemporaryUploadedFile
import controllers.admin.CaseController.CaseFormData
import controllers.admin.{CaseController, OwnersController}
import controllers.sysadmin.DataImportController._
import controllers.sysadmin.SpreadsheetContentsHandler.{Cell, Row, Sheet}
import controllers.{BaseController, UploadedFileControllerHelper}
import domain.{CaseNoteSave, OwnerSave, Team, Teams}
import helpers.ServiceResults
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler
import org.apache.poi.xssf.eventusermodel.{ReadOnlySharedStringsTable, XSSFReader, XSSFSheetXMLHandler}
import org.apache.poi.xssf.usermodel.XSSFComment
import org.xml.sax.InputSource
import org.xml.sax.helpers.XMLReaderFactory
import play.api.Configuration
import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData}
import services.tabula.ProfileService
import services.{CaseService, EnquiryService, SecurityService}
import warwick.sso.{UniversityID, UserLookupService, Usercode}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object DataImportController {
  val TeamColumnHeading = "Team"
  val NotesColumnHeading = "Notes"
  val CaseOwnerColumnHeading = "Case owner"

  val ColumnMappings: Map[String, String] = Map(
    "Client" -> "clients[0]",
    "Subject" -> "case.subject",
    "Cause" -> "case.cause",
  )

  val AllColumnHeadings: Seq[String] = Seq(TeamColumnHeading, NotesColumnHeading, CaseOwnerColumnHeading) ++ ColumnMappings.keys
}

@Singleton
class DataImportController @Inject()(
  securityService: SecurityService,
  profileService: ProfileService,
  enquiryService: EnquiryService,
  caseService: CaseService,
  userLookupService: UserLookupService,
  configuration: Configuration,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  def importForm(): Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.importForm())
  }

  def importCases(): Action[MultipartFormData[TemporaryUploadedFile]] = RequireSysadmin(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    request.body.file("file").map { file =>
      val pkg = OPCPackage.open(file.ref.in.openStream())
      val sst = new ReadOnlySharedStringsTable(pkg)
      val reader = new XSSFReader(pkg)
      val styles = reader.getStylesTable

      val handler = new SpreadsheetContentsHandler

      val parser = {
        val p = XMLReaderFactory.createXMLReader("org.apache.xerces.parsers.SAXParser")
        p.setContentHandler(new XSSFSheetXMLHandler(styles, sst, handler, false))
        p
      }

      val sheets = reader.getSheetsData.asInstanceOf[XSSFReader.SheetIterator]
      while (sheets.hasNext) {
        val sheet = sheets.next()

        try {
          val currentSheetName = sheets.getSheetName
          handler.startSheet(currentSheetName)
          parser.parse(new InputSource(sheet))
          handler.endSheet(currentSheetName)
        } finally {
          sheet.close()
        }
      }

      val forms: Seq[((String, Row), (Team, Form[CaseFormData], Option[Form[OwnerSave]], Seq[CaseNoteSave]))] = handler.parsedSheets.toSeq.flatMap { case (sheetName, rows) =>
        rows.flatMap { row =>
          // Need at least the team
          row.values.get(TeamColumnHeading)
            .flatMap { cell => Teams.all.find { t => t.id == cell.formattedValue || t.name == cell.formattedValue } }
            .map { team =>
              val form = CaseController.form(
                team,
                profileService,
                enquiryService,
                Set.empty,
                None
              ).bind(row.values.filterKeys(ColumnMappings.contains).map { case (k, cell) => ColumnMappings(k) -> cell.formattedValue })

              // Has an owner been passed?
              val ownerForm =
                row.values.get(CaseOwnerColumnHeading)
                  .flatMap { cell =>
                    if (cell.formattedValue.forall(_.isDigit)) {
                      val universityID = UniversityID(cell.formattedValue)
                      userLookupService.getUsers(Seq(universityID)).toOption.flatMap(_.get(universityID))
                    } else userLookupService.getUser(Usercode(cell.formattedValue)).toOption
                  }
                  .map { user =>
                    // TODO this doesn't validate
                    OwnersController.ownersForm.bind(Map("owners[0]" -> user.usercode.string))
                  }

              val generalNote: Option[CaseNoteSave] =
                row.values.get(NotesColumnHeading)
                  .filter(_.formattedValue.hasText)
                  .map { cell =>
                    CaseNoteSave(
                      cell.formattedValue,
                      Usercode("system"),
                      None
                    )
                  }

              val extraValues = row.values.filterKeys(!AllColumnHeadings.contains(_))

              val migrationNote: Option[CaseNoteSave] =
                if (extraValues.nonEmpty)
                  Some(CaseNoteSave(
                    extraValues.map { case (k, cell) => s"$k: ${cell.formattedValue}" }.mkString("\n"),
                    Usercode("system"),
                    None
                  ))
                else None

              ((sheetName, row), (team, form, ownerForm, Seq(generalNote, migrationNote).flatten))
            }
        }
      }

      val (invalid, valid) = forms.partition { case (_, (_, caseForm, ownerForm, _)) =>
        caseForm.hasErrors || ownerForm.exists(_.hasErrors)
      }

      if (invalid.nonEmpty) {
        // Just show errors
        Future.successful(Ok(views.html.sysadmin.importErrors(invalid, valid)))
      } else if (valid.isEmpty) {
        // Redirect back, empty spreadsheet
        Future.successful(
          Redirect(routes.DataImportController.importForm())
            .flashing("warning" -> Messages("flash.import.empty"))
        )
      } else {
        // Run the import
        ServiceResults.futureSequence(forms.map { case (_, (team, caseForm, ownerForm, notes)) =>
          val caseData = caseForm.get

          caseService.create(caseData.`case`, caseData.clients, Set.empty, team, None, None)
            .successFlatMapTo { c =>
              ownerForm.map(_.get).map { owner =>
                caseService.setOwners(c.id, owner.usercodes.toSet, None)
              }.getOrElse(Future.successful(Right(())))
                .successMapTo(_ => c)
            }
            .successFlatMapTo { c =>
              ServiceResults.futureSequence(notes.map { n => caseService.addGeneralNote(c.id, n) })
                .successMapTo(_ => c)
            }
        }).successMap { cases =>
          Redirect(routes.DataImportController.importForm())
            .flashing("success" -> Messages("flash.cases.imported", cases.size))
        }
      }
    }.getOrElse(Future.successful(Redirect(routes.DataImportController.importForm())))
  }

}

object SpreadsheetContentsHandler {
  type Sheet = Seq[Row]
  case class Row(rowNumber: Int, values: Map[String, Cell])
  case class Cell(cellReference: String, formattedValue: String)
}

class SpreadsheetContentsHandler extends SheetContentsHandler {

  var parsedSheets: mutable.Map[String, Sheet] = mutable.Map.empty
  var parsedRows: mutable.ListBuffer[Row] = _
  var currentItem: mutable.Map[String, Cell] = _
  var columns: mutable.Map[Short, String] = _

  def startSheet(name: String): Unit = {
    parsedRows = mutable.ListBuffer.empty
    columns = mutable.Map.empty
  }

  def endSheet(name: String): Unit = {
    parsedSheets(name) = parsedRows.toList // Not strictly necessary but enforces immutability
  }

  override def headerFooter(text: String, isHeader: Boolean, tagName: String): Unit = {}

  override def startRow(rowNum: Int): Unit = {
    currentItem = mutable.Map.empty
  }
  override def endRow(rowNum: Int): Unit = {
    if (rowNum > 0 && currentItem.nonEmpty) parsedRows += Row(rowNum + 1, currentItem.toMap)
  }

  override def cell(cellReference: String, formattedValue: String, comment: XSSFComment): Unit = {
    val cell = new CellReference(cellReference)
    val col = new CellReference(cellReference).getCol

    if (cell.getRow == 0) {
      // Header
      columns(col) = formattedValue
    } else if (columns.contains(col)) { // We ignore anything outside of the header columns
      currentItem(columns(col)) = Cell(cellReference, formattedValue)
    }
  }
}
