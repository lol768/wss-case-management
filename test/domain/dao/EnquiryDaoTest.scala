package domain.dao

import domain._
import helpers.{MorePatience, OneAppPerSuite}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import warwick.sso.Usercode

class EnquiryDaoTest extends AbstractDaoTest {

  private val dao = get[DaoRunner]

  "EnquiryDao" should {
    "save enquiry objects" in {

    }

  }
}
