package controllers

import controllers.refiners.AnyTeamActionRefiner
import helpers.ServiceResults
import helpers.ServiceResults.{ServiceError, ServiceResult}
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import services.tabula.MemberSearchService
import services.tabula.TabulaResponseParsers.MemberSearchResult
import services.{CaseService, ClientSummaryService, EnquiryService, RegistrationService}
import warwick.core.timing.TimingContext
import warwick.sso.{User, UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientSearchController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  userLookupService: UserLookupService,
  memberSearchService: MemberSearchService,
  clientSummaryService: ClientSummaryService,
  enquiryService: EnquiryService,
  caseService: CaseService,
  registrationService: RegistrationService
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._

  def search(query: String): Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    if (query.safeTrim.length < 3) {
      Future.successful(Ok(Json.toJson(API.Success(data = Json.obj()))))
    } else {
      // Check Tabula first
      memberSearchService.search(query).successFlatMap { memberResults =>
        // Filter to only return clients
        getClients(memberResults).successFlatMap { memberClients =>
          if (memberClients.nonEmpty) {
            Future.successful(Ok(Json.toJson(API.Success(data = Json.obj(
              "results" -> memberClients.map(toJson)
            )))))
          } else if (query.contains("@")) {
            // Search for alternate email
            clientSummaryService.getByAlternativeEmailAddress(query).successFlatMap {
              case Some(summary) => memberSearchService.search(summary.universityID.string).successMap(uniIdMembers =>
                Ok(Json.toJson(API.Success(data = Json.obj(
                  "results" -> uniIdMembers.map(toJson)
                ))))
              )
              case _ => Future.successful(Ok(Json.toJson(API.Success(data = Json.obj()))))
            }
          } else {
            // Check for usercode directly
            getUser(Usercode(query)).fold(
              _ => Future.successful(Ok(Json.toJson(API.Success(data = Json.obj())))), // Don't care if this fails
              user => {
                isClient(user).successMap {
                  case Some(clientUser) =>
                    Ok(Json.toJson(API.Success(data = Json.obj(
                      "results" -> Json.arr(toJson(clientUser))
                    ))))
                  case _ =>
                    Ok(Json.toJson(API.Success(data = Json.obj())))
                }
              }
            )
          }
        }

      }
    }

  }

  private def getUser(usercode: Usercode): ServiceResult[User] =
    userLookupService.getUser(usercode).fold(
      e => ServiceResults.exceptionToServiceResult(e),
      user => if (user.universityId.nonEmpty)
        Right(user)
      else
        Left(List(ServiceError(s"Found user matching $user but user had no University ID")))
    )

  private def toJson(member: MemberSearchResult) = Json.obj(
    "name" -> s"${member.firstName} ${member.lastName}",
    "department" -> member.department.name,
    "userType" -> member.userType,
    "type" -> "user",
    "value" -> member.universityID.string,
    "photo" -> member.photo
  )

  private def toJson(user: User) = Json.obj(
    "name" -> user.name.full.getOrElse[String]("[Unknown user]"),
    "department" -> user.department.flatMap(_.shortName).getOrElse[String]("[Unknown department]"),
    "userType" -> user.rawProperties.getOrElse[String]("urn:websignon:usertype", "[Unknown type]"),
    "type" -> "user",
    "value" -> user.universityId.map(_.string).get
  )

  private def getClients(members: Seq[MemberSearchResult])(implicit t: TimingContext): Future[ServiceResult[Seq[MemberSearchResult]]] = {
    ServiceResults.futureSequence(members.map(isClient)).map(_.map(_.flatten))
  }

  private def isClient(member: MemberSearchResult)(implicit t: TimingContext): Future[ServiceResult[Option[MemberSearchResult]]] = {
    def hasEnquiry = enquiryService.findEnquiriesForClient(member.universityID).map(_.map(_.nonEmpty))
    def hasCase = caseService.findForClient(member.universityID).map(_.map(_.nonEmpty))
    def hasRegistration = registrationService.get(member.universityID).map(_.map(_.nonEmpty))
    def hasSummary = clientSummaryService.get(member.universityID).map(_.map(_.nonEmpty))

    isAnyTrue(hasEnquiry #:: hasCase #:: hasRegistration #:: hasSummary #:: Stream.empty[Future[ServiceResult[Boolean]]]).map(_.map(result =>
      if (result) {
        Some(member)
      } else {
        None
      }
    ))
  }

  private def isClient(user: User)(implicit t: TimingContext): Future[ServiceResult[Option[User]]] = {
    if (user.universityId.isEmpty) {
      Future.successful(Right(None))
    } else {
      def hasEnquiry = enquiryService.findEnquiriesForClient(user.universityId.get).map(_.map(_.nonEmpty))
      def hasCase = caseService.findForClient(user.universityId.get).map(_.map(_.nonEmpty))
      def hasRegistration = registrationService.get(user.universityId.get).map(_.map(_.nonEmpty))
      def hasSummary = clientSummaryService.get(user.universityId.get).map(_.map(_.nonEmpty))

      isAnyTrue(hasEnquiry #:: hasCase #:: hasRegistration #:: hasSummary #:: Stream.empty[Future[ServiceResult[Boolean]]]).map(_.map(result =>
        if (result) {
          Some(user)
        } else {
          None
        }
      ))
    }
  }

  private def isAnyTrue(queries: Stream[Future[ServiceResult[Boolean]]]): Future[ServiceResult[Boolean]] =
    if (queries.isEmpty) {
      Future.successful(Right(false))
    } else {
      queries.head.flatMap(_.fold(
        errors => Future.successful(Left(errors)),
        isHead => {
          if (isHead) {
            Future.successful(Right(true))
          } else {
            isAnyTrue(queries.tail)
          }
        }
      ))
    }


}
