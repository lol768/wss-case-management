package controllers

import java.time.OffsetDateTime
import java.util.UUID

import controllers.IndexController.{ClientInformation, TeamMemberInformation}
import controllers.refiners.AnyTeamActionRefiner
import domain._
import domain.dao.CaseDao.Case
import helpers.ServiceResults
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services._
import services.tabula.ProfileService
import warwick.sso.{AuthenticatedRequest, UniversityID}

import scala.concurrent.{ExecutionContext, Future}

object IndexController {
  case class ClientInformation(
    enquiries: Seq[EnquiryRender],
    registration: Option[Registration]
  )

  case class TeamMemberInformation(
    teams: Seq[Team],
    enquiriesRequiringAction: Seq[(Enquiry, MessageData)],
    enquiriesAwaitingClient: Seq[(Enquiry, MessageData)],
    closedEnquiries: Int,
    openCases: Seq[(Case, OffsetDateTime)],
    closedCases: Int,
    clients: Map[UUID, Set[Either[UniversityID, SitsProfile]]]
  )
}

@Singleton
class IndexController @Inject()(
  securityService: SecurityService,
  anyTeamActionRefiner: AnyTeamActionRefiner,
  permissions: PermissionService,
  enquiries: EnquiryService,
  registrations: RegistrationService,
  audit: AuditService,
  cases: CaseService,
  profiles: ProfileService,
  clientSummaries: ClientSummaryService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit executionContext: ExecutionContext) extends BaseController {
  import anyTeamActionRefiner._
  import securityService._

  private def clientHome(implicit request: AuthenticatedRequest[AnyContent]): Future[ServiceResult[ClientInformation]] = {
    val client = request.context.user.get.universityId.get

    ServiceResults.zip(
      enquiries.findEnquiriesForClient(client),
      registrations.get(client)
    ).flatMap(_.fold(
      errors => Future.successful(Left(errors)),
      { case (e, registration) =>
        val result = Future.successful(Right(ClientInformation(e, registration)))

        // Record an EnquiryView event for the first enquiry as that's open by default in the accordion
        e.headOption.map { e =>
          audit.audit('EnquiryView, e.enquiry.id.get.toString, 'Enquiry, Json.obj())(result)
        }.getOrElse(result)
      }
    ))
  }

  private def teamMemberHome(implicit request: AuthenticatedRequest[AnyContent]): Future[ServiceResult[Option[TeamMemberInformation]]] = {
    val usercode = request.context.user.get.usercode

    permissions.teams(usercode).fold(
      errors => Future.successful(Left(errors)),
      teams => {
        if (teams.isEmpty) Future.successful(Right(None))
        else ServiceResults.zip(
          enquiries.findEnquiriesNeedingReply(usercode),
          enquiries.findEnquiriesAwaitingClient(usercode),
          enquiries.countClosedEnquiries(usercode),
          cases.listOpenCases(usercode),
          cases.countClosedCases(usercode)
        ).successFlatMapTo { case (enquiriesNeedingReply, enquiriesAwaitingClient, closedEnquiries, openCases, closedCases) =>
          cases.getClients(openCases.flatMap { case (c, _) => c.id }.toSet).successFlatMapTo { caseClients =>
            val clients = (enquiriesNeedingReply ++ enquiriesAwaitingClient).map{ case (e, _) => e.id.get -> Set(e.universityID) }.toMap ++ caseClients

            profiles.getProfiles(clients.values.flatten.toSet).successMapTo { clientProfiles =>
              val resolvedClients = clients.mapValues(_.map(c => clientProfiles.get(c).map(Right.apply).getOrElse(Left(c))))

              Some(TeamMemberInformation(
                teams,
                enquiriesNeedingReply,
                enquiriesAwaitingClient,
                closedEnquiries,
                openCases,
                closedCases,
                resolvedClients
              ))
            }
          }
        }
      }
    )
  }

  def home: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    ServiceResults.zip(
      clientHome,
      teamMemberHome
    ).successMap { case (clientInformation, teamMemberInformation) =>
      Ok(views.html.home(clientInformation, teamMemberInformation, uploadedFileControllerHelper.supportedMimeTypes))
    }
  }

  def closedEnquiries: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    enquiries.findClosedEnquiries(request.context.user.get.usercode).successFlatMap { enquiries =>
      val clients = enquiries.map { case (e, _) => e.id.get -> Set(e.universityID) }.toMap

      profiles.getProfiles(clients.values.flatten.toSet).successMap { profiles =>
        val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
        Ok(views.html.admin.closedEnquiries(enquiries, resolvedClients))
      }
    }
  }

  def closedCases: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    cases.listClosedCases(request.context.user.get.usercode).successFlatMap { closedCases =>
      cases.getClients(closedCases.flatMap { case (c, _) => c.id }.toSet).successFlatMap { clients =>
        profiles.getProfiles(clients.values.flatten.toSet).successMap { profiles =>
          val resolvedClients = clients.mapValues(_.map(c => profiles.get(c).map(Right.apply).getOrElse(Left(c))))
          Ok(views.html.admin.closedCases(closedCases, resolvedClients))
        }
      }
    }
  }

  def atRiskClients: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    val usercode = request.context.user.get.usercode

    Future.successful(permissions.teams(usercode)).successFlatMap(teams =>
      clientSummaries.findAtRisk(teams.contains(Teams.MentalHealth)).successMap(clients =>
        Ok(views.html.admin.atRiskClients(clients, teams.contains(Teams.MentalHealth)))
      )
    )
  }

  def redirectToPath(path: String, status: Int = MOVED_PERMANENTLY) = Action {
    Redirect(s"/${path.replaceFirst("^/","")}", status)
  }
}
