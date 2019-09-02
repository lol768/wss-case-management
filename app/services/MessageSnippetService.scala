package services

import java.time.OffsetDateTime
import java.util.UUID

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.AuditEvent.{Operation, Target}
import domain.ExtendedPostgresProfile.api._
import domain.dao.MessageDao.StoredMessageSnippet
import domain.dao.UploadedFileDao.StoredUploadedFile
import domain.dao.{DaoRunner, MessageDao}
import domain.{MessageSnippet, MessageSnippetSave, OneToMany, UploadedFileOwner}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileSave

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MessageSnippetServiceImpl])
trait MessageSnippetService {
  def create(save: MessageSnippetSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[MessageSnippet]]
  def update(id: UUID, save: MessageSnippetSave, files: Seq[(ByteSource, UploadedFileSave)], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[MessageSnippet]]
  def delete(id: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
  def list()(implicit t: TimingContext): Future[ServiceResult[Seq[MessageSnippet]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[MessageSnippet]]
  def reorder(requestedOrder: Seq[MessageSnippet], latestVersion: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[MessageSnippet]]]
  def deleteFile(snippetID: UUID, fileID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class MessageSnippetServiceImpl @Inject()(
  auditService: AuditService,
  uploadedFileService: UploadedFileService,
  daoRunner: DaoRunner,
  dao: MessageDao,
)(implicit ec: ExecutionContext) extends MessageSnippetService with Logging {

  override def create(save: MessageSnippetSave, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[MessageSnippet]] = {
    val id = UUID.randomUUID()
    auditService.audit(
      Operation.MessageSnippet.Save,
      id.toString,
      Target.MessageSnippet,
      Json.toJson(save)
    ) {
      daoRunner.run(for {
        inserted <- dao.insert(StoredMessageSnippet(
          id = id,
          title = save.title,
          body = save.body,
          sortOrder = 0,
          version = JavaTime.offsetDateTime,
        ))
        f <- DBIO.sequence(files.map { case (in, metadata) =>
          uploadedFileService.storeDBIO(in, metadata, ac.usercode.get, inserted.id, UploadedFileOwner.MessageSnippet)
        })
      } yield inserted.asSnippet(f)).map(ServiceResults.success)
    }
  }

  override def update(id: UUID, save: MessageSnippetSave, files: Seq[(ByteSource, UploadedFileSave)], version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[MessageSnippet]] =
    auditService.audit(
      Operation.MessageSnippet.Update,
      id.toString,
      Target.MessageSnippet,
      Json.toJson(save)
    ) {
      daoRunner.run(for {
        existing <- dao.findSnippetsQuery.filter(_.id === id).result.head
        updated <- dao.update(StoredMessageSnippet(
          id = id,
          title = save.title,
          body = save.body,
          sortOrder = existing.sortOrder,
          version = JavaTime.offsetDateTime
        ), version)
        existingFiles <- dao.findSnippetsQuery.filter(_.id === id).withUploadedFiles.map(_._2).result
        f <- DBIO.sequence(files.map { case (in, metadata) =>
          uploadedFileService.storeDBIO(in, metadata, ac.usercode.get, updated.id, UploadedFileOwner.MessageSnippet)
        })
      } yield updated.asSnippet(existingFiles.flatten.map(_.asUploadedFile) ++ f)).map(ServiceResults.success)
    }

  override def delete(id: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(
      Operation.MessageSnippet.Delete,
      id.toString,
      Target.MessageSnippet,
      Json.obj()
    ) {
      daoRunner.run(for {
        existing <- dao.findSnippetsQuery.filter(_.id === id).result.head
        done <- dao.delete(existing, version)
      } yield done).map(ServiceResults.success)
    }

  override def list()(implicit t: TimingContext): Future[ServiceResult[Seq[MessageSnippet]]] =
    daoRunner.run(
      dao.findSnippetsQuery
        .withUploadedFiles
        .result
    ).map { sf =>
      ServiceResults.success {
        OneToMany.leftJoin(sf.distinct)(StoredUploadedFile.dateOrdering)
          .sortBy(_._1)
          .map { case (s, f) => s.asSnippet(f.map(_.asUploadedFile)) }
      }
    }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[MessageSnippet]] =
    daoRunner.run(
      dao.findSnippetsQuery
        .withUploadedFiles
        .filter { case (s, _) => s.id === id }
        .result
    ).map {
      case Nil => ServiceResults.error(s"Could not find a MessageSnippet with ID $id")
      case sf =>
        ServiceResults.success {
          OneToMany.leftJoin(sf.distinct)(StoredUploadedFile.dateOrdering)
            .map { case (s, f) => s.asSnippet(f.map(_.asUploadedFile)) }
            .head
        }
    }

  override def reorder(requestedOrder: Seq[MessageSnippet], latestVersion: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[MessageSnippet]]] =
    auditService.audit(
      Operation.MessageSnippet.Reorder,
      requestedOrder.head.id.toString, // We require audit events to have a single target, but actually none makes sense here
      Target.MessageSnippet,
      Json.obj("order" -> requestedOrder.map(_.id.toString))
    ) {
      def reorderOps(existing: Seq[StoredMessageSnippet]): DBIO[Done] = {
        var order: Int = 0
        var previous: Option[StoredMessageSnippet] = None

        val ops =
          requestedOrder.map(s => existing.find(_.id == s.id).get)
            .flatMap { snippet =>
              if (previous.exists { s => StoredMessageSnippet.defaultOrdering.compare(s, snippet.copy(sortOrder = order)) > 0 }) {
                order = order + 10
              }

              val updatedSnippet = snippet.copy(sortOrder = order)
              previous = Some(updatedSnippet)

              if (updatedSnippet == snippet) None
              else Some(dao.update(updatedSnippet, updatedSnippet.version))
            }

        if (ops.isEmpty) DBIO.successful(Done)
        else DBIO.sequence(ops).map(_ => Done)
      }

      daoRunner.run(for {
        existing <- dao.findSnippetsQuery.sortBy { s => (s.sortOrder, s.title) }.result
        if (
          // There isn't a newer version
          !existing.exists(_.version.isAfter(latestVersion))

          // The ID set matches
          && existing.map(_.id).toSet == requestedOrder.map(_.id).toSet
        )
        _ <- reorderOps(existing)
        updated <- dao.findSnippetsQuery.withUploadedFiles.result
      } yield updated).map { sf =>
        ServiceResults.success {
          OneToMany.leftJoin(sf.distinct)(StoredUploadedFile.dateOrdering)
            .sortBy(_._1)
            .map { case (s, f) => s.asSnippet(f.map(_.asUploadedFile)) }
        }
      }
    }

  override def deleteFile(snippetID: UUID, fileID: UUID, version: OffsetDateTime)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    daoRunner.run(for {
      _ <- dao.findSnippetsQuery.filter(_.id === snippetID).withUploadedFiles.map(_._2).filter(_.nonEmpty).filter(_.map(_.id) === fileID).result.head
      done <- uploadedFileService.deleteDBIO(fileID)
    } yield done).map(ServiceResults.success)

}
