package specs


import domain.Fixtures.users
import domain.Teams
import play.api.test.Helpers._
import uk.ac.warwick.util.web.Uri

class HomeSpec extends BaseSpec {

  "The home page" should {

    "render home page with no admin links for a student" in {
      val home = req("/").forUser(users.studentNewVisitor).get()
      status(home) mustEqual OK
      contentType(home).get mustEqual "text/html"
      contentAsString(home) must include("Make an enquiry")

      val html = contentAsHtml(home)
      html.navigationPages mustBe 'empty
      html.pageHeading mustBe "My messages"
    }

    // FIXME H2 doesn't like the query for getting open enquiries
    "render team link for member of a test" ignore {
      val home = req("/").forUser(users.ss1).get()
      val html = contentAsHtml(home)
      html.navigationPages mustBe Seq((s"${Teams.WellbeingSupport.name.replace("&", "&amp;")} team", Uri.parse(s"/team/${Teams.WellbeingSupport.id}")))
    }

    "reject a user without a University ID" in {
      val home = req("/").forUser(users.noUniId).get()
      status(home) mustEqual PRECONDITION_FAILED
      contentAsString(home) must include("University ID is required")
    }

    "redirect away an anonymous user" in {
      val res = req("/").get()
      status(res) mustBe SEE_OTHER
      header("Location", res).get must startWith ("https://sso.example.com/login")
    }
  }
}
