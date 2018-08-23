package controllers

import javax.inject.{Inject, Singleton}

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import services.SecurityService
import warwick.sso._

case class FlexiPickerQuery(
  query: String,
  exact: Boolean,
  includeUsers: Boolean,
  includeGroups: Boolean,
  includeEmail: Boolean,
  universityId: Boolean
)

@Singleton
class FlexiPickerController @Inject()(
  securityService: SecurityService,
  userLookupService: UserLookupService,
  groupService: GroupService
) extends BaseController {

  import securityService._

  private val form = Form[FlexiPickerQuery](
    mapping (
      "query" -> nonEmptyText,
      "exact" -> default(boolean, false),
      "includeUsers" -> default(boolean, true),
      "includeGroups" -> default(boolean, false),
      "includeEmail" -> default(boolean, false),
      "universityId" -> default(boolean, false)
    )(FlexiPickerQuery.apply)(FlexiPickerQuery.unapply)
  )

  def queryJson = SigninRequiredAction { implicit request =>
    form.bindFromRequest.fold(
      _ => Ok(Json.toJson(Map[String, String]())).withHeaders(CONTENT_TYPE -> "text/json"),

      flexiPickerQuery => {
        val query = flexiPickerQuery.query.trim

        var results = Seq.empty[Either[User, Group]]

        if (flexiPickerQuery.exact) {
          results ++=
            (if (flexiPickerQuery.includeGroups) groupService.getWebGroup(GroupName(query)).toOption.flatten.toSeq else Nil).map(Right.apply) ++
            (if (flexiPickerQuery.includeUsers) userLookupService.getUser(warwick.sso.Usercode(query)).toOption.toSeq else Nil).map(Left.apply) ++
            (if (flexiPickerQuery.includeUsers) userLookupService.getUsers(Seq(UniversityID(query)), includeDisabled = true).toOption.flatMap(_.headOption.map(_._2)).toSeq else Nil).map(Left.apply)
        } else {
          results ++=
            (if (flexiPickerQuery.includeGroups) groupService.getGroupsForQuery(query).getOrElse(Nil) else Nil).map(Right.apply) ++
            (if (flexiPickerQuery.includeUsers) queryUsers(query) else Nil).map(Left.apply)
        }

        val response = Map("data" -> Map("results" -> results.map(toMap(_, flexiPickerQuery.universityId)).filter(_.nonEmpty)))

        Ok(Json.toJson(response)).withHeaders(CONTENT_TYPE -> "text/json")
      }
    )
  }

  final val EnoughResults = 10

  final val FirstName = "givenName"
  final val LastName = "sn"
  final val UniversityId = "warwickUniId"
  final val Usercode = "cn"

  private def queryUsers(query: String): Seq[User] = {
    def usersMatching(filter: (String, String)*) = userLookupService.searchUsers(filter.flatMap {
      case (name, value) if value.trim.nonEmpty => Some(name -> (value + "*"))
      case _ => None
    }.toMap[String, String]).toOption.getOrElse(Nil)

    val words = query.split("\\s+")

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

    results.sortBy(_.name.full)
  }

  private def toMap(entity: Either[User, Group], universityIdAsValue: Boolean): Map[String, String] = entity match {
    case Left(user) if universityIdAsValue && user.universityId.isEmpty =>
      Map.empty
    case Left(user) =>
      Map(
        "name" -> user.name.full.getOrElse(user.usercode.string),
        "department" -> user.department.flatMap(d => d.name.orElse(d.shortName).orElse(d.code)).orNull,
        "isStaff" -> user.isStaffNotPGR.toString,
        "type" -> "user",
        "value" -> (if (universityIdAsValue) user.universityId.get.string else user.usercode.string)
      )
    case Right(group) =>
      Map(
        "type" -> "group",
        "title" -> group.title.getOrElse(group.name.string),
        "groupType" -> group.`type`,
        "value" -> group.name.string
      )
  }

}
