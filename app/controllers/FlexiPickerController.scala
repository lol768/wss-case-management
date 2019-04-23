package controllers

import controllers.FlexiPickerController._
import controllers.refiners.AnyTeamActionRefiner
import domain.{SitsProfile, UserType}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import helpers.StringUtils._
import javax.inject.{Inject, Named, Singleton}
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent}
import services.PermissionService
import services.tabula.TabulaResponseParsers.TabulaMemberSearchResult
import services.tabula.{ProfileService, TabulaMemberSearchService}
import warwick.sso._

import scala.concurrent.{ExecutionContext, Future}

object FlexiPickerController {
  case class FlexiPickerQuery(
    query: String,
    exact: Boolean,
    includeUsers: Boolean,
    includeGroups: Boolean,
    universityId: Boolean,
    potentialClientsOnly: Boolean,
    team: Option[String]
  )

  val form: Form[FlexiPickerQuery] = Form(mapping(
    "query" -> nonEmptyText,
    "exact" -> default(boolean, false),
    "includeUsers" -> default(boolean, true),
    "includeGroups" -> default(boolean, false),
    "universityId" -> default(boolean, false),
    "potentialClientsOnly" -> default(boolean, false),
    "team" -> optional(text)
  )(FlexiPickerQuery.apply)(FlexiPickerQuery.unapply))

  sealed abstract class FlexiPickerResult(val `type`: String) {
    val value: String
  }
  object FlexiPickerResult {
    def apply(user: User, universityIdAsValue: Boolean): Option[UserFlexiPickerResult] =
      if (universityIdAsValue && user.universityId.isEmpty) None
      else Some(
        UserFlexiPickerResult(
          user.name.full.getOrElse[String]("[Unknown user]"),
          user.department.flatMap(_.shortName).getOrElse[String]("[Unknown department]"),
          user.rawProperties.getOrElse[String]("urn:websignon:usertype", "[Unknown type]"),
          if (universityIdAsValue) user.universityId.get.string else user.usercode.string
        )
      )

    def apply(member: TabulaMemberSearchResult, universityIdAsValue: Boolean): UserFlexiPickerResult =
      UserFlexiPickerResult(
        s"${member.firstName} ${member.lastName}",
        member.department.name,
        member.userType,
        if (universityIdAsValue) member.universityID.string else member.usercode.string,
        member.photo
      )

    def apply(profile: SitsProfile, universityIdAsValue: Boolean): UserFlexiPickerResult =
      UserFlexiPickerResult(
        profile.fullName,
        profile.department.name,
        profile.userType.entryName,
        if (universityIdAsValue) profile.universityID.string else profile.usercode.string,
        profile.photo
      )

    def apply(group: Group): GroupFlexiPickerResult =
      GroupFlexiPickerResult(
        group.title.getOrElse(group.name.string),
        group.`type`,
        group.name.string
      )

    implicit val writesFlexiPickerResult: Writes[FlexiPickerResult] = {
      case u: UserFlexiPickerResult =>
        Json.obj(
          "name" -> u.name,
          "department" -> u.department,
          "userType" -> u.userType,
          "type" -> "user",
          "value" -> u.value,
          "photo" -> u.photo
        )

      case g: GroupFlexiPickerResult =>
        Json.obj(
          "title" -> g.title,
          "groupType" -> g.groupType,
          "type" -> "group",
          "value" -> g.value
        )
    }
  }

  case class UserFlexiPickerResult(
    name: String,
    department: String,
    userType: String,
    value: String,
    photo: Option[String] = None
  ) extends FlexiPickerResult("user")

  case class GroupFlexiPickerResult(
    title: String,
    groupType: String,
    value: String
  ) extends FlexiPickerResult("group")
}

