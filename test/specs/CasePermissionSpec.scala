package specs

import domain.IssueKeyType._
import domain.{CaseTag, Fixtures, IssueKey, Teams}
import org.scalatest.BeforeAndAfterAll
import services.{CaseService, OwnerService}
import warwick.sso.{UniversityID, Usercode}
import play.api.test.Helpers._

class CasePermissionSpec extends BaseSpec with BeforeAndAfterAll {

  val caseService = get[CaseService]
  val ownerService = get[OwnerService]

  val firstKey = IssueKey("CAS-1000")
  val nonexistentKey = IssueKey("CASE-9000")

  override protected def beforeAll(): Unit = {
    val c = Fixtures.cases.newCaseNoId()
    val clients = Set[UniversityID](
      UniversityID("0672088")
    )
    val tags = Set.empty[CaseTag]
    val created = caseService.create(c, clients, tags).serviceValue
    created.key.value mustBe firstKey
    created.team mustBe Teams.MentalHealth

    ownerService.setCaseOwners(created.id.get, Set(
      "mh1", // Mental Health member
      "ss1", // Wellbeing member
      "dilbert" // Some nobody
    ).map(Usercode.apply))

    caseService.find(nonexistentKey).futureValue mustBe 'left
  }

  def caseViewReq(key: IssueKey) = req(controllers.admin.routes.CaseController.view(key).url)

  "Detailed case view" should {

    "be denied when not authenticated" in {
      val page = caseViewReq(firstKey).get()
      page.mustBeRedirectToLogin
    }

    "be denied when not authenticated even for nonexistent cases" in {
      val page = caseViewReq(nonexistentKey).get()
      page.mustBeRedirectToLogin
    }

    "be visible to assigned team" in {
      // mh2 is not an owner but they're in the right team
      val page = caseViewReq(firstKey).forUser(Fixtures.users.mh2).get()
      status(page) mustBe OK
    }

    "be visible to owner in assigned team" in {
      val page = caseViewReq(firstKey).forUser(Fixtures.users.mh1).get()
      status(page) mustBe OK
    }

    "be visible to owner from different team" in {
      val page = caseViewReq(firstKey).forUser(Fixtures.users.ss1).get()
      status(page) mustBe OK
    }

    "be denied to a non-owner from different team" in {
      val page = caseViewReq(firstKey).forUser(Fixtures.users.ss2).get()
      status(page) mustBe NOT_FOUND
    }

    "be denied to an owner who isn't in WSS" in {
      val page = caseViewReq(firstKey).forUser(Fixtures.users.studentNewVisitor).get()
      status(page) mustBe NOT_FOUND
    }

  }
}
