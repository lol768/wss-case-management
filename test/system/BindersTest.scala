package system

import java.time.{Duration, OffsetDateTime, ZoneId}

import org.scalatestplus.play.PlaySpec
import Binders._
import helpers.JavaTime
import play.api.mvc.{PathBindable, QueryStringBindable}
import warwick.sso.UniversityID

class BindersTest extends PlaySpec {

  "UniversityID" should {
    "be bindable in paths" in {
      val binder = implicitly[PathBindable[UniversityID]]

      binder.bind("client", "1234567") mustBe Right(UniversityID("1234567"))
      binder.unbind("client", UniversityID("1234567")) mustBe "1234567"
    }

    "be bindable in query strings" in {
      val binder = implicitly[QueryStringBindable[UniversityID]]

      binder.bind("client", Map("client" -> Seq("1234567"))) mustBe Some(Right(UniversityID("1234567")))
      binder.unbind("client", UniversityID("1234567")) mustBe "client=1234567"
    }
  }

  "OffsetDateTime" should {
    "be bindable in query strings" in {
      val binder = implicitly[QueryStringBindable[OffsetDateTime]]

      val dtAsString = "2018-10-22T10:00:00.000+01"
      val dt = OffsetDateTime.parse("2018-10-22T10:00:00.000+01", JavaTime.iSO8601DateFormat)

      binder.bind("start", Map("start" -> Seq(dtAsString))) mustBe Some(Right(dt))
      binder.unbind("start", dt) mustBe "start=2018-10-22T10%3A00%3A00.000%2B01"
    }
  }

  "ZoneId" should {
    "be bindable in query strings" in {
      val binder = implicitly[QueryStringBindable[ZoneId]]

      val str = "Europe/London"
      val z = ZoneId.of(str)

      binder.bind("tz", Map("tz" -> Seq(str))) mustBe Some(Right(z))
      binder.unbind("tz", z) mustBe "tz=Europe%2FLondon"
    }
  }

  "Duration" should {
    "be bindable in query strings" in {
      val binder = implicitly[QueryStringBindable[Duration]]

      binder.bind("dur", Map("dur" -> Seq("3600"))) mustBe Some(Right(Duration.ofHours(1)))
      binder.unbind("dur", Duration.ofHours(1)) mustBe "dur=3600"
    }
  }

}
