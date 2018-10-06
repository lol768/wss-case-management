package services

import java.time.{LocalDate, OffsetDateTime}

import com.google.inject.ImplementedBy
import enumeratum.{EnumEntry, PlayEnum}
import helpers.ServiceResults.ServiceResult
import helpers.caching.CacheElement
import javax.inject.{Inject, Singleton}
import services.FreeBusyService.FreeBusyPeriod
import services.tabula.TabulaFreeBusyService
import warwick.core.timing.TimingContext
import warwick.sso.UniversityID

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

object FreeBusyService {
  case class FreeBusyPeriod(
    start: OffsetDateTime,
    end: OffsetDateTime,
    `type`: FreeBusyType
  )

  object FreeBusyPeriod {
    implicit val dateTimeOrdering: Ordering[FreeBusyPeriod] = Ordering.by { fb => (fb.start, fb.end) }

    def combine(allPeriods: Seq[FreeBusyPeriod]): Seq[FreeBusyPeriod] =
      allPeriods.sorted
        .groupBy(_.`type`)
        .mapValues { periods =>
          periods.foldLeft[List[FreeBusyPeriod]](Nil) { case (acc, next) =>
            acc.lastOption match {
              case Some(previous) if !previous.end.isBefore(next.start) =>
                acc.dropRight(1) :+ previous.copy(end = next.end)
              case _ => acc :+ next
            }
          }
        }
        .values.toList.flatten.sorted
  }

  sealed abstract class FreeBusyType(val description: String) extends EnumEntry
  object FreeBusyType extends PlayEnum[FreeBusyType] {
    case object Free extends FreeBusyType("Free")
    case object WorkingElsewhere extends FreeBusyType("Working Elsewhere")
    case object Tentative extends FreeBusyType("Tentative")
    case object Busy extends FreeBusyType("Busy")
    case object OutOfOffice extends FreeBusyType("Out of Office")

    override def values: immutable.IndexedSeq[FreeBusyType] = findValues
  }
}

@ImplementedBy(classOf[FreeBusyServiceImpl])
trait FreeBusyService {
  def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]]
}

@Singleton
class FreeBusyServiceImpl @Inject()(
  tabula: TabulaFreeBusyService,
)(implicit ec: ExecutionContext) extends FreeBusyService {

  override def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyPeriod]]]] =
    tabula.findFreeBusyPeriods(universityID, start, end)
      .map { fb => fb.copy(value = fb.value.right.map(FreeBusyPeriod.combine))}

}