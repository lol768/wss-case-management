package controllers

import helpers.FakeRequestMethods._
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.ConfigMemorySize
import domain.UploadedFileSave
import helpers.{MockVirusScanService, OneAppPerSuite}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.play.PlaySpec
import play.api.Configuration
import play.api.http.Status
import play.api.test.{FakeHeaders, FakeRequest}
import warwick.sso.{Usercode, Users}

class UploadedFileControllerHelperTest extends PlaySpec with OneAppPerSuite with MockitoSugar with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(scaled(Span(2000, Millis)), scaled(Span(15, Millis)))

  val uploadedFileControllerHelper: UploadedFileControllerHelper = get[UploadedFileControllerHelper]
  implicit val materializer: Materializer = get[Materializer]

  private abstract class MultipartBodyFixture(val content: Array[Byte], val fileName: String, val contentType: String = "application/octet-stream") {
    def this(fileSize: Long, fileName: String, contentType: String) {
      this(Array.ofDim[Byte](fileSize.toInt), fileName, contentType)
    }

    def this(fileSize: Long, fileName: String) {
      this(fileSize, fileName, "application/octet-stream")
    }

    val boundary = "-----------------------------14568445977970839651285587160"
    val header =
      s"--$boundary\r\n" +
        s"""Content-Disposition: form-data; name="uploadedfile"; filename="$fileName"""" + "\r\n" +
        s"Content-Type: $contentType\r\n" +
        "\r\n"
    val footer =
      "\r\n" +
      s"--$boundary--\r\n"

    val body = Source(
      ByteString(header) ::
      ByteString(content) ::
      ByteString(footer) ::
      Nil
    )

    val bodySize = header.length + content.length + footer.length

    val request = FakeRequest(
      method = "POST",
      uri = "/x",
      headers = FakeHeaders(Seq(
        "Content-Type" -> s"multipart/form-data; boundary=$boundary",
        "Content-Length" -> bodySize.toString)),
      body = body).withUser(Users.create(Usercode("cuscav")))
  }

  "UploadedFileControllerHelper.bodyParser" should {
    "parse files into temporary files" in new MultipartBodyFixture(100, "uploadedfile.txt") {
      val response = uploadedFileControllerHelper.bodyParser.apply(request).run(body)
      response.futureValue.isRight mustBe true

      val parsedBody = response.futureValue.right.get
      parsedBody.files.size mustBe 1

      val file = parsedBody.files.head.ref
      file.key mustBe "uploadedfile"
      file.in.size() mustBe content.length
      file.metadata mustBe UploadedFileSave("uploadedfile.txt", content.length.toLong, "text/plain")
    }

    "fail if a file is too large" in new MultipartBodyFixture(get[Configuration].get[ConfigMemorySize]("wellbeing.files.maxIndividualFileSize").toBytes + 1, "uploadedfile.txt") {
      val response = uploadedFileControllerHelper.bodyParser.apply(request).run(body)
      response.futureValue.isLeft mustBe true

      val result = response.futureValue.left.get
      result.header.status mustBe Status.REQUEST_ENTITY_TOO_LARGE
    }

    "fail if a file is a dangerous content type" in new MultipartBodyFixture(100, "evil.html") {
      val response = uploadedFileControllerHelper.bodyParser.apply(request).run(body)
      response.futureValue.isLeft mustBe true

      val result = response.futureValue.left.get
      result.header.status mustBe Status.UNSUPPORTED_MEDIA_TYPE
    }

    "fail if a file is a virus" in new MultipartBodyFixture(MockVirusScanService.virusContent.read(), "uploadedfile.txt") {
      val response = uploadedFileControllerHelper.bodyParser.apply(request).run(body)
      response.futureValue.isLeft mustBe true

      val result = response.futureValue.left.get
      result.header.status mustBe Status.UNPROCESSABLE_ENTITY
    }
  }

}
