package services

import java.io.{IOException, InputStream}
import java.nio.charset.StandardCharsets

import com.google.common.io.ByteSource
import domain.UploadedFileSave
import domain.dao.{AbstractDaoTest, DaoRunner, UploadedFileDao}
import helpers.DataFixture
import org.jclouds.http.HttpResponseException
import org.mockito.Matchers._
import org.mockito.Mockito._
import slick.jdbc.PostgresProfile.api._
import warwick.objectstore.ObjectStorageService
import warwick.sso.Usercode

import scala.util.Try

class UploadedFileServiceTest extends AbstractDaoTest {

  private class TestFixture extends DataFixture[(UploadedFileService, ObjectStorageService)] {
    override def setup(): (UploadedFileService, ObjectStorageService) =
      (get[UploadedFileService], get[ObjectStorageService])

    override def teardown(): Unit =
      execWithCommit(
        UploadedFileDao.uploadedFiles.table.delete andThen
        UploadedFileDao.uploadedFiles.versionsTable.delete
      )
  }

  private class MockObjectStorageServiceFixture extends DataFixture[(UploadedFileService, ObjectStorageService)] {
    override def setup(): (UploadedFileService, ObjectStorageService) = {
      val objectStorageService = mock[ObjectStorageService](RETURNS_SMART_NULLS)
      val uploadedFileService = new UploadedFileServiceImpl(
        get[AuditService],
        objectStorageService,
        get[DaoRunner],
        get[UploadedFileDao]
      )

      (uploadedFileService, objectStorageService)
    }

    override def teardown(): Unit =
      execWithCommit(
        UploadedFileDao.uploadedFiles.table.delete andThen
        UploadedFileDao.uploadedFiles.versionsTable.delete
      )
  }

  "UploadedFileService" should {
    "store uploaded files" in withData(new TestFixture) { case (service, objectStorageService) =>
      val saved = service.store(
        ByteSource.wrap("I love lamp".getBytes(StandardCharsets.UTF_8)),
        UploadedFileSave("problem.txt", 11, "text/plain", Usercode("cuscav"))
      ).serviceValue
      saved.fileName mustBe "problem.txt"
      saved.contentLength mustBe 11
      saved.contentType mustBe "text/plain"

      objectStorageService.keyExists(saved.id.toString) mustBe true
      val byteSource = new ByteSource {
        override def openStream(): InputStream = objectStorageService.fetch(saved.id.toString).orNull
      }
      byteSource.isEmpty mustBe false
      byteSource.size() mustBe 11
      byteSource.asCharSource(StandardCharsets.UTF_8).read() mustBe "I love lamp"

      exec(UploadedFileDao.uploadedFiles.table.length.result) mustBe 1
      exec(UploadedFileDao.uploadedFiles.versionsTable.length.result) mustBe 1
    }

    "rollback if blobstore fails" in withData(new MockObjectStorageServiceFixture) { case (service, objectStorageService) =>
      when(objectStorageService.put(any(), any(), any())).thenThrow(new HttpResponseException("error", null, null))

      Try(service.store(
        ByteSource.wrap("I love lamp".getBytes(StandardCharsets.UTF_8)),
        UploadedFileSave("problem.txt", 11, "text/plain", Usercode("cuscav"))
      ).serviceValue).isFailure mustBe true

      exec(UploadedFileDao.uploadedFiles.table.length.result) mustBe 0
      exec(UploadedFileDao.uploadedFiles.versionsTable.length.result) mustBe 0
    }
  }

}
