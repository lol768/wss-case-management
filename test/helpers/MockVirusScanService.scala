package helpers

import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.concurrent.CompletableFuture

import com.google.common.io.{ByteSource, CharSource}
import helpers.MockVirusScanService._
import uk.ac.warwick.util.virusscan.{VirusScanResult, VirusScanService, VirusScanServiceStatus}

import scala.concurrent.Future
import scala.compat.java8.FutureConverters._

object MockVirusScanService {
  def virusContent: ByteSource = CharSource.wrap("VIRUS").asByteSource(StandardCharsets.UTF_8)
  def errorContent: ByteSource = CharSource.wrap("ERROR").asByteSource(StandardCharsets.UTF_8)
}

class MockVirusScanService extends VirusScanService {
  override def scan(in: ByteSource): CompletableFuture[VirusScanResult] = Future.successful {
    if (in.contentEquals(virusContent)) {
      new VirusScanResult {
        override def getStatus: VirusScanResult.Status = VirusScanResult.Status.virus
        override def getVirus: Optional[String] = Optional.of("Snake!")
        override def getError: Optional[String] = Optional.empty()
      }
    } else if (in.contentEquals(errorContent)) {
      new VirusScanResult {
        override def getStatus: VirusScanResult.Status = VirusScanResult.Status.error
        override def getVirus: Optional[String] = Optional.empty()
        override def getError: Optional[String] = Optional.of("NaN")
      }
    } else {
      new VirusScanResult {
        override def getStatus: VirusScanResult.Status = VirusScanResult.Status.clean
        override def getVirus: Optional[String] = Optional.empty()
        override def getError: Optional[String] = Optional.empty()
      }
    }
  }.toJava.asInstanceOf[CompletableFuture[VirusScanResult]]

  override def status(): CompletableFuture[VirusScanServiceStatus] =
    Future.successful {
      new VirusScanServiceStatus {
        override def isAvailable: Boolean = true
        override def getStatusMessage: String = "OK"
      }
    }.toJava.asInstanceOf[CompletableFuture[VirusScanServiceStatus]]
}