@Singleton
class FlexiPickerController @Inject()(
  anyTeamActionRefiner: AnyTeamActionRefiner,
  userLookupService: UserLookupService,
  memberSearchService: TabulaMemberSearchService,
  groupService: GroupService,
  profileService: ProfileService,
  permissions: PermissionService,
  configuration: Configuration,
  @Named("userLookup") userLookupExecutionContext: ExecutionContext,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import anyTeamActionRefiner._

  private[this] val clientUserTypes = configuration.get[Seq[String]]("wellbeing.validClientUserTypes").flatMap(UserType.namesToValuesMap.get)

  def queryJson: Action[AnyContent] = AnyTeamMemberRequiredAction.async { implicit request =>
    form.bindFromRequest.fold(
      _ => Future.successful(Ok(Json.toJson(API.Success(data = Json.obj())))),

      flexiPickerQuery => {
        val query = flexiPickerQuery.query.trim

        val futures: Seq[Future[ServiceResult[Seq[FlexiPickerResult]]]] =
          Seq(
            if (flexiPickerQuery.includeGroups)
              Future { groupService.getWebGroup(GroupName(query)).toOption.flatten.toSeq }(userLookupExecutionContext)
                .map { g => Right(g.map(FlexiPickerResult.apply)) }
            else Future.successful(Right(Nil)),

            if (flexiPickerQuery.includeUsers)
              Future { userLookupService.getUser(warwick.sso.Usercode(query)).toOption.toSeq }(userLookupExecutionContext)
                .map(_.filter { u => matchesTeamFilter(flexiPickerQuery, u.usercode) && matchesPotentialClientFilter(flexiPickerQuery, UserType(u)) })
                .map { u => Right(u.flatMap(FlexiPickerResult.apply(_, flexiPickerQuery.universityId))) }
            else Future.successful(Right(Nil)),

            if (flexiPickerQuery.includeUsers && query.matches("^[0-9]{7,}$"))
              profileService.getProfile(UniversityID(query)).map(_.value)
                .flatMap {
                  case Right(Some(profile)) if matchesTeamFilter(flexiPickerQuery, profile.usercode) && matchesPotentialClientFilter(flexiPickerQuery, profile.userType) => Future.successful(Right(Seq(FlexiPickerResult.apply(profile, flexiPickerQuery.universityId))))
                  case _ =>
                    Future { userLookupService.getUsers(Seq(UniversityID(query)), includeDisabled = true).toOption.flatMap(_.headOption.map(_._2)).toSeq }(userLookupExecutionContext)
                      .map(_.filter { u => matchesTeamFilter(flexiPickerQuery, u.usercode) && matchesPotentialClientFilter(flexiPickerQuery, UserType(u)) })
                      .map { g => Right(g.flatMap(FlexiPickerResult.apply(_, flexiPickerQuery.universityId))) }
                }
            else Future.successful(Right(Nil)),

            if (!flexiPickerQuery.exact && flexiPickerQuery.includeGroups)
              Future { groupService.getGroupsForQuery(query).getOrElse(Nil) }(userLookupExecutionContext)
                .map { g => Right(g.map(FlexiPickerResult.apply)) }
            else Future.successful(Right(Nil)),

            if (!flexiPickerQuery.exact && flexiPickerQuery.includeUsers)
              memberSearchService.search(query)
                .map(_.right.map(_.filter { r => matchesTeamFilter(flexiPickerQuery, r.usercode) && matchesPotentialClientFilter(flexiPickerQuery, UserType.namesToValuesMap(r.userType)) }))
                .map(_.right.map(_.map(FlexiPickerResult.apply(_, flexiPickerQuery.universityId))))
                .flatMap {
                  case Right(results) if results.nonEmpty => Future.successful(Right(results))
                  case _ => queryUsers(flexiPickerQuery).map(_.right.map(_.flatMap(FlexiPickerResult.apply(_, flexiPickerQuery.universityId))))
                }
            else Future.successful(Right(Nil))
          )

        ServiceResults.futureSequence(futures).map(_.fold(
          _ => Ok(Json.toJson(API.Success(data = Json.obj()))), // Don't care if this fails
          rawResults => {
            val flatResults = rawResults.flatten

            // Don't include user results where we have rich user results
            val results = flatResults.filterNot {
              case u: UserFlexiPickerResult =>
                u.photo.isEmpty && flatResults.exists {
                  case ou: UserFlexiPickerResult => ou.value == u.value && ou.photo.nonEmpty
                  case _ => false
                }

              case _ => false
            }.distinct

            Ok(Json.toJson(API.Success(data = Json.obj(
              "results" -> results.map(Json.toJson(_)(FlexiPickerResult.writesFlexiPickerResult))
            ))))
          }
        ))
      }
    )
  }

  private def matchesTeamFilter(flexiPickerQuery: FlexiPickerQuery, usercode: Usercode): Boolean =
    if (!flexiPickerQuery.team.exists(_.hasText)) true
    else flexiPickerQuery.team.map(_.trim).exists { teamId =>
      permissions.teams(usercode).fold(
        _ => false,
        teams =>
          if (teams.nonEmpty && (teamId == "*" || teamId == "any")) true
          else teams.exists(_.id == teamId)
      )
    }

  private def matchesPotentialClientFilter(flexiPickerQuery: FlexiPickerQuery, userType: UserType): Boolean =
    !flexiPickerQuery.potentialClientsOnly || clientUserTypes.contains(userType)

  final val EnoughResults = 10

  final val FirstName = "givenName"
  final val LastName = "sn"
  final val UniversityId = "warwickUniId"
  final val Usercode = "cn"

  private def queryUsers(flexiPickerQuery: FlexiPickerQuery): Future[ServiceResult[Seq[User]]] = Future {
    def usersMatching(filter: (String, String)*) = userLookupService.searchUsers(filter.flatMap {
      case (name, value) if value.trim.nonEmpty => Some(name -> (value + "*"))
      case _ => None
    }.toMap[String, String]).toOption.map(_.filter { u => matchesTeamFilter(flexiPickerQuery, u.usercode) && matchesPotentialClientFilter(flexiPickerQuery, UserType(u)) }).getOrElse(Nil)

    val words = flexiPickerQuery.query.trim.split("\\s+")

    var results = Seq.empty[User]

    if (words.length == 2) {
      results ++= usersMatching(FirstName -> words(0), LastName -> words(1))

      if (results.length < EnoughResults)
        results ++= usersMatching(LastName -> words(0), FirstName -> words(1))
    } else if (words.length == 1) {
      val word = words(0)

      if (word.toCharArray.forall(_.isDigit))
        results ++= usersMatching(UniversityId -> word)
      else
        results ++= usersMatching(LastName -> word)

      if (results.length < EnoughResults)
        results ++= usersMatching(FirstName -> word)

      if (results.length < EnoughResults)
        results ++= usersMatching(Usercode -> word)
    }

    Right(results.sortBy(_.name.full))
  }(userLookupExecutionContext)

}
