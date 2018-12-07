package controllers.sysadmin

import com.google.common.io.ByteSource
import controllers.UploadedFileControllerHelper.TemporaryUploadedFile
import controllers.admin.CaseController.CaseFormData
import controllers.admin.{CaseController, OwnersController}
import controllers.sysadmin.DataImportController._
import controllers.sysadmin.SpreadsheetContentsHandler.{Cell, Row, Sheet}
import controllers.{BaseController, UploadedFileControllerHelper}
import domain._
import helpers.ServiceResults._
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.util.CellReference
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler
import org.apache.poi.xssf.eventusermodel.{ReadOnlySharedStringsTable, XSSFReader, XSSFSheetXMLHandler}
import org.apache.poi.xssf.usermodel.XSSFComment
import org.quartz._
import org.xml.sax.InputSource
import org.xml.sax.helpers.XMLReaderFactory
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, Json, Reads, Writes, _}
import play.api.mvc.{Action, AnyContent, MultipartFormData}
import services._
import services.tabula.ProfileService
import warwick.core.Logging
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, UserLookupService, Usercode}

import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object DataImportController {
  val TeamColumnHeading = "Team"
  val NotesColumnHeading = "Notes"
  val CaseOwnerColumnHeading = "Case owner"
  val IssueStateColumnHeading = "Case state"

  val ColumnMappings: Map[String, String] = Map(
    "Client" -> "clients[0]",
    "Subject" -> "case.subject",
    "Cause" -> "case.cause",
  )

  val ColumnTransforms: Map[String, String => String] = Map(
    "Cause" -> ((d: String) => CaseCause.values.find(_.description == d).map(_.entryName).getOrElse(d))
  )

  val AllColumnHeadings: Seq[String] = Seq(TeamColumnHeading, NotesColumnHeading, CaseOwnerColumnHeading, IssueStateColumnHeading) ++ ColumnMappings.keys
}

@Singleton
class DataImportController @Inject()(
  securityService: SecurityService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  scheduler: Scheduler,
)(implicit
  executionContext: ExecutionContext,
  profileService: ProfileService,
  enquiryService: EnquiryService,
  userLookupService: UserLookupService,
  permissionService: PermissionService,
) extends BaseController {

  import securityService._

  def importForm(): Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.importForm())
  }

  def importCasesPreview(): Action[MultipartFormData[TemporaryUploadedFile]] = RequireSysadmin(uploadedFileControllerHelper.bodyParser) { implicit request =>
    request.body.file("file").map { file =>
      val forms: Seq[((String, Row), (Team, Form[CaseFormData], IssueState, Option[Form[OwnerSave]], Seq[CaseNoteSave]))] =
        DataImportJob.parse(file.ref.in)

      val (invalid, valid) = forms.partition { case (_, (_, caseForm, _, ownerForm, _)) =>
        caseForm.hasErrors || ownerForm.exists(_.hasErrors)
      }

      if (invalid.nonEmpty) {
        // Just show errors
        Ok(views.html.sysadmin.importPreview(invalid, valid, None))
      } else if (valid.isEmpty) {
        // Redirect back, empty spreadsheet
        Redirect(routes.DataImportController.importForm())
          .flashing("warning" -> Messages("flash.import.empty"))
      } else {
        Ok(views.html.sysadmin.importPreview(
          invalid,
          valid,
          Some(DataImportJob.form.fill(
            valid.map { case ((sheetName, row), (team, caseForm, state, ownerForm, notes)) =>
              ((sheetName, row), (team, caseForm.get, state, ownerForm.map(_.get), notes))
            }
          ))
        ))
      }
    }.getOrElse(Redirect(routes.DataImportController.importForm()))
  }

  def scheduleJob(): Action[AnyContent] = RequireSysadmin { implicit request =>
    DataImportJob.form.bindFromRequest().fold(
      formWithErrors => BadRequest(formWithErrors.errors.mkString("\n")),
      data =>
        if (scheduler.checkExists(new JobKey("DataImportJob", null))) {
          Redirect(controllers.sysadmin.routes.DataImportController.importForm())
            .flashing("error" -> Messages("flash.import.alreadyRunning"))
        } else {
          scheduler.scheduleJob(
            JobBuilder.newJob(classOf[DataImportJob])
              .withIdentity("DataImportJob")
              .usingJobData("data", Json.toJson(data)(DataImportJob.jobDataFormat).toString())
              .build(),
            TriggerBuilder.newTrigger()
              .startNow()
              .build()
          )
          Redirect(controllers.sysadmin.routes.DataImportController.importForm())
            .flashing("success" -> Messages("flash.import.scheduled"))
        }
    )
  }

}

