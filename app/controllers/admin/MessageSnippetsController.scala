package controllers.admin

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import controllers.BaseController
import controllers.admin.MessageSnippetsController._
import controllers.refiners.AdminActionRefiner
import domain.{MessageSnippet, MessageSnippetSave}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, MultipartFormData}
import services.MessageSnippetService
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.fileuploads.UploadedFileControllerHelper
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile

import scala.concurrent.{ExecutionContext, Future}

object MessageSnippetsController {
  case class MessageSnippetData(
    snippet: MessageSnippetSave,
    version: Option[OffsetDateTime]
  )

  private[this] def createOrUpdateSnippetForm(existingVersion: Option[OffsetDateTime]): Form[MessageSnippetData] =
    Form(mapping(
      "snippet" -> mapping(
        "title" -> nonEmptyText,
        "body" -> nonEmptyText,
      )(MessageSnippetSave.apply)(MessageSnippetSave.unapply),
      "version" -> optional(JavaTime.offsetDateTimeFormField).verifying("error.optimisticLocking", _ == existingVersion)
    )(MessageSnippetData.apply)(MessageSnippetData.unapply))

  val createSnippetForm: Form[MessageSnippetData] = createOrUpdateSnippetForm(None)
  def updateSnippetForm(existing: MessageSnippet): Form[MessageSnippetData] = createOrUpdateSnippetForm(Some(existing.lastUpdated))

  def deleteSnippetForm(existing: MessageSnippet): Form[OffsetDateTime] =
    Form(single(
      "version" -> JavaTime.offsetDateTimeFormField.verifying("error.optimisticLocking", _ == existing.lastUpdated)
    ))
}

