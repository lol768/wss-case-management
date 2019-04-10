package services.job

import javax.inject.Inject
import org.quartz._
import services.tabula.ProfileService
import services.{AuditLogContext, ClientService}

import scala.concurrent.{ExecutionContext, Future}

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class UpdateClientsJob @Inject()(
  scheduler: Scheduler,
  clientService: ClientService,
  profileService: ProfileService,
)(implicit executionContext: ExecutionContext) extends AbstractJob(scheduler) {

  override def getDescription(context: JobExecutionContext): String = "Update Clients"

  override def run(implicit context: JobExecutionContext, auditLogContext: AuditLogContext): Future[JobResult] =
    clientService.getForUpdate.flatMap {
      case Left(errors) =>
        val throwable = errors.flatMap(_.cause).headOption
        logger.error("Unable to get clients for update", throwable.orNull)
        Future.successful(JobResult.failure(errors))
      case Right(clients) =>
        profileService.getProfiles(clients.map(_.universityID).toSet).flatMap {
          case Left(errors) =>
            val throwable = errors.flatMap(_.cause).headOption
            logger.error("Unable to get profiles for update", throwable.orNull)
            Future.successful(JobResult.failure(errors))
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
                JobResult.failure(errors)
              case Right(updated) =>
                JobResult.success(s"Updated ${updated.size} clients")
            }
        }
    }

}