object SpreadsheetContentsHandler {
  type Sheet = Seq[Row]
  case class Row(rowNumber: Int, values: Map[String, Cell])
  case class Cell(cellReference: String, formattedValue: String)

  implicit val cellFormat: Format[Cell] = Json.format[Cell]
  implicit val rowFormat: Format[Row] = Json.format[Row]
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
      currentItem(columns(col)) = Cell(cellReference, formattedValue.replace("_x000D_", ""))
    }
  }
}

object DataImportJob {
  type ParsedSpreadsheet = Seq[((String, Row), (Team, Form[CaseFormData], IssueState, Option[Form[OwnerSave]], Seq[CaseNoteSave]))]
  type JobData = Seq[((String, Row), (Team, CaseFormData, IssueState, Option[OwnerSave], Seq[CaseNoteSave]))]

  implicit val universityIDFormat: Format[UniversityID] = Json.format[UniversityID]
  implicit val usercodeFormat: Format[Usercode] = Json.format[Usercode]
  implicit val caseIncidentFormat: Format[CaseIncident] = Json.format[CaseIncident]
  implicit val caseSaveFormat: Format[CaseSave] = Json.format[CaseSave]
  val caseFormDataFormat: Format[CaseFormData] = Json.format[CaseFormData]
  val ownerSaveFormat: Format[OwnerSave] = Json.format[OwnerSave]
  val caseNoteSaveFormat: Format[CaseNoteSave] = Json.format[CaseNoteSave]

  val readsSingleRow: Reads[((String, Row), (Team, CaseFormData, IssueState, Option[OwnerSave], Seq[CaseNoteSave]))] =
    (
      (
        (__ \ "sheetName").read[String] and
        (__ \ "row").read[Row](SpreadsheetContentsHandler.rowFormat)
      ).tupled and
      (
        (__ \ "team").read[Team](Teams.format) and
        (__ \ "caseFormData").read[CaseFormData](caseFormDataFormat) and
        (__ \ "state").read[IssueState] and
        (__ \ "ownerFormData").readNullable[OwnerSave](ownerSaveFormat) and
        (__ \ "notes").read[Seq[CaseNoteSave]](Reads.seq(caseNoteSaveFormat))
      ).tupled
    ).tupled

  val jobDataFormat: Format[JobData] = Format(
    Reads.seq(readsSingleRow),
    Writes.seq { case ((sheetName: String, row: Row), (team: Team, caseFormData: CaseFormData, state: IssueState, ownerFormData: Option[OwnerSave], notes: Seq[CaseNoteSave])) =>
      Json.obj(
        "sheetName" -> Json.toJson(sheetName),
        "row" -> Json.toJson(row)(SpreadsheetContentsHandler.rowFormat),
        "team" -> Json.toJson(team)(Teams.format),
        "caseFormData" -> Json.toJson(caseFormData)(caseFormDataFormat),
        "state" -> Json.toJson(state),
        "ownerFormData" -> ownerFormData.map(Json.toJson(_)(ownerSaveFormat)),
        "notes" -> Json.toJson(notes)(Writes.seq(caseNoteSaveFormat)),
      )
    }
  )