@Singleton
class MessageSnippetsController @Inject()(
  messageSnippets: MessageSnippetService,
  adminActionRefiner: AdminActionRefiner,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import adminActionRefiner._

  def list(): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    messageSnippets.list().successMap { snippets =>
      Ok(views.html.admin.messageSnippets.list(
        snippets
      ))
    }
  }

  def createForm(): Action[AnyContent] = AdminRequiredAction { implicit request =>
    Ok(views.html.admin.messageSnippets.create(
      createSnippetForm,
      uploadedFileControllerHelper.supportedMimeTypes
    ))
  }

  def create(): Action[MultipartFormData[TemporaryUploadedFile]] = AdminRequiredAction(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    createSnippetForm.bindFromRequest().fold(
      formWithErrors => Future.successful(Ok(views.html.admin.messageSnippets.create(
        formWithErrors,
        uploadedFileControllerHelper.supportedMimeTypes
      ))),
      data => {
        val save = data.snippet
        val files = request.body.files.map(_.ref)

        messageSnippets.create(save, files.map(f => (f.in, f.metadata))).successMap { _ =>
          Redirect(controllers.admin.routes.MessageSnippetsController.list())
            .flashing("success" -> Messages("flash.messageSnippet.created"))
        }
      }
    )
  }

  def editForm(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    messageSnippets.get(id).successMap { snippet =>
      Ok(views.html.admin.messageSnippets.edit(
        snippet,
        updateSnippetForm(snippet)
          .fill(MessageSnippetData(
            MessageSnippetSave(snippet.title, snippet.body),
            Some(snippet.lastUpdated)
          )),
        uploadedFileControllerHelper.supportedMimeTypes
      ))
    }
  }

  def edit(id: UUID): Action[MultipartFormData[TemporaryUploadedFile]] = AdminRequiredAction(uploadedFileControllerHelper.bodyParser).async { implicit request =>
    messageSnippets.get(id).successFlatMap { snippet =>
      updateSnippetForm(snippet).bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.messageSnippets.edit(
          snippet,
          formWithErrors,
          uploadedFileControllerHelper.supportedMimeTypes
        ))),
        data => {
          val save = data.snippet
          val files = request.body.files.map(_.ref)

          messageSnippets.update(id, save, files.map(f => (f.in, f.metadata)), data.version.get).successMap { _ =>
            Redirect(controllers.admin.routes.MessageSnippetsController.list())
              .flashing("success" -> Messages("flash.messageSnippet.updated"))
          }
        }
      )
    }
  }

  def deleteForm(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    messageSnippets.get(id).successMap { snippet =>
      Ok(views.html.admin.messageSnippets.delete(
        snippet,
        deleteSnippetForm(snippet).fill(snippet.lastUpdated)
      ))
    }
  }

  def delete(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    messageSnippets.get(id).successFlatMap { snippet =>
      deleteSnippetForm(snippet).bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.messageSnippets.delete(
          snippet,
          formWithErrors
        ))),
        version => messageSnippets.delete(id, version).successMap { _ =>
          Redirect(controllers.admin.routes.MessageSnippetsController.list())
            .flashing("success" -> Messages("flash.messageSnippet.deleted"))
        }
      )
    }
  }

  def downloadFile(snippetID: UUID, fileID: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    messageSnippets.get(snippetID).successFlatMap { snippet =>
      snippet.files.find(_.id == fileID).map { f =>
        uploadedFileControllerHelper.serveFile(f)
      }.getOrElse(Future.successful(NotFound(views.html.errors.notFound())))
    }
  }

  def deleteFile(snippetID: UUID, fileID: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    messageSnippets.get(snippetID).successFlatMap { snippet =>
      deleteSnippetForm(snippet).bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.messageSnippets.edit(
          snippet,
          updateSnippetForm(snippet)
            .fill(MessageSnippetData(
              MessageSnippetSave(snippet.title, snippet.body),
              Some(snippet.lastUpdated)
            ))
            .copy(errors = formWithErrors.errors),
          uploadedFileControllerHelper.supportedMimeTypes
        ))),
        version => {
          val deleteFileOp =
            snippet.files.find(_.id == fileID).map { f =>
              messageSnippets.deleteFile(snippetID, fileID, version)
            }.getOrElse(Future.successful(ServiceResults.success(Done)))

          deleteFileOp.successMap { _ =>
            Redirect(controllers.admin.routes.MessageSnippetsController.editForm(snippetID))
              .flashing("success" -> Messages("flash.messageSnippet.attachment.deleted"))
          }
        }
      )
    }
  }

  def reorderUp(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    messageSnippets.list().successFlatMap { existing =>
      val toReorderUp = existing.find(_.id == id).get
      val existingBefore = existing.takeWhile(_.id != id)
      val toReorderDown = existingBefore.last
      val existingAfter = existing.reverse.takeWhile(_.id != id).reverse
      val reordered = existingBefore.reverse.tail.reverse ++ Seq(toReorderUp, toReorderDown) ++ existingAfter

      messageSnippets.reorder(reordered, reordered.map(_.lastUpdated).max).successMap { _ =>
        Redirect(controllers.admin.routes.MessageSnippetsController.list())
          .flashing("success" -> Messages("flash.messageSnippet.reordered"))
      }
    }
  }

  def reorderDown(id: UUID): Action[AnyContent] = AdminRequiredAction.async { implicit request =>
    messageSnippets.list().successFlatMap { existing =>
      val toReorderDown = existing.find(_.id == id).get
      val existingBefore = existing.takeWhile(_.id != id)
      val existingAfter = existing.reverse.takeWhile(_.id != id).reverse
      val toReorderUp = existingAfter.head
      val reordered = existingBefore ++ Seq(toReorderUp, toReorderDown) ++ existingAfter.tail

      messageSnippets.reorder(reordered, reordered.map(_.lastUpdated).max).successMap { _ =>
        Redirect(controllers.admin.routes.MessageSnippetsController.list())
          .flashing("success" -> Messages("flash.messageSnippet.reordered"))
      }
    }
  }

}
