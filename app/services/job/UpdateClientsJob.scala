package services.job

import javax.inject.Inject
import org.quartz._
import services.tabula.ProfileService
import services.{AuditLogContext, ClientService}
import system.Logging
import warwick.core.timing.TimingContext

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class UpdateClientsJob @Inject()(
  clientService: ClientService,
  profileService: ProfileService,
  scheduler: Scheduler
)(implicit executionContext: ExecutionContext) extends Job with Logging {

  override def execute(context: JobExecutionContext): Unit = {
    implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()(
      TimingContext.none // TODO could provide a real context per job run, to track sluggish jobs
    )

    Await.result(
      clientService.getForUpdate.flatMap {
        case Left(errors) =>
          val throwable = errors.flatMap(_.cause).headOption
          logger.error("Unable to get clients for update", throwable.orNull)
          Future.successful(Left(errors))
        case Right(clients) =>
          profileService.getProfiles(clients.map(_.universityID).toSet).flatMap {
            case Left(errors) =>
              val throwable = errors.flatMap(_.cause).headOption
              logger.error("Unable to get profiles for update", throwable.orNull)
              Future.successful(Left(errors))
            case Right(profiles) =>
              val (available, missing) = clients.partition(c => profiles.keySet.contains(c.universityID))
              if (missing.nonEmpty) {
                logger.info(s"Could not update ${missing.size} clients: ${missing.map(_.universityID.string).mkString(", ")}")
              }
              clientService.updateClients((
                available.map(c => c.universityID -> Some(profiles(c.universityID).fullName)) ++
                missing.map(c => c.universityID -> c.fullName)
              ).toMap).map {
                case Left(errors) =>
                  val throwable = errors.flatMap(_.cause).headOption
                  logger.error("Could not update clients", throwable.orNull)
                  Left(errors)
                case Right(updated) =>
                  logger.info(s"Updated ${updated.size} clients")
                  Right(updated)
              }
          }
      },
      Duration.Inf
    )
  }

}
