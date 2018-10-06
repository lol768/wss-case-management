package views.tags.profiles

import domain.Fixtures
import org.scalatestplus.play.PlaySpec

class ProfilesTest extends PlaySpec {

  "typeAndDepartment" should {

    "render student" in {
      typeAndDepartment(Fixtures.profiles.undergraduate) mustBe
        "First year Undergraduate student, Chemistry"
    }

    "render student with no group" in {
      val noGroup = Fixtures.profiles.undergraduate.copy(group = None)
      typeAndDepartment(noGroup) mustBe
        "First year student, Chemistry"
    }

    "render staff" in {
      typeAndDepartment(Fixtures.profiles.mat) mustBe
        "Staff, IT Services"
    }

  }
}
