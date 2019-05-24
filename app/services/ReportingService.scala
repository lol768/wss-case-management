package services

import java.time.{LocalDate, OffsetDateTime}

import com.google.inject.ImplementedBy
import controllers.DateRange
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.AppointmentDao.{AppointmentSearchQuery, StoredAppointment}
import domain.dao.CaseDao.StoredCase
import domain.dao.EnquiryDao.StoredEnquiry
import domain.dao.{AppointmentDao, CaseDao, DaoRunner, EnquiryDao}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{Writes, Json => PlayJson}
import warwick.core.Logging
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

case class TeamMetrics (team: Team, value: Int)
case class Metric (name: String, description: Option[String], urlPath: Option[String], teamMetrics: Seq[TeamMetrics])
case class DailyMetrics (day: LocalDate, value: Int)
object DailyMetrics {
  implicit val writesDailyMetrics: Writes[DailyMetrics] = PlayJson.writes[DailyMetrics]
}

@ImplementedBy(classOf[ReportingServiceImpl])
trait ReportingService {
  def countFirstEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def firstEnquiriesByDay(start: LocalDate, end: LocalDate, team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]

  def countOpenedEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countClosedEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def openedEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  def closedEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]

  def countOpenedCasesFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countClosedCasesFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def openedCasesFromEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  def closedCasesFromEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]

  def countOpenedCasesWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countClosedCasesWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def openedCasesWithoutEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  def closedCasesWithoutEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  
  def countCasesWithAppointmentsFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countCasesWithAppointmentsWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def casesWithAppointmentsFromEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  def casesWithAppointmentsWithoutEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  
  def countProvisionalAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countAcceptedAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countAttendedAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countCancelledAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def provisionalAppointmentsByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  def acceptedAppointmentsByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  def attendedAppointmentsByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  def cancelledAppointmentsByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]]
  
  def metrics(start: OffsetDateTime, end: OffsetDateTime, teams: Seq[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[Metric]]]
}

