package system

import akka.actor.ActorSystem
import play.api.inject._
import javax.inject.{Inject, Provider}
import play.api.Configuration
import play.api.inject.SimpleModule

import scala.concurrent.ExecutionContext

class ThreadsModule extends SimpleModule((_, configuration) => {
  configuration.get[Configuration]("threads").subKeys.toSeq.map { name =>
    bind[ExecutionContext].qualifiedWith(name).to(new NamedThreadPoolProvider(name))
  }
})

class NamedThreadPoolProvider(name: String) extends Provider[ExecutionContext] {
  @Inject private var akka: ActorSystem = _

  lazy val get: ExecutionContext = akka.dispatchers.lookup(s"threads.$name")
}