package services

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import enumeratum.{EnumEntry, PlayEnum}
import helpers.ServiceResults.ServiceResult
import helpers.caching.CacheElement
import javax.inject.{Inject, Singleton}
import services.FreeBusyService.FreeBusyPeriod
import services.office365.Office365FreeBusyService
import services.tabula.TabulaFreeBusyService
import warwick.core.timing.TimingContext
import warwick.sso.{UniversityID, Usercode}

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
        .map {
          // Don't combine frees
          case v @ ((FreeBusyStatus.Free, _) | (FreeBusyStatus.FreeWithCategories(_), _)) => v
          case (status, periods) => status ->
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
    case class FreeWithCategories(categories: Seq[String]) extends FreeBusyStatus(s"Free (${categories.mkString(", ")})") {
      override val entryName: String = "Free"
    }
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
  def findFreeBusyPeriods(usercode: Usercode, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]]
  def findFreeBusyPeriods(roomID: UUID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]]
}

@Singleton
class FreeBusyServiceImpl @Inject()(
  tabula: TabulaFreeBusyService,
  office365: Office365FreeBusyService,
  appointments: AppointmentFreeBusyService,
)(implicit ec: ExecutionContext) extends FreeBusyService {

  private def combineFreeBusyPeriods(c1: CacheElement[ServiceResult[Seq[FreeBusyPeriod]]], c2: CacheElement[ServiceResult[Seq[FreeBusyPeriod]]], c3: CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]): CacheElement[ServiceResult[Seq[FreeBusyPeriod]]] =
    CacheElement(
      value = (c1.value, c2.value, c3.value) match {
        case (Right(r1), Right(r2), Right(r3)) => Right(FreeBusyPeriod.combine(r1 ++ r2 ++ r3))
        case (s1, s2, s3) => Left(List(s1, s2, s3).collect { case Left(x) => x }.flatten)
      },
      created = Math.max(c1.created, Math.max(c2.created, c3.created)),
      softExpiry = Math.min(c1.softExpiry, Math.max(c2.softExpiry, c3.softExpiry)),
      mediumExpiry = Math.min(c1.mediumExpiry, Math.max(c2.mediumExpiry, c3.mediumExpiry))
    )

  override def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] =
    tabula.findFreeBusyPeriods(universityID, start, end)
      .zip(office365.findFreeBusyPeriods(universityID, start, end))
      .zip(appointments.findFreeBusyPeriods(universityID, start, end))
      .map { case ((tab, o365), apps) => combineFreeBusyPeriods(tab, o365, apps) }

  override def findFreeBusyPeriods(usercode: Usercode, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] =
    tabula.findFreeBusyPeriods(usercode, start, end)
      .zip(office365.findFreeBusyPeriods(usercode, start, end))
      .zip(appointments.findFreeBusyPeriods(usercode, start, end))
      .map { case ((tab, o365), apps) => combineFreeBusyPeriods(tab, o365, apps) }

  override def findFreeBusyPeriods(roomID: UUID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] =
    tabula.findFreeBusyPeriods(roomID, start, end)
      .zip(office365.findFreeBusyPeriods(roomID, start, end))
      .zip(appointments.findFreeBusyPeriods(roomID, start, end))
      .map { case ((tab, o365), apps) => combineFreeBusyPeriods(tab, o365, apps) }

}