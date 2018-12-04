package services.healthcheck

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import com.google.common.io.CharSource
import javax.inject.{Inject, Named, Singleton}
import services.healthcheck.EncryptedObjectStorageHealthCheck._
import uk.ac.warwick.util.service.ServiceHealthcheck.Status
import uk.ac.warwick.util.service.ServiceHealthcheck.Status._
import uk.ac.warwick.util.service.{ServiceHealthcheck, ServiceHealthcheckProvider}
import warwick.core.Logging
import warwick.core.helpers.JavaTime.{localDateTime => now}
import warwick.objectstore.{EncryptedObjectStorageService, ObjectStorageService}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try

object EncryptedObjectStorageHealthCheck {
  val objectKey = "healthcheck-test"
  val contents = "Here is a short sentence that we'll store in the encrypted object storage."
}

@Singleton
class EncryptedObjectStorageHealthCheck @Inject()(
  objectStorageService: ObjectStorageService,
  system: ActorSystem,
  @Named("objectStorage") ec: ExecutionContext
) extends ServiceHealthcheckProvider(new ServiceHealthcheck("encrypted-storage", Status.Unknown, now)) with Logging {

  private val name: String = "encrypted-storage"

  override def run(): Unit = update({
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
            new ServiceHealthcheck(
              name,
              Error,
              now,
              s"Couldn't find ${EncryptedObjectStorageService.metadataIVKey} in the metadata for object $objectKey - object not encrypted?"
            )
          } else {
            // Check the decrypted contents match the original
            val actualContents = Source.fromInputStream(blob.getPayload.openStream()).mkString
            if (actualContents != contents) {
              new ServiceHealthcheck(
                name,
                Error,
                now,
                s"Encrypted contents $actualContents didn't match expected $contents"
              )
            } else {
              val endTime = System.currentTimeMillis()
              val timeTakenMs = endTime - startTime

              new ServiceHealthcheck(
                name,
                Okay,
                now,
                s"Fetched and decrypted $objectKey in ${timeTakenMs}ms",
                Seq[ServiceHealthcheck.PerformanceData[_]](new ServiceHealthcheck.PerformanceData("time_taken_ms", timeTakenMs)).asJava
              )
            }
          }
        }
        .getOrElse {
          new ServiceHealthcheck(
            name,
            Error,
            now,
            s"Couldn't find object with key $objectKey in the object store"
          )
        }
    }.recover { case t =>
      new ServiceHealthcheck(
        name,
        Unknown,
        now,
        s"Error performing health check: ${t.getMessage}"
      )
    }.get
  })

  system.scheduler.schedule(0.seconds, interval = 1.minute) {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  }(ec)

}
