package services

import java.time.{LocalDate, OffsetDateTime}

import com.google.inject.ImplementedBy
import enumeratum.{EnumEntry, PlayEnum}
import helpers.ServiceResults.ServiceResult
import helpers.caching.CacheElement
import javax.inject.{Inject, Singleton}
import services.FreeBusyService.FreeBusyPeriod
import services.office365.Office365FreeBusyService
import services.tabula.TabulaFreeBusyService
import warwick.core.timing.TimingContext
import warwick.sso.UniversityID

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

object FreeBusyService {
  case class FreeBusyPeriod(
    start: OffsetDateTime,
    end: OffsetDateTime,
    status: FreeBusyStatus
  )

  object FreeBusyPeriod {
    implicit val dateTimeOrdering: Ordering[FreeBusyPeriod] = Ordering.by { fb => (fb.start, fb.end) }

    def combine(allPeriods: Seq[FreeBusyPeriod]): Seq[FreeBusyPeriod] =
      allPeriods.sorted
        .groupBy(_.status)
        .mapValues { periods =>
          periods.foldLeft[List[FreeBusyPeriod]](Nil) { case (acc, next) =>
            acc.lastOption match {
              case Some(previous) if !previous.end.isBefore(next.start) && next.end.isAfter(previous.end) =>
                acc.dropRight(1) :+ previous.copy(end = next.end)
              case Some(previous) if !previous.end.isBefore(next.start) =>
                acc
              case _ => acc :+ next
            }
          }
        }
        .values.toList.flatten.sorted
  }

  sealed abstract class FreeBusyStatus(val description: String) extends EnumEntry
  object FreeBusyStatus extends PlayEnum[FreeBusyStatus] {
    case object Free extends FreeBusyStatus("Free")
    case object WorkingElsewhere extends FreeBusyStatus("Working Elsewhere")
    case object Tentative extends FreeBusyStatus("Tentative")
    case object Busy extends FreeBusyStatus("Busy")
    case object OutOfOffice extends FreeBusyStatus("Out of Office")

    override def values: immutable.IndexedSeq[FreeBusyStatus] = findValues
  }
}

@ImplementedBy(classOf[FreeBusyServiceImpl])
trait FreeBusyService {
  def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]]
}

@Singleton
class FreeBusyServiceImpl @Inject()(
  tabula: TabulaFreeBusyService,
  office365: Office365FreeBusyService,
)(implicit ec: ExecutionContext) extends FreeBusyService {

  override def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] =
    tabula.findFreeBusyPeriods(universityID, start, end)
      .zip(office365.findFreeBusyPeriods(universityID, start, end))
      .map { case (tab, o365) =>
        CacheElement(
          value = (tab.value, o365.value) match {
            case (Right(r1), Right(r2)) => Right(FreeBusyPeriod.combine(r1 ++ r2))
            case (s1, s2) => Left(List(s1, s2).collect { case Left(x) => x }.flatten)
          },
          created = Math.max(tab.created, o365.created),
          softExpiry = Math.min(tab.softExpiry, o365.softExpiry),
          mediumExpiry = Math.min(tab.mediumExpiry, o365.mediumExpiry)
        )
      }

}