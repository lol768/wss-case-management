package services

import java.util.UUID

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.dao.UploadedFileDao.StoredUploadedFile
import domain.dao.{DaoRunner, UploadedFileDao}
import domain.{UploadedFile, UploadedFileSave}
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import domain.ExtendedPostgresProfile.api._
import warwick.objectstore.ObjectStorageService

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UploadedFileServiceImpl])
trait UploadedFileService {
  def storeDBIO(in: ByteSource, metadata: UploadedFileSave): DBIO[UploadedFile]
  def store(in: ByteSource, metadata: UploadedFileSave)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]]

  def deleteDBIO(id: UUID): DBIO[Done]
  def delete(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class UploadedFileServiceImpl @Inject()(
  auditService: AuditService,
  objectStorageService: ObjectStorageService,
  daoRunner: DaoRunner,
  dao: UploadedFileDao
)(implicit ec: ExecutionContext) extends UploadedFileService {

  private def storeDBIO(id: UUID, in: ByteSource, metadata: UploadedFileSave): DBIO[UploadedFile] = {
    for {
      // Treat the ObjectStorageService put as DBIO so we force a rollback if it fails (even though it won't delete the object)
      _ <- DBIO.from(Future {
        objectStorageService.put(id.toString, in, ObjectStorageService.Metadata(
          contentLength = metadata.contentLength,
          contentType = metadata.contentType,
          fileHash = None, // This is calculated and stored by EncryptedObjectStorageService so no need to do it here
        ))
      })
      file <- dao.insert(StoredUploadedFile(
        id,
        metadata.fileName,
        metadata.contentLength,
        metadata.contentType,
        metadata.uploadedBy,
        JavaTime.offsetDateTime,
        JavaTime.offsetDateTime
      ))
    } yield file.asUploadedFile
  }

  override def storeDBIO(in: ByteSource, metadata: UploadedFileSave): DBIO[UploadedFile] =
    storeDBIO(UUID.randomUUID(), in, metadata)

  override def store(in: ByteSource, metadata: UploadedFileSave)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]] = {
    val id = UUID.randomUUID()
    auditService.audit('UploadedFileStore, id.toString, 'UploadedFile, Json.obj()) {
      daoRunner.run(storeDBIO(id, in, metadata)).map(Right.apply)
    }
  }

  override def deleteDBIO(id: UUID): DBIO[Done] =
    for {
      existing <- dao.find(id)
      done <- dao.delete(existing)
    } yield done

  override def delete(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit('UploadedFileDelete, id.toString, 'UploadedFile, Json.obj()) {
      daoRunner.run(deleteDBIO(id)).map(Right.apply)
    }

}
