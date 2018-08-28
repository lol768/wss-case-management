package services.tabula

import com.google.inject.ImplementedBy
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.{ServiceResults, TrustedAppsHelper, WSRequestUriBuilder}
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.json.{JsPath, JsValue, JsonValidationError}
import play.api.libs.ws.WSClient
import services.PhotoService
import system.Logging
import uk.ac.warwick.sso.client.trusted.{TrustedApplicationUtils, TrustedApplicationsManager}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[MemberSearchServiceImpl])
trait MemberSearchService {

  def search(query: String): Future[ServiceResult[Seq[TabulaResponseParsers.MemberSearchResult]]]

}

class MemberSearchServiceImpl @Inject()(
  ws: WSClient,
  trustedApplicationsManager: TrustedApplicationsManager,
  photoService: PhotoService,
  configuration: Configuration
)(implicit ec: ExecutionContext) extends MemberSearchService with Logging {

  private val tabulaUsercode = configuration.get[String]("wellbeing.tabula.user")
  private val tabulaQueryUrl = configuration.get[String]("wellbeing.tabula.query")

  override def search(query: String): Future[ServiceResult[Seq[TabulaResponseParsers.MemberSearchResult]]] = {
    val request = ws.url(tabulaQueryUrl).withQueryStringParameters(("query", query))

    val trustedHeaders = TrustedApplicationUtils.getRequestHeaders(
      trustedApplicationsManager.getCurrentApplication,
      tabulaUsercode,
      WSRequestUriBuilder.buildUri(request).toString
    ).asScala.map(h => h.getName -> h.getValue).toSeq

    val jsonResponse = request.addHttpHeaders(trustedHeaders: _*).get()
      .map(r => ServiceResults.catchAsServiceError(Some("Trusted apps integration error")) {
        TrustedAppsHelper.validateResponse(tabulaQueryUrl, r).json
      })

    jsonResponse.map(response => {
      response.flatMap(json => {
        TabulaResponseParsers.validateAPIResponse(json, TabulaResponseParsers.memberSearchResultsReads).fold(
          errors => handleValidationError(json, errors),
          data => {
            Right(data.map(result => result.copy(
              photo = Some(photoService.photoUrl(result.universityID))
            )).sorted)
          }
        )
      })
    })
  }

  private def handleValidationError(json: JsValue, errors: Seq[(JsPath, Seq[JsonValidationError])]): ServiceResult[Seq[TabulaResponseParsers.MemberSearchResult]] = {
    val serviceErrors = errors.map { case (path, validationErrors) =>
      ServiceError(s"$path: ${validationErrors.map(_.message).mkString(", ")}")
    }
    logger.error(s"Could not parse JSON result from Tabula:\n$json\n${serviceErrors.map(_.message).mkString("\n")}")
    Left(serviceErrors.toList)
  }

}
