package domain

import java.time.{OffsetDateTime, ZonedDateTime}
import java.util.UUID

import domain.dao.CaseDao.StoredCaseTagVersion
import org.mockito.Mockito.{RETURNS_SMART_NULLS, _}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import services.{ClientService, NoAuditLogging}
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime
import warwick.sso._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class CaseHistoryTest extends PlaySpec with MockitoSugar with ScalaFutures with NoAuditLogging {

  private val userLookupService = mock[UserLookupService](RETURNS_SMART_NULLS)
  when(userLookupService.getUsers(Seq.empty[Usercode])).thenReturn(Try(Map.empty[Usercode, User]))
  when(userLookupService.getUsers(Seq(Usercode("cusfal")))).thenReturn(Try(Map(Usercode("cusfal") -> Users.create(Usercode("cusfal")))))
  private val clientService = mock[ClientService](RETURNS_SMART_NULLS)
  when(clientService.getOrAddClients(Set.empty[UniversityID])).thenReturn(Future.successful(Right(Seq.empty[Client])))

  "CaseHistory" should {

    "flatten case tags" in {
      val nowInstant = ZonedDateTime.of(2018, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant
      val now = OffsetDateTime.ofInstant(nowInstant, JavaTime.timeZone)
      val caseID = UUID.randomUUID()

      DateTimeUtils.useMockDateTime(nowInstant, () => {
        val history = Seq(
          StoredCaseTagVersion(caseID, CaseTag.Accommodation, now, DatabaseOperation.Insert, now, Some(Usercode("cusfal"))),
          StoredCaseTagVersion(caseID, CaseTag.Alcohol, now.minusMinutes(1), DatabaseOperation.Insert, now.minusMinutes(1), Some(Usercode("cusfal"))),
          StoredCaseTagVersion(caseID, CaseTag.Alcohol, now.minusMinutes(5), DatabaseOperation.Delete, now.minusMinutes(2), Some(Usercode("cusfal"))),
          StoredCaseTagVersion(caseID, CaseTag.Antisocial, now.minusMinutes(3), DatabaseOperation.Insert, now.minusMinutes(3), Some(Usercode("cusfal"))),
          StoredCaseTagVersion(caseID, CaseTag.Bullying, now.minusMinutes(4), DatabaseOperation.Insert, now.minusMinutes(4), Some(Usercode("cusfal"))),
          StoredCaseTagVersion(caseID, CaseTag.Alcohol, now.minusMinutes(5), DatabaseOperation.Insert, now.minusMinutes(5), Some(Usercode("cusfal")))
        )
        val result = CaseHistory.apply(Seq(), history, Seq(), Seq(), userLookupService, clientService).futureValue.right.get
        result.tags.head._1 mustBe Set(CaseTag.Accommodation, CaseTag.Alcohol, CaseTag.Antisocial, CaseTag.Bullying)
        result.tags.head._2 mustBe now
        result.tags(1)._1 mustBe Set(CaseTag.Alcohol, CaseTag.Antisocial, CaseTag.Bullying)
        result.tags(1)._2 mustBe now.minusMinutes(1)
        result.tags(2)._1 mustBe Set(CaseTag.Antisocial, CaseTag.Bullying)
        result.tags(2)._2 mustBe now.minusMinutes(2)
        result.tags(3)._1 mustBe Set(CaseTag.Alcohol, CaseTag.Antisocial, CaseTag.Bullying)
        result.tags(3)._2 mustBe now.minusMinutes(3)
        result.tags(4)._1 mustBe Set(CaseTag.Alcohol, CaseTag.Bullying)
        result.tags(4)._2 mustBe now.minusMinutes(4)
        result.tags(5)._1 mustBe Set(CaseTag.Alcohol)
        result.tags(5)._2 mustBe now.minusMinutes(5)
      })
    }

  }

}
