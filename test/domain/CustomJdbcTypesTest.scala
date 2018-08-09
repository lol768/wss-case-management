package domain

import java.sql.{PreparedStatement, Timestamp}
import java.time.ZonedDateTime

import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.mockito.Mockito._

class CustomJdbcTypesTest extends PlaySpec with MockitoSugar {

  import CustomJdbcTypes._

  "ZonedDateTime mapper" should {

    /**
      * Note that this is currently a bug under CASE-30.
      * I've put this test in to demonstrate the behaviour and to
      * test that a future fix works.
      */
    "currently maps to local time" in {

      val statement = mock[PreparedStatement]
      val zdt = ZonedDateTime.parse("2018-08-08T12:30:00+01:00[Europe/London]")

      zdt.toInstant.toString mustBe "2018-08-08T11:30:00Z"

      zonedDateTimeTypeMapper.setValue(zdt, statement, 0)

      val t1: Timestamp = Timestamp.valueOf("2018-08-08 12:30:00")

      verify(statement, times(1)).setTimestamp(0, t1)
    }

  }

}
