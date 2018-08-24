package system

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.mvc.{EssentialAction, EssentialFilter}

import scala.concurrent.ExecutionContext

/**
  * Sets some default headers on responses if they haven't already been set
  */
@Singleton
class CacheDefaultHeadersFilter @Inject() (
  configuration: Configuration
)(implicit executionContext: ExecutionContext) extends EssentialFilter {

  private val cacheControlDefaultHeaderValue = configuration.get[String]("play.filters.headers.cacheControl")

  override def apply(next: EssentialAction): EssentialAction = EssentialAction { req =>
    val headers = Seq(
      HeaderNames.CACHE_CONTROL -> cacheControlDefaultHeaderValue
    )

    next(req).map(result =>
      result.withHeaders(
        headers.filterNot { case (name, _) => result.header.headers.contains(name) }: _*
      )
    )
  }

}