  val form = Form(single(
    "data" -> text.transform[JobData](Json.parse(_).as[JobData](jobDataFormat), Json.toJson(_)(jobDataFormat).toString())
  ))

  def parse(
    in: ByteSource
  )(implicit
    profileService: ProfileService,
    enquiryService: EnquiryService,
    userLookupService: UserLookupService,
    permissionService: PermissionService,
    timingContext: TimingContext,
    executionContext: ExecutionContext,
  ): ParsedSpreadsheet = {
    val pkg = OPCPackage.open(in.openStream())
    val handler = new SpreadsheetContentsHandler

    try {
      val sst = new ReadOnlySharedStringsTable(pkg)
      val reader = new XSSFReader(pkg)
      val styles = reader.getStylesTable

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
    } finally {
      pkg.close()
    }

    handler.parsedSheets.toSeq.par.flatMap { case (sheetName, rows) =>
      rows.par.flatMap { row =>
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
            ).bind(
              row.values.filterKeys(ColumnMappings.contains)
                .map { case (k, cell) =>
                  val value = cell.formattedValue
                  ColumnMappings(k) -> ColumnTransforms.get(k).map(_(value)).getOrElse(value)
                }
            )

            val state =
              row.values.get(IssueStateColumnHeading)
                .flatMap { cell => IssueState.namesToValuesMap.get(cell.formattedValue) }
                .getOrElse(IssueState.Open)

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
                  OwnersController.ownersForm(userLookupService, permissionService, Set.empty, allowEmpty = false)
                    .bind(Map("owners[0]" -> user.usercode.string))
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

            ((sheetName, row), (team, form, state, ownerForm, Seq(generalNote, migrationNote).flatten))
          }
      }.seq
    }.seq
  }
}

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class DataImportJob @Inject()(
  caseService: CaseService,
)(implicit executionContext: ExecutionContext) extends Job with Logging {

  private[this] implicit class FutureServiceResultOps[A](f: Future[ServiceResult[A]]) {
    // Convenient way to block on a Future[ServiceResult[_]] that you expect
    // to be successful.
    def serviceValue: A =
      Await.result(f, Duration.Inf).fold(
        e => {
          val msg = e.head.message
          e.headOption.flatMap(_.cause).fold(logger.error(msg))(t => logger.error(msg, t))

          e.flatMap(_.cause).headOption
            .map(throw _)
            .getOrElse(throw new IllegalStateException(e.map(_.message).mkString("; ")))
        },
        identity // return success as-is
      )
  }

  private def auditLogContext(usercode: Usercode) = AuditLogContext(
    usercode = Some(usercode),
    ipAddress = None,
    userAgent = None,
    timingData = new TimingContext.Data
  )

  override def execute(context: JobExecutionContext): Unit = {
    // Run the import
    Try {
      val jobData = Json.parse(context.getJobDetail.getJobDataMap.getString("data")).as[DataImportJob.JobData](DataImportJob.jobDataFormat)

      implicit val ac: AuditLogContext = auditLogContext(Usercode("system"))

      jobData.foreach { case (_, (team, caseData, state, ownerData, notes)) =>
        val c = caseService.create(caseData.`case`, caseData.clients, Set.empty, team, None, None).serviceValue

        state match {
          case IssueState.Closed =>
            caseService.updateState(c.id, IssueState.Closed, c.lastUpdated, CaseNoteSave("Case closed on migration", Usercode("system"), None)).serviceValue

          case _ => // do nothing
        }

        ownerData.foreach { owner =>
          caseService.setOwners(c.id, owner.usercodes.toSet, None).serviceValue
        }

        futureSequence(notes.map { n => caseService.addGeneralNote(c.id, n) }).serviceValue
      }

      logger.info(s"Imported ${jobData.size} cases")
    } match {
      case Success(_) =>
      case Failure(t) =>
        logger.error("Something went wrong!", t)
        throw new JobExecutionException(t)
    }
  }

}