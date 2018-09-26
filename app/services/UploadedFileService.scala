package services

import java.util.UUID

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.dao.UploadedFileDao.StoredUploadedFile
import domain.dao.{DaoRunner, UploadedFileDao}
import domain.{UploadedFile, UploadedFileOwner, UploadedFileSave}
import helpers.JavaTime
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import domain.ExtendedPostgresProfile.api._
import system.TimingCategories
import warwick.core.timing.{TimingContext, TimingService}
import warwick.objectstore.ObjectStorageService
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UploadedFileServiceImpl])
trait UploadedFileService {
  def storeDBIO(in: ByteSource, metadata: UploadedFileSave, uploader: Usercode)(implicit t: TimingContext): DBIO[UploadedFile]
  def storeDBIO(in: ByteSource, metadata: UploadedFileSave, uploader: Usercode, ownerId: UUID, ownerType: UploadedFileOwner)(implicit t: TimingContext): DBIO[UploadedFile]
  def store(in: ByteSource, metadata: UploadedFileSave)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]]
  def store(in: ByteSource, metadata: UploadedFileSave, ownerId: UUID, ownerType: UploadedFileOwner)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]]

  def deleteDBIO(id: UUID): DBIO[Done]
  def delete(id: UUID)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class UploadedFileServiceImpl @Inject()(
  auditService: AuditService,
  objectStorageService: ObjectStorageService,
  daoRunner: DaoRunner,
  dao: UploadedFileDao,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends UploadedFileService {

  import timing._

  private def storeDBIO(id: UUID, in: ByteSource, metadata: UploadedFileSave, uploader: Usercode, ownerId: Option[UUID], ownerType: Option[UploadedFileOwner])(implicit t: TimingContext): DBIO[UploadedFile] = {
    for {
      // Treat the ObjectStorageService put as DBIO so we force a rollback if it fails (even though it won't delete the object)
      _ <- DBIO.from(time(TimingCategories.ObjectStorageWrite) {
        Future {
          objectStorageService.put(id.toString, in, ObjectStorageService.Metadata(
            contentLength = metadata.contentLength,
            contentType = metadata.contentType,
            fileHash = None, // This is calculated and stored by EncryptedObjectStorageService so no need to do it here
          ))
        }
      })
      file <- dao.insert(StoredUploadedFile(
        id,
        metadata.fileName,
        metadata.contentLength,
        metadata.contentType,
        uploader,
        ownerId,
        ownerType,
        JavaTime.offsetDateTime,
        JavaTime.offsetDateTime
      ))
    } yield file.asUploadedFile
  }

  override def storeDBIO(in: ByteSource, metadata: UploadedFileSave, uploader: Usercode)(implicit t: TimingContext): DBIO[UploadedFile] =
    storeDBIO(UUID.randomUUID(), in, metadata, uploader, None, None)

  override def storeDBIO(in: ByteSource, metadata: UploadedFileSave, uploader: Usercode, ownerId: UUID, ownerType: UploadedFileOwner)(implicit t: TimingContext): DBIO[UploadedFile] =
    storeDBIO(UUID.randomUUID(), in, metadata, uploader, Some(ownerId), Some(ownerType))

  override def store(in: ByteSource, metadata: UploadedFileSave)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]] =
    auditService.audit[UploadedFile]('UploadedFileStore, (f: UploadedFile) => f.id.toString, 'UploadedFile, Json.obj()) {
      daoRunner.run(storeDBIO(in, metadata, ac.usercode.get)).map(Right.apply)
    }

  override def store(in: ByteSource, metadata: UploadedFileSave, ownerId: UUID, ownerType: UploadedFileOwner)(implicit ac: AuditLogContext): Future[ServiceResult[UploadedFile]] =
    auditService.audit[UploadedFile]('UploadedFileStore, (f: UploadedFile) => f.id.toString, 'UploadedFile, Json.obj()) {
      daoRunner.run(storeDBIO(in, metadata, ac.usercode.get, ownerId, ownerType)).map(Right.apply)
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
