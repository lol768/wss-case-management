package services

import java.math.MathContext
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.AuditEvent
import domain.CustomJdbcTypes._
import domain.dao.{AuditDao, DaoRunner}
import helpers.ConditionalChain._
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json._
import slick.jdbc.PostgresProfile.api._
import uk.ac.warwick.util.logging.AuditLogger
import uk.ac.warwick.util.logging.AuditLogger.RequestInformation
import warwick.core.timing.TimingContext
import warwick.sso.Usercode

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

case class AuditLogContext(
  usercode: Option[Usercode] = None,
  ipAddress: Option[String] = None,
  userAgent: Option[String] = None,
  timingData: TimingContext.Data
) extends TimingContext

object AuditLogContext {
  def empty()(implicit t: TimingContext): AuditLogContext = AuditLogContext(timingData = t.timingData)
}

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {
  def audit[A](operation: Symbol, targetId: String, targetType: Symbol, data: JsValue)(f: => Future[ServiceResult[A]])(implicit context: AuditLogContext): Future[ServiceResult[A]]
  def audit[A](operation: Symbol, targetIdTransform: A => String, targetType: Symbol, data: JsValue)(f: => Future[ServiceResult[A]])(implicit context: AuditLogContext): Future[ServiceResult[A]]
  def findRecentTargetIDsByOperation(operation: Symbol, usercode: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[String]]]
}

@Singleton
class AuditServiceImpl @Inject()(
  dao: AuditDao,
  daoRunner: DaoRunner
)(implicit ec: ExecutionContext) extends AuditService {

  lazy val AUDIT_LOGGER: AuditLogger = AuditLogger.getAuditLogger("casemanagement")

  private def doAudit[A](operation: Symbol, targetId: String, targetType: Symbol, data: JsValue)(implicit context: AuditLogContext): Unit = {
    def handle(value: JsValue): AnyRef = value match {
      case JsBoolean(b) => Option(b).map(Boolean.box).getOrElse("-")
      case JsNumber(n) => Option(n).map(bd => new java.math.BigDecimal(bd.toDouble, MathContext.DECIMAL128)).getOrElse("-")
      case JsString(s) => Option(s).getOrElse("-")
      case JsArray(array) => array.map(handle).toArray
      case obj: JsObject => obj.value.map { case (k, v) => k -> handle(v) }.asJava
      case _ => "-"
    }

    val dataMap = (data match {
      case obj: JsObject => obj.value.map { case (k, v) => new AuditLogger.Field(k) -> handle(v) }
      case _ => Map[AuditLogger.Field, AnyRef]()
    }) ++ Map(
      new AuditLogger.Field(targetType.name) -> targetId
    )

    AUDIT_LOGGER.log(
      RequestInformation.forEventType(operation.name)
        .when(context.ipAddress.nonEmpty) { _.withIpAddress(context.ipAddress.get) }
        .when(context.userAgent.nonEmpty) { _.withUserAgent(context.userAgent.get) }
        .when(context.usercode.nonEmpty) { _.withUsername(context.usercode.get.string) },
      dataMap.asJava
    )
  }

  override def audit[A](operation: Symbol, targetIdTransform: A => String, targetType: Symbol, data: JsValue)(f: => Future[ServiceResult[A]])(implicit context: AuditLogContext): Future[ServiceResult[A]] =
    f.flatMap {
      case Left(errors) =>
        Future.successful(Left(errors))
      case Right(result) =>
        val targetId = targetIdTransform(result)
        daoRunner.run(
          dao.insert(AuditEvent(
            id = UUID.randomUUID(),
            operation = operation,
            usercode = context.usercode,
            data = data,
            targetId = targetId,
            targetType = targetType
          ))
        ).map { _ =>
          doAudit(operation, targetId, targetType, data)
          Right(result)
        }
    }

  override def audit[A](operation: Symbol, targetId: String, targetType: Symbol, data: JsValue)(f: => Future[ServiceResult[A]])(implicit context: AuditLogContext): Future[ServiceResult[A]] =
    f.flatMap {
      case Left(errors) =>
        Future.successful(Left(errors))
      case Right(result) =>
        daoRunner.run(
          dao.insert(AuditEvent(
            id = UUID.randomUUID(),
            operation = operation,
            usercode = context.usercode,
            data = data,
            targetId = targetId,
            targetType = targetType
          ))
        ).map { _ =>
          doAudit(operation, targetId, targetType, data)
          Right(result)
        }
    }

  private val asUUID = SimpleExpression.unary[String, UUID] { (id, qb) =>
    qb.expr(id)
    qb.sqlBuilder += "::uuid"
  }

  override def findRecentTargetIDsByOperation(operation: Symbol, usercode: Usercode, limit: Int)(implicit t: TimingContext): Future[ServiceResult[Seq[String]]] =
    daoRunner.run(
      dao.findByOperationAndUsercodeQuery(operation, usercode)
        .groupBy(_.targetId)
        .map { case (targetId, q) => (targetId, q.map(_.date).max) }
        .sortBy { case (_, maxDate) => maxDate.desc }
        .take(limit)
        .map { case (targetId, _) => targetId }
        .result
    ).map(Right.apply)

}