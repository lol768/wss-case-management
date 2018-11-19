package system

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.Cookie
import play.api.mvc.{EssentialAction, EssentialFilter}
import warwick.core.Logging

import scala.concurrent.ExecutionContext

@Singleton
class CSRFSameSiteCookieFilter @Inject() (
  configuration: Configuration
)(implicit executionContext: ExecutionContext) extends EssentialFilter with Logging {

  private val headerName: Option[String] = configuration.get[Option[String]]("play.filters.csrf.cookie.name")
  private val sameSite: Option[Cookie.SameSite] = configuration.get[Option[String]]("play.filters.csrf.cookie.sameSite").flatMap { value =>
    val result = Cookie.SameSite.parse(value)
    if (result.isEmpty) {
      logger.warn(
        s"""Assuming play.filters.csrf.cookie.sameSite = null, since "$value" is not a valid SameSite value"""
      )
    }
    result
  }

  override def apply(next: EssentialAction): EssentialAction = EssentialAction { req =>
    next(req).map { result =>
      if (headerName.nonEmpty && sameSite.nonEmpty) {
        result.newCookies.find { c => headerName.contains(c.name) }.map { c =>
          result.withCookies(c.copy(sameSite = sameSite))
        }.getOrElse(result)
      } else result
    }
  }
}
