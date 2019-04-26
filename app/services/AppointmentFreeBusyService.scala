package services

import java.time.LocalDate
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.dao.AppointmentDao.AppointmentSearchQuery
import domain.{AppointmentRender, AppointmentState}
import javax.inject.{Inject, Singleton}
import play.api.cache.AsyncCacheApi
import services.FreeBusyService.{FreeBusyPeriod, FreeBusyStatus}
import warwick.caching.CacheElement
import warwick.core.helpers.JavaTime
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.{TimingContext, TimingService}
import warwick.sso.{UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AppointmentFreeBusyServiceImpl])
trait AppointmentFreeBusyService extends FreeBusyService

@Singleton
class AppointmentFreeBusyServiceImpl @Inject()(
  appointments: AppointmentService,
  cache: AsyncCacheApi,
  timing: TimingService,
)(implicit executionContext: ExecutionContext) extends AppointmentFreeBusyService {

  private def freshResultsFromAppointments(result: ServiceResult[Seq[AppointmentRender]]): CacheElement[ServiceResult[Seq[FreeBusyService.FreeBusyPeriod]]] = {
    val now = JavaTime.instant
    CacheElement(result.right.map { appointments =>
      appointments.filterNot(_.appointment.state == AppointmentState.Cancelled)
        .map { a =>
          FreeBusyPeriod(
            start = a.appointment.start,
            end = a.appointment.end,
            status = a.appointment.state match {
              case AppointmentState.Provisional => FreeBusyStatus.Tentative
              case _ => FreeBusyStatus.Busy
            }
          )
        }
    }, now.getEpochSecond, now.getEpochSecond, now.getEpochSecond)
  }

  override def findFreeBusyPeriods(universityID: UniversityID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyService.FreeBusyPeriod]]]] =
    appointments.findForSearch(AppointmentSearchQuery(
      startAfter = Some(start),
      startBefore = Some(end),
      client = Some(universityID),
    )).map(freshResultsFromAppointments)

  override def findFreeBusyPeriods(usercode: Usercode, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyService.FreeBusyPeriod]]]] =
    appointments.findForSearch(AppointmentSearchQuery(
      startAfter = Some(start),
      startBefore = Some(end),
      teamMember = Some(usercode),
    )).map(freshResultsFromAppointments)

  override def findFreeBusyPeriods(roomID: UUID, start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[CacheElement[ServiceResult[Seq[FreeBusyService.FreeBusyPeriod]]]] =
    appointments.findForSearch(AppointmentSearchQuery(
      startAfter = Some(start),
      startBefore = Some(end),
      roomID = Some(roomID),
    )).map(freshResultsFromAppointments)

}