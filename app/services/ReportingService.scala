package services

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain._
import domain.dao.AppointmentDao.{AppointmentSearchQuery, StoredAppointment}
import domain.dao.CaseDao.StoredCase
import domain.dao.EnquiryDao.StoredEnquiry
import domain.dao.{AppointmentDao, CaseDao, DaoRunner, EnquiryDao}
import javax.inject.{Inject, Singleton}
import warwick.core.Logging
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

case class TeamMetrics (team: Team, value: Int)
case class Metric (name: String, teamMetrics: Seq[TeamMetrics])

@ImplementedBy(classOf[ReportingServiceImpl])
trait ReportingService {
  def countOpenedEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countClosedEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  
  def countOpenedCasesFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countClosedCasesFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  
  def countFirstEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  
  def countCasesWithAppointmentsFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  
  def countProvisionalAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countAcceptedAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countAttendedAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  def countCancelledAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int]
  
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
    .map(teamMetric => Metric(name, teamMetric))
    .map(metric =>
      ServiceResults.success(metric)
    ).recover { case e =>
      ServiceResults.error(s"Error collecting metric $name: $e")
    }
    
  def metrics(start: OffsetDateTime, end: OffsetDateTime, teams: Seq[Team])(implicit t: TimingContext): Future[ServiceResult[Seq[Metric]]] =
    ServiceResults.futureSequence(Seq(
      collectMetric("First-time enquirers", teams, start, end, countFirstEnquiries),
      collectMetric("Enquiries opened", teams, start, end, countOpenedEnquiries),
      collectMetric("Enquiries closed", teams, start, end, countClosedEnquiries),
      collectMetric("Cases opened", teams, start, end, countOpenedCasesFromEnquiries),
      collectMetric("Cases closed", teams, start, end, countClosedCasesFromEnquiries),
      collectMetric("Cases with non-cancelled appointments", teams, start, end, countCasesWithAppointmentsFromEnquiries),
      collectMetric("Provisional appointments",teams, start, end, countProvisionalAppointments),
      collectMetric("Accepted appointments", teams, start, end, countAcceptedAppointments),
      collectMetric("Attended appointments", teams, start, end, countAttendedAppointments),
      collectMetric("Cancelled appointments", teams, start, end, countCancelledAppointments)
    ))

  implicit class TeamTransformers[T <: Teamable](fn: => Future[Seq[T]]) {
    def forTeam(team: Option[Team])(implicit t: TimingContext): Future[Int] =
      fn.map(teamableThings => team.map(t => teamableThings.count(_.team == t)).getOrElse(teamableThings.size))

    def toTeamMap(implicit t: TimingContext): Future[Map[Team, Int]] =
      fn.map(teamableThings =>
        teamableThings.groupBy(_.team)
          .map { case (team, things) => team -> things.size }
      )
  }

  private def getOpenedEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredEnquiry]] =
    daoRunner.run {
      Compiled(
        enquiryDao.findByStateQuery(IssueStateFilter.Open)
          .filter(e => e.created >= start && e.created < end)
      ).result
    }

  def countOpenedEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getOpenedEnquiries(start, end).forTeam(team)

  private def getClosedEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredEnquiry]] =
    daoRunner.run {
      Compiled(
        enquiryDao.findByStateQuery(IssueStateFilter.Closed)
          .filter(e => e.version >= start && e.version < end)
      ).result
    }

  def countClosedEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getClosedEnquiries(start, end).forTeam(team)

  private def getOpenedCasesWithEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredCase]] =
    daoRunner.run {
      Compiled(
        caseDao.findCasesWithEnquiriesQuery(IssueStateFilter.Open)
          .filter(c => c.created >= start && c.created < end)
      ).result
    }

  def countOpenedCasesFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getOpenedCasesWithEnquiries(start, end).forTeam(team)

  private def getClosedCasesWithEnquiries(start: OffsetDateTime, end: OffsetDateTime)(implicit t: TimingContext): Future[Seq[StoredCase]] =
    daoRunner.run {
      Compiled(
        caseDao.findCasesWithEnquiriesQuery(IssueStateFilter.Closed)
          .filter(c => c.version >= start && c.version < end)
      ).result
    }

  def countClosedCasesFromEnquiries(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getClosedCasesWithEnquiries(start, end).forTeam(team)

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
    getCasesWithLiveAppointmentsFromEnquiries(start, end).forTeam(team)

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
    getAppointments(start, end, AppointmentState.Provisional).forTeam(team)

  def countAcceptedAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getAppointments(start, end, AppointmentState.Accepted).forTeam(team)

  def countAttendedAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getAppointments(start, end, AppointmentState.Attended).forTeam(team)

  def countCancelledAppointments(start: OffsetDateTime, end: OffsetDateTime, team: Option[Team])(implicit t: TimingContext): Future[Int] =
    getAppointments(start, end, AppointmentState.Cancelled).forTeam(team)
}
