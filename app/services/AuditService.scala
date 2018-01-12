package services

import java.math.MathContext
import javax.inject.{Inject, Singleton}

import com.google.inject.ImplementedBy
import domain.AuditEvent
import domain.dao.AuditDao
import helpers.ConditionalChain._
import helpers.ServiceResults.ServiceResult
import play.api.libs.json._
import uk.ac.warwick.util.logging.AuditLogger
import uk.ac.warwick.util.logging.AuditLogger.RequestInformation
import warwick.sso.Usercode

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

case class AuditLogContext(
  usercode: Option[Usercode] = None,
  ipAddress: Option[String] = None,
  userAgent: Option[String] = None
)

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {
  def audit[A](operation: String, targetId: String, targetType: String, data: JsValue)(f: => Future[ServiceResult[A]])(implicit context: AuditLogContext): Future[ServiceResult[A]]
}

@Singleton
class AuditServiceImpl @Inject()(
  dao: AuditDao
)(implicit ec: ExecutionContext) extends AuditService {

  lazy val AUDIT_LOGGER: AuditLogger = AuditLogger.getAuditLogger("APP_NAME")

  override def audit[A](operation: String, targetId: String, targetType: String, data: JsValue)(f: => Future[ServiceResult[A]])(implicit context: AuditLogContext): Future[ServiceResult[A]] =
    f.flatMap { result =>
      dao.insert(AuditEvent(
        operation = operation,
        usercode = context.usercode,
        data = data,
        targetId = targetId,
        targetType = targetType
      )).map { _ =>
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
          new AuditLogger.Field(targetType) -> targetId
        )

        AUDIT_LOGGER.log(
          RequestInformation.forEventType(operation)
            .when(context.ipAddress.nonEmpty) { _.withIpAddress(context.ipAddress.get) }
            .when(context.userAgent.nonEmpty) { _.withUserAgent(context.userAgent.get) }
            .when(context.usercode.nonEmpty) { _.withUsername(context.usercode.get.string) },
          dataMap.asJava
        )

        result
      }
    }

}