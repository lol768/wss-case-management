package system

import akka.actor.ActorSystem
import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides}
import javax.inject.Singleton

import scala.concurrent.ExecutionContext

class ThreadsModule extends AbstractModule {
  override def configure(): Unit = {}

  @Provides
  @Singleton
  @Named("mailer")
  def mailer(akka: ActorSystem): ExecutionContext =
    akka.dispatchers.lookup("threads.mailer")

  @Provides
  @Singleton
  @Named("objectStorage")
  def objectStorage(akka: ActorSystem): ExecutionContext =
    akka.dispatchers.lookup("threads.objectStorage")

  @Provides
  @Singleton
  @Named("userLookup")
  def userLookup(akka: ActorSystem): ExecutionContext =
    akka.dispatchers.lookup("threads.userLookup")
}
