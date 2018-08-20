package services.healthcheck

import java.nio.charset.StandardCharsets

import com.google.common.io.CharSource
import helpers.JavaTime
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsObject, Json}
import services.healthcheck.EncryptedObjectStorageHealthCheck._
import warwick.objectstore.{EncryptedObjectStorageService, ObjectStorageService}

import scala.io.Source
import scala.util.Try

object EncryptedObjectStorageHealthCheck {
  val objectKey = "healthcheck-test"
  val contents = "Here is a short sentence that we'll store in the encrypted object storage."
}

@Singleton
class EncryptedObjectStorageHealthCheck @Inject()(
  objectStorageService: ObjectStorageService
) extends HealthCheck {

  override val name = "encrypted-storage"
  override def toJson: JsObject = {
    Try {
      val startTime = System.currentTimeMillis()

      objectStorageService.fetchBlob(objectKey)
        .orElse {
          val byteSource = CharSource.wrap(contents).asByteSource(StandardCharsets.UTF_8)
          objectStorageService.put(objectKey, byteSource, ObjectStorageService.Metadata(
            contentLength = byteSource.size(),
            contentType = "text/plain",
            fileHash = None
          ))

          objectStorageService.fetchBlob(objectKey)
        }
        .map { blob =>
          // Do we have an IV stored in the user metadata?
          if (!blob.getMetadata.getUserMetadata.containsKey(EncryptedObjectStorageService.metadataIVKey)) {
            Json.obj(
              "name" -> name,
              "status" -> HealthCheckStatus.Critical.string,
              "message" -> s"Couldn't find ${EncryptedObjectStorageService.metadataIVKey} in the metadata for object $objectKey - object not encrypted?",
              "testedAt" -> JavaTime.offsetDateTime
            )
          } else {
            // Check the decrypted contents match the original
            val actualContents = Source.fromInputStream(blob.getPayload.openStream()).mkString
            if (actualContents != contents) {
              Json.obj(
                "name" -> name,
                "status" -> HealthCheckStatus.Critical.string,
                "message" -> s"Encrypted contents $actualContents didn't match expected $contents",
                "testedAt" -> JavaTime.offsetDateTime
              )
            } else {
              val endTime = System.currentTimeMillis()
              val timeTakenMs = endTime - startTime

              Json.obj(
                "name" -> name,
                "status" -> HealthCheckStatus.Okay.string,
                "message" -> s"Fetched and decrypted $objectKey in ${timeTakenMs}ms",
                "perfData" -> Seq(PerfData("time_taken_ms", timeTakenMs)).map(_.formatted),
                "testedAt" -> JavaTime.offsetDateTime
              )
            }
          }
        }
        .getOrElse {
          Json.obj(
            "name" -> name,
            "status" -> HealthCheckStatus.Critical.string,
            "message" -> s"Couldn't find object with key $objectKey in the object store",
            "testedAt" -> JavaTime.offsetDateTime
          )
        }
    }.recover { case t =>
      Json.obj(
        "name" -> name,
        "status" -> HealthCheckStatus.Unknown.string,
        "message" -> s"Error performing health check: ${t.getMessage}",
        "testedAt" -> JavaTime.offsetDateTime
      )
    }.get
  }

}
