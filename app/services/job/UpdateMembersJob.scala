package services.job

import javax.inject.Inject
import org.quartz._
import services.{AuditLogContext, MemberService}
import warwick.core.Logging
import warwick.sso.UserLookupService

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class UpdateMembersJob @Inject()(
  memberService: MemberService,
  userLookupService: UserLookupService,
  scheduler: Scheduler
)(implicit executionContext: ExecutionContext) extends Job with Logging {

  override def execute(context: JobExecutionContext): Unit = {
    implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()

    Await.result(
      memberService.getForUpdate.flatMap {
        case Left(errors) =>
          val throwable = errors.flatMap(_.cause).headOption
          logger.error("Unable to get members for update", throwable.orNull)
          Future.successful(Left(errors))
        case Right(members) =>
          val userMap = userLookupService.getUsers(members.map(_.usercode)).toOption.getOrElse(Map())
          val (available, missing) = members.partition(m => userMap.get(m.usercode).exists(_.isFound))
          if (missing.nonEmpty) {
            logger.info(s"Could not update ${missing.size} members: ${missing.map(_.usercode.string).mkString(", ")}")
          }
          memberService.updateMembers((
            available.map(m => m.usercode -> userMap(m.usercode).name.full) ++
            missing.map(m => m.usercode -> m.fullName)
          ).toMap).map {
            case Left(errors) =>
              val throwable = errors.flatMap(_.cause).headOption
              logger.error("Could not update members", throwable.orNull)
              Left(errors)
            case Right(updated) =>
              logger.info(s"Updated ${updated.size} members")
              Right(updated)
          }
      },
      Duration.Inf
    )
  }

}
