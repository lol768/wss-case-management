package specs

import domain.Fixtures.users._
import domain._
import org.scalatest.BeforeAndAfterAll
import play.api.test.Helpers._
import services.{CaseService, OwnerService}
import warwick.sso.{User, Usercode}

class CasePermissionSpec extends BaseSpec with BeforeAndAfterAll {

  // Dependencies
  private val caseService = get[CaseService]
  private val ownerService = get[OwnerService]

  // Constants
  private val firstKey = IssueKey("CAS-1000")
  private val nonexistentKey = IssueKey("CASE-9000")

  private def caseViewReq(key: IssueKey) = req(controllers.admin.routes.CaseController.view(key).url)
  private def caseEditReq(key: IssueKey) = req(controllers.admin.routes.CaseController.editForm(key).url)

  // Assertions

  private def assertCanView(key: IssueKey, user: User): Unit = {
    val page = caseViewReq(key).forUser(user).get()
    status(page) mustBe OK
  }

  private def assertCanEdit(key: IssueKey, user: User): Unit = {
    val page = caseEditReq(key).forUser(user).get()
    status(page) mustBe OK
  }

  private def assertCanViewAndEdit(key: IssueKey, user: User): Unit = {
    assertCanView(key, user)
    assertCanEdit(key, user)
  }

  private def assertCannotViewOrEdit(key: IssueKey, user: User): Unit = {
    val view = caseViewReq(key).forUser(user).get()
    status(view) mustBe NOT_FOUND
    val edit = caseEditReq(key).forUser(user).get()
    status(edit) mustBe NOT_FOUND
  }

  // Create data
  override protected def beforeAll(): Unit = {
    val clients = Set(studentCaseClient.universityId.get )
    val tags = Set.empty[CaseTag]
    val created = caseService.create(CaseSave("Mental health assessment", None, None, CaseCause.New, Set(), Set(), Set()), clients, tags, Teams.MentalHealth, None, None).serviceValue
    created.key mustBe firstKey
    created.team mustBe Teams.MentalHealth

    ownerService.setCaseOwners(created.id, Set[Usercode](
      Usercode("mh1"), // Mental Health member
      Usercode("ss1"), // Wellbeing member
      studentNewVisitor.usercode // Some nobody
    ))

    caseService.find(nonexistentKey).futureValue mustBe 'left
  }

  "Detailed case view" should {

    "be denied when not authenticated" in {
      val page = caseViewReq(firstKey).get()
      page.mustBeRedirectToLogin
    }

    "be denied when not authenticated even for nonexistent cases" in {
      val page = caseViewReq(nonexistentKey).get()
      page.mustBeRedirectToLogin
    }

    "be visible and editable to assigned team" in {
      // mh2 is not an owner but they're in the right team
      assertCanViewAndEdit(firstKey, mh2)
    }

    "be visible and editable to owner in assigned team" in {
      assertCanViewAndEdit(firstKey, mh1)
    }

    "be visible and editable to owner from different team" in {
      assertCanViewAndEdit(firstKey, ss1)
    }

    "be denied to a non-owner from different team" in {
      assertCannotViewOrEdit(firstKey, ss2)
    }

    // Someone who may previously have been a valid owner, but
    // then external group changes happen - they shouldn't get
    // continued access just for being an owner.
    "be denied to an owner who isn't in WSS" in {
      assertCannotViewOrEdit(firstKey, studentNewVisitor)
    }

    "be denied to the client" in {
      assertCannotViewOrEdit(firstKey, studentCaseClient)
    }

  }
}
