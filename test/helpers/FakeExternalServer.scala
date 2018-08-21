package helpers

import play.api.http.Port
import play.api.routing.Router
import play.api.test.TestServer

import scala.reflect.ClassTag
import scala.util.Random

object FakeExternalServer {
  def randomPort(): Port = new Port(10000 + new Random().nextInt(1000))

  def apply[R <: Router : ClassTag](port: Port, config: (String, Any)*)(block: Port => Any): Unit = {
    val server = TestServer(
      port = port.value,
      application = TestApplications.minimalWithRouter[R](config : _*)
    )
    try {
      server.start()
      block(new Port(server.runningHttpPort.get))
    } finally {
      server.stop()
    }
  }
}
