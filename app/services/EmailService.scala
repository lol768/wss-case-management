package services

import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.AuditEvent._
import domain.OutgoingEmail
import domain.dao.{DaoRunner, OutgoingEmailDao}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Named, Singleton}
import org.quartz.{JobBuilder, JobKey, Scheduler, TriggerBuilder}
import play.api.libs.json.Json
import play.api.libs.mailer.{Email, MailerClient}
import services.job.SendOutgoingEmailJob
import warwick.core.Logging
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso.{User, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@ImplementedBy(classOf[EmailServiceImpl])
trait EmailService {
  def queue(email: Email, recipients: Seq[User])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[OutgoingEmail]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Option[OutgoingEmail]]]
  def sendImmediately(email: OutgoingEmail)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
  def allUnsentEmails()(implicit t: TimingContext): Future[ServiceResult[Seq[OutgoingEmail]]]
  def countUnsentEmails()(implicit t: TimingContext): Future[ServiceResult[Int]]
  def oldestUnsentEmail()(implicit t: TimingContext): Future[ServiceResult[Option[OutgoingEmail]]]
  def mostRecentlySentEmail()(implicit t: TimingContext): Future[ServiceResult[Option[OutgoingEmail]]]
}

@Singleton
class EmailServiceImpl @Inject()(
  auditService: AuditService,
  dao: OutgoingEmailDao,
  daoRunner: DaoRunner,
  scheduler: Scheduler,
  userLookupService: UserLookupService,
  mailer: MailerClient,
  @Named("mailer") mailerExecutionContext: ExecutionContext,
)(implicit executionContext: ExecutionContext) extends EmailService with Logging {

  override def queue(email: Email, recipients: Seq[User])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[OutgoingEmail]]] = {
    val emails = recipients.map { u =>
      OutgoingEmail(
        id = None, // Allow the DAO to set this
        email = email,
        recipient = Some(u.usercode)
      )
    }

    daoRunner.run(dao.insertAll(emails)).map { inserted =>
      // This may be a Stream() so call foreach to ensure this is actually run
      inserted.foreach { email =>
        // Schedule the email to be sent
        val key = new JobKey(email.id.toString, "SendOutgoingEmail")
        logger.info(s"Scheduling job with key $key")

        scheduler.scheduleJob(
          JobBuilder.newJob(classOf[SendOutgoingEmailJob])
            .withIdentity(key)
            .usingJobData("id", email.id.toString)
            .build(),
          TriggerBuilder.newTrigger()
            .startNow()
            .build()
        )
      }

      Right(inserted.map(_.parsed))
    }
  }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Option[OutgoingEmail]]] =
    daoRunner.run(dao.get(id)).map(_.map(_.parsed)).map(Right.apply)

  override def sendImmediately(email: OutgoingEmail)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] = {
    def update(e: OutgoingEmail): Future[ServiceResult[OutgoingEmail]] =
      daoRunner.run(dao.update(e, e.updated)).map(_.parsed).map(Right.apply)

    def send(e: OutgoingEmail, user: Option[User]): Future[ServiceResult[Done]] =
      auditService.audit(
        Operation.OutgoingEmail.Send,
        e.id.map(_.toString).orNull,
        Target.OutgoingEmail,
        Json.toJson(e.email)(OutgoingEmail.emailFormatter)
      ) {
        val sendEmail = Future {
          mailer.send(e.email.copy(
            to = user match {
              case Some(u) => Seq(u.name.full.map { full => s"$full <${u.email.get}>" }.getOrElse(u.email.get))
              case None => e.emailAddress.toSeq
            }
          ))
          Right(Done)
        }(mailerExecutionContext)

        sendEmail
          .recover { case t => ServiceResults.exceptionToServiceResult(t) }
          .flatMap {
            case Left(errors) => update(e.copy(lastSendAttempt = Some(JavaTime.offsetDateTime), failureReason = Some(errors.map(_.message).mkString(", ")))).map { _ => Left(errors) }
            case Right(result) => update(e.copy(sent = Some(JavaTime.offsetDateTime), lastSendAttempt = None, failureReason = None)).map { _ => Right(result) }
          }
      }(AuditLogContext.empty())

    if (email.recipient.nonEmpty) {
      // Lookup the recipient email address - the alias may have changed
      userLookupService.getUser(email.recipient.get) match {
        case Success(user) if user.email.nonEmpty =>
          update(email.copy(emailAddress = user.email)).flatMap(_.fold(
            errors => Future.successful(Left(errors)),
            send(_, Some(user))
          ))

        case Success(_) =>
          logger.info(s"Not sending email to ${email.recipient} as they don't have an email address")
          update(email.copy(failureReason = Some("No email address"))).map { _ => Right(Done) }

        case Failure(t) =>
          // User probably doesn't exist
          logger.error(s"Couldn't lookup user ${email.recipient} - ignoring", t)
          update(email.copy(failureReason = Some("User not found"))).map { _ => Right(Done) }
      }
    } else send(email, None)
  }

  override def allUnsentEmails()(implicit t: TimingContext): Future[ServiceResult[Seq[OutgoingEmail]]] =
    daoRunner.run(dao.allUnsentEmails()).map(_.map(_.parsed)).map(Right.apply)

  override def countUnsentEmails()(implicit t: TimingContext): Future[ServiceResult[Int]] =
    daoRunner.run(dao.countUnsentEmails()).map(Right.apply)

  override def oldestUnsentEmail()(implicit t: TimingContext): Future[ServiceResult[Option[OutgoingEmail]]] =
    daoRunner.run(dao.oldestUnsentEmail()).map(_.map(_.parsed)).map(Right.apply)

  override def mostRecentlySentEmail()(implicit t: TimingContext): Future[ServiceResult[Option[OutgoingEmail]]] =
    daoRunner.run(dao.mostRecentlySentEmail()).map(_.map(_.parsed)).map(Right.apply)

}