@Singleton
class ReportingServiceImpl @Inject() (
  enquiryDao: EnquiryDao,
  caseDao: CaseDao,
  appointmentDao: AppointmentDao,
  daoRunner: DaoRunner,
)(implicit ec: ExecutionContext) extends ReportingService with Logging {

  type ReportGenerator = (OffsetDateTime, OffsetDateTime, Option[Team]) => Future[Int]
  
  private def collectMetric(
    name: String,
    description: String,
    urlPath: String,
    teams: Seq[Team],
    start: OffsetDateTime,
    end: OffsetDateTime,
    reportGenerator: ReportGenerator
  ): Future[ServiceResult[Metric]] =
    Future.sequence(
      teams.map { team =>
        reportGenerator(start, end, Some(team))
          .map(value => TeamMetrics(team, value))
      }
    )
    .map(teamMetric => Metric(name, Some(description), Some(urlPath), teamMetric))
    .map(metric =>
      ServiceResults.success(metric)
    ).recover { case e =>
      ServiceResults.error(s"Error collecting metric $name: $e")
    }
    
  def metrics(start: OffsetDateTime, end: OffsetDateTime, teams: Seq[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[Metric]]] =
    ServiceResults.futureSequence(Seq(
      collectMetric("First-time enquirers", "Unique university IDs making a first recorded enquiry in the period", "/admin/reports/first-enquiries", teams, start, end, countFirstEnquiries),
      collectMetric("Enquiries opened", "Enquiries created in the period (and still open)", "/admin/reports/opened-enquiries", teams, start, end, countOpenedEnquiries),
      collectMetric("Enquiries closed", "Enquiries closed in the period", "/admin/reports/closed-enquiries", teams, start, end, countClosedEnquiries),
      collectMetric("Cases opened from enquiries", "Cases created in the period (and still open), which started as enquiries", "/admin/reports/opened-cases-with-enq", teams, start, end, countOpenedCasesFromEnquiries),
      collectMetric("Cases opened from enquiries, with appointments", "Cases created in the period (and still open), which started as enquiries, and have resulted in an appointment which is either pending or has taken place", "/admin/reports/cases-with-appts-with-enq", teams, start, end, countCasesWithAppointmentsFromEnquiries),
      collectMetric("Cases closed from enquiries", "Cases closed in the period, which started as enquiries", "/admin/reports/closed-cases-with-enq", teams, start, end, countClosedCasesFromEnquiries),
      collectMetric("Cases opened without enquiries", "Cases created in the period (and still open), which did not start as enquiries", "/admin/reports/opened-cases-no-enq", teams, start, end, countOpenedCasesWithoutEnquiries),
      collectMetric("Cases opened without enquiries, with appointments", "Cases created in the period (and still open), which did not start as enquiries, and have resulted in an appointment which is either pending or has taken place", "/admin/reports/cases-with-appts-no-enq", teams, start, end, countCasesWithAppointmentsWithoutEnquiries),
      collectMetric("Cases closed without enquiries", "Cases closed in the period, which did not start as enquiries", "/admin/reports/closed-cases-no-enq", teams, start, end, countClosedCasesWithoutEnquiries),
      collectMetric("Provisional appointments", "Appointments provisionally scheduled to occur during the period", "/admin/reports/provisional-appts", teams, start, end, countProvisionalAppointments),
      collectMetric("Accepted appointments", "Appointments scheduled to occur during the period, which have been accepted", "/admin/reports/accepted-appts", teams, start, end, countAcceptedAppointments),
      collectMetric("Attended appointments", "Appointments attended during the period", "/admin/reports/attended-appts", teams, start, end, countAttendedAppointments),
      collectMetric("Cancelled appointments", "Appointments scheduled to occur during the period, but subsequently cancelled", "/admin/reports/cancelled-appts", teams, start, end, countCancelledAppointments)
    ))

  implicit class TeamableTransformers[T <: Teamable](teamableFuture: Future[Seq[T]]) {
    def forTeam(team: Option[Team])(implicit t: TimingContext): Future[Seq[T]] =
      teamableFuture.map(teamable => team.map(t => teamable.filter(_.team == t)).getOrElse(teamable))

    def countForTeam(team: Option[Team])(implicit t: TimingContext): Future[Int] =
      teamableFuture.forTeam(team).map(_.size)

    def toTeamMap(implicit t: TimingContext): Future[Map[Team, Int]] =
      teamableFuture.map(teamable =>
        teamable.groupBy(_.team)
          .map { case (team, things) => team -> things.size }
      )
  }

  implicit class DailyTransformers[T <: Versioned[T]](timestampedFuture: Future[Seq[T]]) {
    def versionedByDay(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[(LocalDate, Seq[T])]] = {

      val startDay = start.toLocalDate
      val lastDay = end.toLocalDate

      timestampedFuture.map(timestamped => {
        val sorted = timestamped.sortBy(_.version)
        Iterator.iterate(startDay)(_.plusDays(1L))
          .takeWhile(!_.isAfter(lastDay))
          .map { date =>
            date -> sorted.filter(t => t.version.toLocalDate.isEqual(date))
          }
          .toSeq
      })
    }
    
    def countVersionedByDay(start: LocalDate, end: LocalDate)(implicit t: TimingContext): Future[Seq[DailyMetrics]] = {
      timestampedFuture.map(timestamped => {
        val sorted = timestamped.sortBy(_.version)
        Iterator.iterate(start)(_.plusDays(1L))
          .takeWhile(!_.isAfter(end))
          .map { date =>
            DailyMetrics(date, sorted.count(t => t.version.toLocalDate.isEqual(date)))
          }
          .toSeq
      })
    }
  }
  
  implicit class DailyServiceResultTransformers(f: Future[Seq[DailyMetrics]]) {
    def toServiceResult(name: String)(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] =
      f.map(seq => ServiceResults.success(seq)
      ).recover { case e =>
        ServiceResults.error(s"Error building metric $name: $e")
      }
  }


  private def getOpenedEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredEnquiry]] =
    daoRunner.run {
      Compiled(
        enquiryDao.findByStateQuery(IssueStateFilter.Open)
          .filter(e => e.created >= start && e.created < end)
      ).result
    }

  def countOpenedEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getOpenedEnquiries(start, end).countForTeam(team)
  
  def openedEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getOpenedEnquiries(dateRange.startTime, dateRange.endTime)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Opened enquiries by day, $dateRange")
  }


  private def getClosedEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredEnquiry]] =
    daoRunner.run {
      Compiled(
        enquiryDao.findByStateQuery(IssueStateFilter.Closed)
          .filter(e => e.version >= start && e.version < end)
      ).result
    }

  def countClosedEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getClosedEnquiries(start, end).countForTeam(team)

  def closedEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getClosedEnquiries(dateRange.startTime, dateRange.endTime)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Closed enquiries by day, $dateRange")
  }


  private def getOpenedCasesWithEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredCase]] =
    daoRunner.run {
      Compiled(
        caseDao.findCasesWithEnquiriesQuery(IssueStateFilter.Open)
          .filter(c => c.created >= start && c.created < end)
      ).result
    }

  def countOpenedCasesFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getOpenedCasesWithEnquiries(start, end).countForTeam(team)

  def openedCasesFromEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getOpenedCasesWithEnquiries(dateRange.startTime, dateRange.endTime)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Opened cases from enquiries by day, $dateRange")
  }


  private def getClosedCasesWithEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredCase]] =
    daoRunner.run {
      Compiled(
        caseDao.findCasesWithEnquiriesQuery(IssueStateFilter.Closed)
          .filter(c => c.version >= start && c.version < end)
      ).result
    }

  def countClosedCasesFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getClosedCasesWithEnquiries(start, end).countForTeam(team)

  def closedCasesFromEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getClosedCasesWithEnquiries(dateRange.startTime, dateRange.endTime)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Closed cases from enquiries by day, $dateRange")
  }


  private def getOpenedCasesWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredCase]] =
    daoRunner.run {
      Compiled(
        caseDao.findCasesWithoutEnquiriesQuery(IssueStateFilter.Open)
          .filter(c => c.created >= start && c.created < end)
      ).result
    }

  def countOpenedCasesWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getOpenedCasesWithoutEnquiries(start, end).countForTeam(team)

  def openedCasesWithoutEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getOpenedCasesWithoutEnquiries(dateRange.startTime, dateRange.endTime)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Opened cases without enquiries by day, $dateRange")
  }

  
  private def getClosedCasesWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredCase]] =
    daoRunner.run {
      Compiled(
        caseDao.findCasesWithoutEnquiriesQuery(IssueStateFilter.Closed)
          .filter(c => c.version >= start && c.version < end)
      ).result
    }

  def countClosedCasesWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getClosedCasesWithoutEnquiries(start, end).countForTeam(team)

  def closedCasesWithoutEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getClosedCasesWithoutEnquiries(dateRange.startTime, dateRange.endTime)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Closed cases without enquiries by day, $dateRange")
  }

  
  private def firstEnquiriesByTeam(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[(Team, Int)]] =
    daoRunner.run {
      Compiled(
        enquiryDao.countFirstEnquiriesByTeam(start, end)
      ).result
    }

  def countFirstEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    firstEnquiriesByTeam(start, end)
      .map(enqsByTeam =>
        team match {
          case Some(searchedTeam) =>
            enqsByTeam
              .find { case (matchingTeam, _) => matchingTeam == searchedTeam }
              .map { case (_, count) => count }
              .getOrElse(0)
          case None =>
            enqsByTeam
              .map { case (_, count) => count }
              .sum
        }
      )

  def firstEnquiriesByDay(start: LocalDate, end: LocalDate, team: Team)(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    daoRunner.run {
        enquiryDao.getFirstEnquiries(dateRange.startTime, dateRange.endTime, team)
    }
      .countVersionedByDay(start, end)
      .toServiceResult(s"First enquiries by day, $dateRange")
  }

  
  private def getCasesWithLiveAppointmentsFromEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredCase]] = {
    val casesWithNonCancelledApptsQuery = appointmentDao.findNotCancelledQuery.withCases.map(_._2).map(_.id)

    daoRunner.run {
      Compiled(
        caseDao.findCasesWithEnquiriesQuery(IssueStateFilter.All)
          .filter(c => c.created >= start && c.created < end)
          .filter(_.id in casesWithNonCancelledApptsQuery)
      ).result
    }
  }

  def countCasesWithAppointmentsFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getCasesWithLiveAppointmentsFromEnquiries(start, end).countForTeam(team)

  def casesWithAppointmentsFromEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getCasesWithLiveAppointmentsFromEnquiries(dateRange.startTime, dateRange.endTime)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Cases with appointments from enquiries by day, $dateRange")
  }
  
  
  private def getCasesWithLiveAppointmentsWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredCase]] = {
    val casesWithNonCancelledApptsQuery = appointmentDao.findNotCancelledQuery.withCases.map(_._2).map(_.id)

    daoRunner.run {
      Compiled(
        caseDao.findCasesWithoutEnquiriesQuery(IssueStateFilter.All)
          .filter(c => c.created >= start && c.created < end)
          .filter(_.id in casesWithNonCancelledApptsQuery)
      ).result
    }
  }

  def countCasesWithAppointmentsWithoutEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getCasesWithLiveAppointmentsWithoutEnquiries(start, end).countForTeam(team)

  def casesWithAppointmentsWithoutEnquiriesByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getCasesWithLiveAppointmentsWithoutEnquiries(dateRange.startTime, dateRange.endTime)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Cases with appointments without enquiries by day, $dateRange")
  }


  private def getAppointments(start: OffsetDateTime, end: OffsetDateTime, state: AppointmentState)(implicit t: TimingContext): Future[Seq[StoredAppointment]] =
    daoRunner.run {
      Compiled(
        appointmentDao.searchQuery(
          AppointmentSearchQuery(
            startAfter = Some(start.toLocalDate),
            startBefore = Some(end.plusDays(1L).toLocalDate),
            states = Set(state),
          )
        )
      ).result
    }

  def countProvisionalAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getAppointments(start, end, AppointmentState.Provisional).countForTeam(team)

  def countAcceptedAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getAppointments(start, end, AppointmentState.Accepted).countForTeam(team)

  def countAttendedAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getAppointments(start, end, AppointmentState.Attended).countForTeam(team)

  def countCancelledAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getAppointments(start, end, AppointmentState.Cancelled).countForTeam(team)
  
  def provisionalAppointmentsByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getAppointments(dateRange.startTime, dateRange.endTime, AppointmentState.Provisional)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Closed enquiries by day, $dateRange")
  }

  def acceptedAppointmentsByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getAppointments(dateRange.startTime, dateRange.endTime, AppointmentState.Accepted)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Closed enquiries by day, $dateRange")
  }

  def attendedAppointmentsByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getAppointments(dateRange.startTime, dateRange.endTime, AppointmentState.Attended)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Closed enquiries by day, $dateRange")
  }

  def cancelledAppointmentsByDay(start: LocalDate, end: LocalDate, team: Option[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[DailyMetrics]]] = {
    val dateRange = DateRange(start, end)
    getAppointments(dateRange.startTime, dateRange.endTime, AppointmentState.Cancelled)
      .forTeam(team)
      .countVersionedByDay(start, end)
      .toServiceResult(s"Closed enquiries by day, $dateRange")
  }
}
