package system

import javax.inject.{Inject, Singleton}

import play.api.Configuration
import play.api.mvc.{EssentialAction, EssentialFilter}

import scala.concurrent.ExecutionContext

@Singleton
class AdditionalSecurityHeadersFilter @Inject() (
  configuration: Configuration
)(implicit executionContext: ExecutionContext) extends EssentialFilter {

  private val featurePolicyHeaderValue = configuration.get[String]("play.filters.headers.featurePolicy")
  private val reportToHeaderValue = configuration.get[String]("play.filters.headers.reportTo")

  override def apply(next: EssentialAction): EssentialAction = EssentialAction { req =>
    next(req).map(result => result.withHeaders(
      "Feature-Policy" -> featurePolicyHeaderValue,
      "Report-To" -> reportToHeaderValue
    ))
  }
}
