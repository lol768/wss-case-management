package controllers.sysadmin

import java.nio.charset.StandardCharsets
import java.time.{Clock, DayOfWeek, OffsetDateTime}

import com.google.common.io.{ByteSource, CharSource}
import controllers.BaseController
import controllers.admin.CaseController.CaseIncidentFormData
import controllers.sysadmin.DataGenerationController._
import domain._
import domain.dao.CaseDao.Case
import enumeratum.{Enum, EnumEntry}
import helpers.ServiceResults.ServiceResult
import javax.inject.{Inject, Singleton}
import org.quartz._
import play.api.Configuration
import play.api.i18n.Messages
import play.api.mvc.{Action, ActionFilter, AnyContent, Result}
import services._
import uk.ac.warwick.util.core.DateTimeUtils
import uk.ac.warwick.util.workingdays.{WorkingDaysHelper, WorkingDaysHelperImpl}
import warwick.core.Logging
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso.{AuthenticatedRequest, UniversityID, Usercode}

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try, Random => SRandom}

object DataGenerationController {
  case class DataGenerationOptions(
    EnquiriesToGenerate: Int = 1000,
    AverageMessagesPerEnquiry: Int = 3,
    MessageAttachmentRate: Double = 0.1, // Proportion of messages that will have an attachment (may be multiple)
    EnquiryCounsellingRate: Double = 0.25, // Proportion of enquiries that will be reassigned to counselling
    EnquiryDisabilityRate: Double = 0.25, // Proportion of enquiries that will be reassigned to disability
    EnquiryMentalHealthRate: Double = 0.25, // Proportion of enquiries that will be reassigned to mental health
    EnquiryToCaseRate: Double = 0.7, // Proportion of enquiries that will become cases
    HighMentalHealthRiskSetRate: Double = 0.1, // Proportion of clients with a high mental health risk set
    HighMentalHealthRiskRate: Double = 0.3, // Proportion of clients with a high mental health risk (of the above)
    ReasonableAdjustmentRate: Double = 0.1, // Proportion of clients with reasonable adjustments
    DisabilityRate: Double = 0.1, // Proportion of clients who declare a disability
    MedicationRate: Double = 0.2, // Proportion of clients who declare medications
    ReferralRate: Double = 0.25, // Proportion of clients who have referral(s)
    CasesToGenerate: Int = 2000, // Will take into account that a proportion of the above enquiries will become cases
    CaseTagRate: Double = 0.8, // Number of cases with tags
    CaseReassignRate: Double = 0.1, // Proportion of cases that will be reassigned to another team
    CaseIncidentRate: Double = 0.2, // Proportion of cases related to incidents
    MultiClientCaseRate: Double = 0.05, // Proportion of cases that will have multiple clients
    CaseLinkRate: Double = 0.1, // Proportion of cases linked to another case
    AverageCaseDocumentsPerCase: Int = 3, // Average number of documents per case
    AverageMessagesPerCasePerClient: Int = 6, // Average number of messages per case, per client
    AverageNotesPerCase: Int = 10, // Average number of case notes per case
    RegistrationRate: Double = 0.9, // Proportion of clients who will successfully register after having a case
    AverageAppointmentsPerCase: Int = 4, // How many times, on average, people will have an appointment
    AppointmentsToGenerate: Int = 10000, // Will take into account that a lot of appointments will come from a case
    MultipleCaseAppointmentRate: Double = 0.05, // What proportion of appointments are linked to multiple cases
    MultipleClientAppointmentRate: Double = 0.05, // What proportion of appointments are linked to multiple clients
    MultipleTeamMemberAppointmentRate: Double = 0.05, // What proportion of appointments are linked to multiple team members
    AppointmentAcceptanceRate: Double = 0.7, // Proportion of appointments that are accepted by a client
    AppointmentDeclineRate: Double = 0.3, // Proportion of appointments that are declined by a client
    AppointmentRescheduleRate: Double = 0.3, // Proportion of appointments that have to be rescheduled (maybe multiple times)
    AppointmentCancelledRate: Double = 0.05, // Proportion of appointments that are cancelled before they happen
    AppointmentAttendedRate: Double = 0.98, // Proportion of appointments where at least one client attends (calculated per client)
  ) {
    def toJobDataMap: JobDataMap = {
      new JobDataMap(Map[String, Any](
        "EnquiriesToGenerate" -> EnquiriesToGenerate,
        "AverageMessagesPerEnquiry" -> AverageMessagesPerEnquiry,
        "MessageAttachmentRate" -> MessageAttachmentRate,
        "EnquiryCounsellingRate" -> EnquiryCounsellingRate,
        "EnquiryDisabilityRate" -> EnquiryDisabilityRate,
        "EnquiryMentalHealthRate" -> EnquiryMentalHealthRate,
        "EnquiryToCaseRate" -> EnquiryToCaseRate,
        "HighMentalHealthRiskSetRate" -> HighMentalHealthRiskSetRate,
        "HighMentalHealthRiskRate" -> HighMentalHealthRiskRate,
        "ReasonableAdjustmentRate" -> ReasonableAdjustmentRate,
        "DisabilityRate" -> DisabilityRate,
        "MedicationRate" -> MedicationRate,
        "ReferralRate" -> ReferralRate,
        "CasesToGenerate" -> CasesToGenerate,
        "CaseTagRate" -> CaseTagRate,
        "CaseReassignRate" -> CaseReassignRate,
        "CaseIncidentRate" -> CaseIncidentRate,
        "MultiClientCaseRate" -> MultiClientCaseRate,
        "CaseLinkRate" -> CaseLinkRate,
        "AverageCaseDocumentsPerCase" -> AverageCaseDocumentsPerCase,
        "AverageMessagesPerCasePerClient" -> AverageMessagesPerCasePerClient,
        "AverageNotesPerCase" -> AverageNotesPerCase,
        "RegistrationRate" -> RegistrationRate,
        "AverageAppointmentsPerCase" -> AverageAppointmentsPerCase,
        "AppointmentsToGenerate" -> AppointmentsToGenerate,
        "MultipleCaseAppointmentRate" -> MultipleCaseAppointmentRate,
        "MultipleClientAppointmentRate" -> MultipleClientAppointmentRate,
        "MultipleTeamMemberAppointmentRate" -> MultipleTeamMemberAppointmentRate,
        "AppointmentAcceptanceRate" -> AppointmentAcceptanceRate,
        "AppointmentDeclineRate" -> AppointmentDeclineRate,
        "AppointmentRescheduleRate" -> AppointmentRescheduleRate,
        "AppointmentCancelledRate" -> AppointmentCancelledRate,
        "AppointmentAttendedRate" -> AppointmentAttendedRate,
      ).asJava)
    }
  }

  object DataGenerationOptions {
    def apply(data: JobDataMap): DataGenerationOptions = {
      val defaults = DataGenerationOptions()

      def getIntOrDefault(key: String, default: => Int): Int =
        if (data.containsKey(key)) data.getIntValue(key)
        else default

      def getDoubleOrDefault(key: String, default: => Double): Double =
        if (data.containsKey(key)) data.getDoubleValue(key)
        else default

      DataGenerationOptions(
        EnquiriesToGenerate = getIntOrDefault("EnquiriesToGenerate", defaults.EnquiriesToGenerate),
        AverageMessagesPerEnquiry = getIntOrDefault("AverageMessagesPerEnquiry", defaults.AverageMessagesPerEnquiry),
        MessageAttachmentRate = getDoubleOrDefault("MessageAttachmentRate", defaults.MessageAttachmentRate),
        EnquiryCounsellingRate = getDoubleOrDefault("EnquiryCounsellingRate", defaults.EnquiryCounsellingRate),
        EnquiryDisabilityRate = getDoubleOrDefault("EnquiryDisabilityRate", defaults.EnquiryDisabilityRate),
        EnquiryMentalHealthRate = getDoubleOrDefault("EnquiryMentalHealthRate", defaults.EnquiryMentalHealthRate),
        EnquiryToCaseRate = getDoubleOrDefault("EnquiryToCaseRate", defaults.EnquiryToCaseRate),
        HighMentalHealthRiskSetRate = getDoubleOrDefault("HighMentalHealthRiskSetRate", defaults.HighMentalHealthRiskSetRate),
        HighMentalHealthRiskRate = getDoubleOrDefault("HighMentalHealthRiskRate", defaults.HighMentalHealthRiskRate),
        ReasonableAdjustmentRate = getDoubleOrDefault("ReasonableAdjustmentRate", defaults.ReasonableAdjustmentRate),
        DisabilityRate = getDoubleOrDefault("DisabilityRate", defaults.DisabilityRate),
        MedicationRate = getDoubleOrDefault("MedicationRate", defaults.MedicationRate),
        ReferralRate = getDoubleOrDefault("ReferralRate", defaults.ReferralRate),
        CasesToGenerate = getIntOrDefault("CasesToGenerate", defaults.CasesToGenerate),
        CaseTagRate = getDoubleOrDefault("CaseTagRate", defaults.CaseTagRate),
        CaseReassignRate = getDoubleOrDefault("CaseReassignRate", defaults.CaseReassignRate),
        CaseIncidentRate = getDoubleOrDefault("CaseIncidentRate", defaults.CaseIncidentRate),
        MultiClientCaseRate = getDoubleOrDefault("MultiClientCaseRate", defaults.MultiClientCaseRate),
        CaseLinkRate = getDoubleOrDefault("CaseLinkRate", defaults.CaseLinkRate),
        AverageCaseDocumentsPerCase = getIntOrDefault("AverageCaseDocumentsPerCase", defaults.AverageCaseDocumentsPerCase),
        AverageMessagesPerCasePerClient = getIntOrDefault("AverageMessagesPerCasePerClient", defaults.AverageMessagesPerCasePerClient),
        AverageNotesPerCase = getIntOrDefault("AverageNotesPerCase", defaults.AverageNotesPerCase),
        RegistrationRate = getDoubleOrDefault("RegistrationRate", defaults.RegistrationRate),
        AverageAppointmentsPerCase = getIntOrDefault("AverageAppointmentsPerCase", defaults.AverageAppointmentsPerCase),
        AppointmentsToGenerate = getIntOrDefault("AppointmentsToGenerate", defaults.AppointmentsToGenerate),
        MultipleCaseAppointmentRate = getDoubleOrDefault("MultipleCaseAppointmentRate", defaults.MultipleCaseAppointmentRate),
        MultipleClientAppointmentRate = getDoubleOrDefault("MultipleClientAppointmentRate", defaults.MultipleClientAppointmentRate),
        MultipleTeamMemberAppointmentRate = getDoubleOrDefault("MultipleTeamMemberAppointmentRate", defaults.MultipleTeamMemberAppointmentRate),
        AppointmentAcceptanceRate = getDoubleOrDefault("AppointmentAcceptanceRate", defaults.AppointmentAcceptanceRate),
        AppointmentDeclineRate = getDoubleOrDefault("AppointmentDeclineRate", defaults.AppointmentDeclineRate),
        AppointmentRescheduleRate = getDoubleOrDefault("AppointmentRescheduleRate", defaults.AppointmentRescheduleRate),
        AppointmentCancelledRate = getDoubleOrDefault("AppointmentCancelledRate", defaults.AppointmentCancelledRate),
        AppointmentAttendedRate = getDoubleOrDefault("AppointmentAttendedRate", defaults.AppointmentAttendedRate),
      )
    }
  }

  val DummyText: String =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut " +
    "labore et dolore magna aliqua. Lut enim ad minim veniam, quis nostrud exercitation ullamco laboris " +
    "nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit " +
    "esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt " +
    "in culpa qui officiae deserunt mollit anim id est laborum."

  def dummyWords(words: Int): String = {
    val paragraphCount = words / DummyText.split(' ').length
    val wordCount = words % DummyText.split(' ').length

    val wordsPara = DummyText.split(' ').slice(0, wordCount).mkString(" ")

    val paras = dummyParagraphs(paragraphCount)

    s"$paras\n\n$wordsPara"
  }

  def dummyParagraphs(paragraphs: Int): String =
    (1 to paragraphs).map(_ => DummyText).mkString("\n\n")

  val Random = new SRandom

  val MaximumDaysInPast = 365 // How many days in the past events may have occurred
  val MaximumDaysInFuture = 30 // How many days in the future events may occur (only scheduled appointments)

  private[this] val workingDays: WorkingDaysHelper = new WorkingDaysHelperImpl

  def randomPastDateTime(maximumDaysInPast: Int = MaximumDaysInPast, base: OffsetDateTime = JavaTime.offsetDateTime, workingHoursOnly: Boolean = false): OffsetDateTime = {
    var dt =
      base.minusDays(Random.nextInt(maximumDaysInPast + 1).toLong)
        .withMinute(Random.nextInt(60))
        .withSecond(Random.nextInt(60))

    if (workingHoursOnly) {
      // Shift to a workday if it's not one already
      while (dt.getDayOfWeek == DayOfWeek.SATURDAY || dt.getDayOfWeek == DayOfWeek.SUNDAY || workingDays.getHolidayDates.contains(dt.toLocalDate) || dt.isAfter(base)) {
        dt = dt.plusDays(Random.nextInt(7).toLong)
        if (dt.isAfter(base))
          dt = dt.minusDays(Random.nextInt(maximumDaysInPast).toLong)
      }

      dt.withHour(9 + Random.nextInt(8))
    } else {
      dt.withHour(Random.nextInt(24))
    }
  }

  def randomFutureDateTime(maximumDaysInFuture: Int = MaximumDaysInFuture, base: OffsetDateTime = JavaTime.offsetDateTime, workingHoursOnly: Boolean = false): OffsetDateTime = {
    var dt =
      base.plusDays(Random.nextInt(maximumDaysInFuture + 1).toLong)
        .withMinute(Random.nextInt(60))
        .withSecond(Random.nextInt(60))

    if (workingHoursOnly) {
      // Shift to a workday if it's not one already
      while (dt.getDayOfWeek == DayOfWeek.SATURDAY || dt.getDayOfWeek == DayOfWeek.SUNDAY || workingDays.getHolidayDates.contains(dt.toLocalDate) || dt.isBefore(base)) {
        dt = dt.plusDays(Random.nextInt(7).toLong)
        if (dt.isAfter(base.plusDays(maximumDaysInFuture.toLong)))
          dt = dt.minusDays(Random.nextInt(maximumDaysInFuture).toLong)
      }

      dt.withHour(9 + Random.nextInt(8))
    } else {
      dt.withHour(Random.nextInt(24))
    }
  }

  private[this] val UserAgents = Seq(
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/601.7.7 (KHTML, like Gecko) Version/9.1.2 Safari/601.7.7",
    "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/48.0.2564.109 Safari/537.36",
    "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/11.1.2 Safari/605.1.15",
    "Mozilla/5.0 (Windows NT 10.0; WOW64; rv:56.0) Gecko/20100101 Firefox/56.0",
    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36 OPR/43.0.2442.991"
  )

  def auditLogContext(usercode: Usercode) = AuditLogContext(
    usercode = Some(usercode),
    ipAddress = Some(s"${Random.nextInt(254) + 1}.${Random.nextInt(254) + 1}.${Random.nextInt(254) + 1}.${Random.nextInt(254) + 1}"),
    userAgent = Some(UserAgents(Random.nextInt(UserAgents.size))),
    timingData = new TimingContext.Data
  )

  def randomAttachment(): (ByteSource, UploadedFileSave) = {
    val in = CharSource.wrap(dummyWords(Random.nextInt(1000))).asByteSource(StandardCharsets.UTF_8)
    val file = UploadedFileSave(s"${Random.alphanumeric.take(Random.nextInt(10) + 3).mkString}.txt", in.size(), "text/plain")

    (in, file)
  }

  def randomAttachments(MessageAttachmentRate: Double): Seq[(ByteSource, UploadedFileSave)] =
    (1 to (MessageAttachmentRate / Random.nextDouble()).toInt).map { _ => randomAttachment() }

  def randomTeam(): Team = Teams.all(Random.nextInt(Teams.all.size))

  def randomEnum[A <: EnumEntry](enum: Enum[A]): A = enum.values(Random.nextInt(enum.values.size))
}

@Singleton
class DataGenerationController @Inject()(
  scheduler: Scheduler,
  securityService: SecurityService,
  configuration: Configuration,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import securityService._

  private[this] val enabled = configuration.get[Boolean]("wellbeing.dummyDataGeneration")

  private[this] val EnabledGuard = new ActionFilter[AuthenticatedRequest] {
    override protected def filter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] =
      Future.successful {
        if (enabled) None
        else Some(BadRequest("wellbeing.dummyDataGeneration = false"))
      }
    override protected def executionContext: ExecutionContext = DataGenerationController.this.executionContext
  }

  def generateForm(): Action[AnyContent] = RequireSysadmin.andThen(EnabledGuard) { implicit request =>
    // TODO Allow customising DataGenerationOptions
    Ok(views.html.sysadmin.generateData())
  }

  def generate(): Action[AnyContent] = RequireSysadmin.andThen(EnabledGuard) { implicit request =>
    if (scheduler.checkExists(new JobKey("DataGenerationJob", null))) {
      Redirect(controllers.sysadmin.routes.DataGenerationController.generateForm())
        .flashing("error" -> Messages("flash.datageneration.alreadyRunning"))
    } else {
      scheduler.scheduleJob(
        JobBuilder.newJob(classOf[DataGenerationJob])
          .withIdentity("DataGenerationJob")
          .usingJobData(DataGenerationOptions().toJobDataMap)
          .build(),
        TriggerBuilder.newTrigger()
          .startNow()
          .build()
      )
      Redirect(controllers.sysadmin.routes.DataGenerationController.generateForm())
        .flashing("success" -> Messages("flash.datageneration.scheduled"))
    }
  }

}

@PersistJobDataAfterExecution
@DisallowConcurrentExecution
class DataGenerationJob @Inject()(
  enquiries: EnquiryService,
  clientSummaries: ClientSummaryService,
  registrations: RegistrationService,
  cases: CaseService,
  appointments: AppointmentService,
  locations: LocationService,
  configuration: Configuration,
)(implicit executionContext: ExecutionContext) extends Job with Logging {

  private[this] val allClients: Seq[UniversityID] =
    configuration.get[Seq[String]]("wellbeing.tabula.testUsers")
      .map(UniversityID.apply)

  private[this] val allTeamMembers: Map[Team, Seq[Usercode]] =
    configuration.get[Map[String, Seq[String]]]("wellbeing.testTeamMembers")
      .map { case (teamId, usercodes) => Teams.fromId(teamId) -> usercodes.map(Usercode.apply) }

  private[this] val allAdmins: Seq[Usercode] =
    configuration.get[Seq[String]]("wellbeing.testAdmins")
      .map(Usercode.apply)

  private[this] val initialTeam: Team = Teams.fromId(configuration.get[String]("app.enquiries.initialTeamId"))

  private[this] implicit class FutureServiceResultOps[A](f: Future[ServiceResult[A]]) {
    // Convenient way to block on a Future[ServiceResult[_]] that you expect
    // to be successful.
    def serviceValue: A =
      Await.result(f, Duration.Inf).fold(
        e => {
          val msg = e.head.message
          e.headOption.flatMap(_.cause).fold(logger.error(msg))(t => logger.error(msg, t))

          e.flatMap(_.cause).headOption
            .map(throw _)
            .getOrElse(throw new IllegalStateException(e.map(_.message).mkString("; ")))
        },
        identity // return success as-is
      )
  }

  private[this] def withMockDateTime[A](dt: OffsetDateTime)(fn: OffsetDateTime => A): A =
    try {
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(dt.toInstant, JavaTime.timeZone)
      fn(dt)
    } finally {
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone()
    }

  private[this] def randomTeamMember(team: Team): Usercode = {
    val teamMembers = allTeamMembers(team)
    teamMembers(Random.nextInt(teamMembers.length))
  }

  private[this] def randomClient(): UniversityID = allClients(Random.nextInt(allClients.size))

  private[this] val enabled = configuration.get[Boolean]("wellbeing.dummyDataGeneration")

  override def execute(context: JobExecutionContext): Unit = {
    if (!enabled)
      throw new JobExecutionException("Tried to run DataGenerationJob but wellbeing.dummyDataGeneration = false")

    Try {
      val options = DataGenerationOptions(context.getJobDetail.getJobDataMap)
      val allRooms = locations.availableRooms(auditLogContext(Usercode("system"))).serviceValue

      // Generate enquiries
      var generatedEnquiries: Seq[Enquiry] = (1 to options.EnquiriesToGenerate).map { _ =>
        withMockDateTime(randomPastDateTime()) { _ =>
          val client = randomClient()
          implicit val ac: AuditLogContext = auditLogContext(Usercode(s"u${client.string}"))

          val enquiry = EnquirySave(client, dummyWords(Random.nextInt(5) + 3), initialTeam, IssueState.Open)
          val initialMessage = MessageSave(dummyWords(Random.nextInt(200)), MessageSender.Client, None)

          enquiries.save(enquiry, initialMessage, randomAttachments(options.MessageAttachmentRate)).serviceValue
        }
      }

      // Re-assign some enquiries
      generatedEnquiries = generatedEnquiries.map { enquiry =>
        withMockDateTime(randomFutureDateTime(base = enquiry.created)) { _ =>
          val teamMember = randomTeamMember(initialTeam)
          implicit val ac: AuditLogContext = auditLogContext(teamMember)

          val note = EnquiryNoteSave(dummyWords(Random.nextInt(50)), teamMember)

          if ((options.EnquiryCounsellingRate / Random.nextDouble()).toInt > 0) {
            enquiries.reassign(enquiry.id.get, Teams.Counselling, note, enquiry.lastUpdated).serviceValue
          } else if ((options.EnquiryDisabilityRate / Random.nextDouble()).toInt > 0) {
            enquiries.reassign(enquiry.id.get, Teams.Disability, note, enquiry.lastUpdated).serviceValue
          } else if ((options.EnquiryMentalHealthRate / Random.nextDouble()).toInt > 0) {
            enquiries.reassign(enquiry.id.get, Teams.MentalHealth, note, enquiry.lastUpdated).serviceValue
          } else {
            enquiry
          }
        }
      }

      // Set owners on enquiries
      generatedEnquiries.map { enquiry =>
        withMockDateTime(randomFutureDateTime(base = enquiry.lastUpdated)) { _ =>
          implicit val ac: AuditLogContext = auditLogContext(randomTeamMember(enquiry.team))
          val owners: Set[Usercode] = Random.nextInt(4) match {
            case 0 => Set()
            case 1 => Set(randomTeamMember(enquiry.team))
            case 2 => Set(randomTeamMember(enquiry.team), randomTeamMember(enquiry.team))
            case 3 => Set(randomTeamMember(enquiry.team), randomTeamMember(enquiry.team), randomTeamMember(initialTeam))
          }

          if (owners.nonEmpty) {
            enquiries.setOwners(enquiry.id.get, owners).serviceValue
          }
        }
      }

      // Add messages to enquiries
      val enquiryMessages: Seq[(Enquiry, Seq[(MessageData, Seq[UploadedFile])])] = generatedEnquiries.map { enquiry =>
        enquiry -> (1 to Random.nextInt(options.AverageMessagesPerEnquiry * 2)).map { i =>
          withMockDateTime(randomFutureDateTime(base = enquiry.created, maximumDaysInFuture = i)) { _ =>
            val isClient = i % 2 == 0

            val message =
              if (isClient) MessageSave(dummyWords(Random.nextInt(200)), MessageSender.Client, None)
              else MessageSave(dummyWords(Random.nextInt(200)), MessageSender.Team, Some(randomTeamMember(enquiry.team)))

            implicit val ac: AuditLogContext =
              if (isClient) auditLogContext(Usercode(s"u${enquiry.client.universityID.string}"))
              else auditLogContext(message.teamMember.get)

            enquiries.addMessage(enquiry, message, randomAttachments(options.MessageAttachmentRate)).serviceValue
          }
        }
      }

      // Generate client summaries
      val generatedClientSummaries: Seq[ClientSummary] = allClients.map { client =>
        implicit val ac: AuditLogContext = auditLogContext(randomTeamMember(randomTeam()))
        withMockDateTime(randomPastDateTime()) { _ =>
          val summary = ClientSummarySave(
            highMentalHealthRisk =
              if ((options.HighMentalHealthRiskSetRate / Random.nextDouble()).toInt > 0) {
                if ((options.HighMentalHealthRiskRate / Random.nextDouble()).toInt > 0) {
                  Some(true)
                } else {
                  Some(false)
                }
              } else {
                None
              },
            notes = dummyWords(Random.nextInt(30)),
            alternativeContactNumber = f"07${Random.nextInt(999999999)}%09d",
            alternativeEmailAddress = s"${Random.alphanumeric.take(Random.nextInt(10) + 10).mkString("")}@gmail.com",
            riskStatus =
              if ((options.HighMentalHealthRiskSetRate / Random.nextDouble()).toInt > 0) {
                Some(randomEnum(ClientRiskStatus))
              } else {
                None
              },
            reasonableAdjustments =
              (1 to (options.ReasonableAdjustmentRate / Random.nextDouble()).toInt).map { _ =>
                randomEnum(ReasonableAdjustment)
              }.toSet
          )

          clientSummaries.get(client).serviceValue match {
            case Some(existing) =>
              clientSummaries.update(client, summary, existing.updatedDate).serviceValue

            case _ =>
              clientSummaries.save(client, summary).serviceValue
          }
        }
      }

      // Generate registrations
      val generatedRegistration: Seq[Option[Registration]] = allClients.map { client =>
        if ((options.RegistrationRate / Random.nextDouble()).toInt > 0) Some {
          implicit val ac: AuditLogContext = auditLogContext(Usercode(s"u${client.string}"))
          withMockDateTime(randomPastDateTime()) { _ =>
            val registration = RegistrationData(
              gp = s"Dr. ${dummyWords(Random.nextInt(2))}",
              tutor = s"Prof. ${dummyWords(Random.nextInt(2))}",
              disabilities =
                (1 to (options.DisabilityRate / Random.nextDouble()).toInt).map { _ =>
                  randomEnum(Disabilities)
                }.toSet,
              medications =
                (1 to (options.MedicationRate / Random.nextDouble()).toInt).map { _ =>
                  randomEnum(Medications)
                }.toSet,
              appointmentAdjustments = dummyWords(Random.nextInt(10)),
              referrals =
                (1 to (options.ReferralRate / Random.nextDouble()).toInt).map { _ =>
                  randomEnum(RegistrationReferrals)
                }.toSet,
              consentPrivacyStatement = Some(true)
            )

            registrations.get(client).serviceValue match {
              case Some(existing) =>
                registrations.update(client, registration, existing.updatedDate).serviceValue

              case _ =>
                registrations.save(client, registration).serviceValue
            }
          }
        } else None
      }

      // Generate cases
      val enquiryCases: Seq[(Case, Set[UniversityID])] = generatedEnquiries.flatMap { enquiry =>
        if ((options.EnquiryToCaseRate / Random.nextDouble()).toInt > 0) Some {
          withMockDateTime(randomFutureDateTime(base = enquiry.lastUpdated)) { _ =>
            implicit val ac: AuditLogContext = auditLogContext(randomTeamMember(enquiry.team))

            val incident: Option[CaseIncidentFormData] =
              if ((options.CaseIncidentRate / Random.nextDouble()).toInt > 0)
                Some(CaseIncidentFormData(
                  incidentDate = randomPastDateTime(maximumDaysInPast = 7),
                  onCampus = Random.nextBoolean(),
                  notifiedPolice = Random.nextBoolean(),
                  notifiedAmbulance = Random.nextBoolean(),
                  notifiedFire = Random.nextBoolean(),
                ))
              else None

            val c = cases.create(
              Case(
                id = None, // Set by service
                key = None, // Set by service
                subject = dummyWords(Random.nextInt(5) + 3),
                created = JavaTime.offsetDateTime,
                team = enquiry.team,
                version = JavaTime.offsetDateTime,
                state = IssueState.Open,
                incidentDate = incident.map(_.incidentDate),
                onCampus = incident.map(_.onCampus),
                notifiedPolice = incident.map(_.notifiedPolice),
                notifiedAmbulance = incident.map(_.notifiedAmbulance),
                notifiedFire = incident.map(_.notifiedFire),
                originalEnquiry = enquiry.id,
                caseType = {
                  val typesForTeam = CaseType.valuesFor(enquiry.team)
                  if (typesForTeam.nonEmpty) Some(typesForTeam(Random.nextInt(typesForTeam.size)))
                  else None
                },
                cause = randomEnum(CaseCause)
              ),
              Set(enquiry.client.universityID),
              (1 to (options.CaseTagRate / Random.nextDouble()).toInt).map { _ =>
                randomEnum(CaseTag)
              }.toSet
            ).serviceValue

            // Close the enquiry
            enquiries.updateState(enquiry.id.get, IssueState.Closed, enquiry.lastUpdated).serviceValue

            // Copy enquiry owners to case owners
            cases.setOwners(
              c.id.get,
              enquiries.getOwners(enquiry.id.toSet).serviceValue.values.toSet.flatten.map(_.usercode)
            ).serviceValue

            c -> Set(enquiry.client.universityID)
          }
        } else None
      }

      // Non-enquiry cases
      val nonEnquiryCases: Seq[(Case, Set[UniversityID])] = (enquiryCases.size to options.CasesToGenerate).map { _ =>
        withMockDateTime(randomPastDateTime()) { _ =>
          val team = randomTeam()
          implicit val ac: AuditLogContext = auditLogContext(randomTeamMember(team))

          val clients: Set[UniversityID] =
            (0 to (options.MultiClientCaseRate / Random.nextDouble()).toInt).map { _ =>
              randomClient()
            }.toSet

          val incident: Option[CaseIncidentFormData] =
            if ((options.CaseIncidentRate / Random.nextDouble()).toInt > 0)
              Some(CaseIncidentFormData(
                incidentDate = randomPastDateTime(maximumDaysInPast = 7),
                onCampus = Random.nextBoolean(),
                notifiedPolice = Random.nextBoolean(),
                notifiedAmbulance = Random.nextBoolean(),
                notifiedFire = Random.nextBoolean(),
              ))
            else None

          val c = cases.create(
            Case(
              id = None, // Set by service
              key = None, // Set by service
              subject = dummyWords(Random.nextInt(5) + 3),
              created = JavaTime.offsetDateTime,
              team = team,
              version = JavaTime.offsetDateTime,
              state = IssueState.Open,
              incidentDate = incident.map(_.incidentDate),
              onCampus = incident.map(_.onCampus),
              notifiedPolice = incident.map(_.notifiedPolice),
              notifiedAmbulance = incident.map(_.notifiedAmbulance),
              notifiedFire = incident.map(_.notifiedFire),
              originalEnquiry = None,
              caseType = {
                val typesForTeam = CaseType.valuesFor(team)
                if (typesForTeam.nonEmpty) Some(typesForTeam(Random.nextInt(typesForTeam.size)))
                else None
              },
              cause = randomEnum(CaseCause)
            ),
            clients,
            (1 to (options.CaseTagRate / Random.nextDouble()).toInt).map { _ =>
              randomEnum(CaseTag)
            }.toSet
          ).serviceValue

          val owners: Set[Usercode] = Random.nextInt(4) match {
            case 0 => Set()
            case 1 => Set(randomTeamMember(team))
            case 2 => Set(randomTeamMember(team), randomTeamMember(team))
            case 3 => Set(randomTeamMember(team), randomTeamMember(team), randomTeamMember(initialTeam))
          }

          if (owners.nonEmpty) {
            cases.setOwners(c.id.get, owners).serviceValue
          }

          c -> clients
        }
      }

      var generatedCases = enquiryCases ++ nonEnquiryCases

      // Re-assign some cases
      generatedCases = generatedCases.map { case (c, clients) =>
        withMockDateTime(randomFutureDateTime(base = c.created)) { _ =>
          val teamMember = randomTeamMember(c.team)
          implicit val ac: AuditLogContext = auditLogContext(teamMember)

          val newTeam = randomTeam()
          if (newTeam != c.team && (options.CaseReassignRate / Random.nextDouble()).toInt > 0) {
            val note = CaseNoteSave(dummyWords(Random.nextInt(50)), teamMember)

            cases.reassign(
              c,
              newTeam,
              {
                val typesForTeam = CaseType.valuesFor(newTeam)
                if (typesForTeam.nonEmpty) Some(typesForTeam(Random.nextInt(typesForTeam.size)))
                else None
              },
              note,
              c.version
            ).serviceValue -> clients
          } else c -> clients
        }
      }

      // Link some cases
      generatedCases = generatedCases.map { case (c, clients) =>
        withMockDateTime(randomFutureDateTime(base = c.created)) { _ =>
          val teamMember = randomTeamMember(c.team)
          implicit val ac: AuditLogContext = auditLogContext(teamMember)

          val other = generatedCases(Random.nextInt(generatedCases.size))._1
          if (other != c && (options.CaseLinkRate / Random.nextDouble()).toInt > 0) {
            val note = CaseNoteSave(dummyWords(Random.nextInt(50)), teamMember)

            cases.addLink(CaseLinkType.Related, c.id.get, other.id.get, note).serviceValue
          }

          c -> clients
        }
      }

      // Add some case documents
      val caseDocuments: Seq[(Case, Seq[CaseDocument])] = generatedCases.map { case (c, _) =>
        c -> (1 to Random.nextInt(options.AverageCaseDocumentsPerCase * 2)).map { i =>
          withMockDateTime(randomFutureDateTime(base = c.created, maximumDaysInFuture = i)) { _ =>
            val teamMember = randomTeamMember(c.team)
            implicit val ac: AuditLogContext = auditLogContext(teamMember)

            val document = CaseDocumentSave(randomEnum(CaseDocumentType), teamMember)
            val attachment = randomAttachment()
            val note = CaseNoteSave(dummyWords(Random.nextInt(50)), teamMember)

            cases.addDocument(c.id.get, document, attachment._1, attachment._2, note).serviceValue
          }
        }
      }

      // Add some messages
      val caseMessages: Seq[(Case, Seq[(MessageData, Seq[UploadedFile])])] = generatedCases.map { case (c, clients) =>
        c -> clients.toSeq.flatMap { client =>
          (1 to Random.nextInt(options.AverageMessagesPerCasePerClient * 2)).map { i =>
            withMockDateTime(randomFutureDateTime(base = c.created, maximumDaysInFuture = i)) { _ =>
              val isClient = i % 2 == 0

              val message =
                if (isClient) MessageSave(dummyWords(Random.nextInt(200)), MessageSender.Client, None)
                else MessageSave(dummyWords(Random.nextInt(200)), MessageSender.Team, Some(randomTeamMember(c.team)))

              implicit val ac: AuditLogContext =
                if (isClient) auditLogContext(Usercode(s"u${client.string}"))
                else auditLogContext(message.teamMember.get)

              cases.addMessage(c, client, message, randomAttachments(options.MessageAttachmentRate)).serviceValue
            }
          }
        }
      }

      // Add some notes
      val caseNotes: Seq[(Case, Seq[CaseNote])] = generatedCases.map { case (c, _) =>
        c -> (1 to Random.nextInt(options.AverageNotesPerCase * 2)).map { i =>
          withMockDateTime(randomFutureDateTime(base = c.created, maximumDaysInFuture = i)) { _ =>
            val teamMember = randomTeamMember(c.team)
            implicit val ac: AuditLogContext = auditLogContext(teamMember)

            val note = CaseNoteSave(dummyWords(Random.nextInt(50)), teamMember)

            cases.addGeneralNote(c.id.get, note).serviceValue
          }
        }
      }

      // Generate appointments
      val caseAppointments: Seq[(Appointment, Set[UniversityID])] = generatedCases.flatMap { case (c, clients) =>
        (1 to Random.nextInt(options.AverageAppointmentsPerCase * 2)).map { _ =>
          withMockDateTime(randomFutureDateTime(base = c.created)) { _ =>
            implicit val ac: AuditLogContext = auditLogContext(randomTeamMember(c.team))

            val appointmentType = randomEnum(AppointmentType)

            val appointment = AppointmentSave(
              start = randomFutureDateTime(workingHoursOnly = true),
              duration = Appointment.DurationOptions(Random.nextInt(Appointment.DurationOptions.size))._2,
              roomID =
                if (appointmentType == AppointmentType.FaceToFace)
                  Some(allRooms(Random.nextInt(allRooms.size)).id)
                else None,
              appointmentType = appointmentType,
              purpose = randomEnum(AppointmentPurpose),
            )

            val teamMembers =
              (1 to (options.MultipleTeamMemberAppointmentRate / Random.nextDouble()).toInt).map { _ =>
                randomTeamMember(randomTeam())
              }.toSet + randomTeamMember(c.team)

            val caseIDs =
              (1 to (options.MultipleCaseAppointmentRate / Random.nextDouble()).toInt).map { _ =>
                generatedCases(Random.nextInt(generatedCases.size))._1.id.get
              }.toSet + c.id.get

            appointments.create(appointment, clients, teamMembers, c.team, caseIDs).serviceValue -> clients
          }
        }
      }

      val nonCaseAppointments: Seq[(Appointment, Set[UniversityID])] = (caseAppointments.size to options.AppointmentsToGenerate).map { _ =>
        withMockDateTime(randomPastDateTime()) { _ =>
          val team = randomTeam()
          implicit val ac: AuditLogContext = auditLogContext(randomTeamMember(team))

          val appointmentType = randomEnum(AppointmentType)

          val appointment = AppointmentSave(
            start = randomFutureDateTime(workingHoursOnly = true),
            duration = Appointment.DurationOptions(Random.nextInt(Appointment.DurationOptions.size))._2,
            roomID =
              if (appointmentType == AppointmentType.FaceToFace)
                Some(allRooms(Random.nextInt(allRooms.size)).id)
              else None,
            appointmentType = appointmentType,
            purpose = randomEnum(AppointmentPurpose),
          )

          val clients =
            (1 to (options.MultipleClientAppointmentRate / Random.nextDouble()).toInt).map { _ =>
              randomClient()
            }.toSet + randomClient()

          val teamMembers =
            (1 to (options.MultipleTeamMemberAppointmentRate / Random.nextDouble()).toInt).map { _ =>
              randomTeamMember(randomTeam())
            }.toSet + randomTeamMember(team)

          appointments.create(appointment, clients, teamMembers, team, Set()).serviceValue -> clients
        }
      }

      var generatedAppointments = caseAppointments ++ nonCaseAppointments

      // Client appointment acceptance
      generatedAppointments = generatedAppointments.map { case (appointment, clients) =>
        clients.toSeq.map { client =>
          if ((options.AppointmentAcceptanceRate / Random.nextDouble()).toInt > 0) {
            withMockDateTime(randomFutureDateTime(base = appointment.created)) { _ =>
              implicit val ac: AuditLogContext = auditLogContext(Usercode(s"u${client.string}"))

              appointments.clientAccept(appointment.id, client).serviceValue -> clients
            }
          } else appointment -> clients
        }.last
      }

      // Client appointment decline
      generatedAppointments = generatedAppointments.map { case (appointment, clients) =>
        clients.toSeq.map { client =>
          if ((options.AppointmentDeclineRate / Random.nextDouble()).toInt > 0) {
            withMockDateTime(randomFutureDateTime(base = appointment.created)) { _ =>
              implicit val ac: AuditLogContext = auditLogContext(Usercode(s"u${client.string}"))

              appointments.clientDecline(appointment.id, client, randomEnum(AppointmentCancellationReason)).serviceValue -> clients
            }
          } else appointment -> clients
        }.last
      }

      // Appointment reschedule
      generatedAppointments = generatedAppointments.map { case (appointment, clients) =>
        (1 to (options.AppointmentRescheduleRate / Random.nextDouble()).toInt).map { _ =>
          withMockDateTime(randomFutureDateTime(base = appointment.start).withNano(Random.nextInt(1000000000))) { _ =>
            implicit val ac: AuditLogContext = auditLogContext(randomTeamMember(appointment.team))

            val a = appointments.findForRender(appointment.key).serviceValue

            val changes = AppointmentSave(
              start = randomFutureDateTime(workingHoursOnly = true, base = a.appointment.start),
              duration = a.appointment.duration,
              roomID = a.room.map(_.id),
              appointmentType = a.appointment.appointmentType,
              purpose = a.appointment.purpose,
            )

            appointments.update(
              appointment.id,
              changes,
              a.clientCases.map(_.id.get),
              a.clients.map(_.client.universityID),
              a.teamMembers.map(_.member.usercode),
              a.appointment.lastUpdated
            ).serviceValue -> clients
          }
        }.lastOption.getOrElse(appointment -> clients)
      }

      // Appointment cancellation
      generatedAppointments = generatedAppointments.map { case (appointment, clients) =>
        if ((options.AppointmentCancelledRate / Random.nextDouble()).toInt > 0) {
          withMockDateTime(randomFutureDateTime(base = appointment.created)) { _ =>
            val teamMember = randomTeamMember(appointment.team)
            implicit val ac: AuditLogContext = auditLogContext(teamMember)

          val cancellationNote = Some(AppointmentNoteSave(dummyWords(Random.nextInt(50)), teamMember))
          val a = appointments.cancel(appointment.id, randomEnum(AppointmentCancellationReason), cancellationNote, appointment.lastUpdated).serviceValue

            a -> clients
          }
        } else appointment -> clients
      }

      // Appointment attended
      generatedAppointments = generatedAppointments.filterNot(_._1.state.isTerminal).map { case (appointment, clients) =>
        if ((options.AppointmentAttendedRate / Random.nextDouble()).toInt > 0) {
          withMockDateTime(randomFutureDateTime(base = appointment.start)) { _ =>
            val teamMember = randomTeamMember(appointment.team)
            implicit val ac: AuditLogContext = auditLogContext(teamMember)

            val attendance: Map[UniversityID, (AppointmentState, Option[AppointmentCancellationReason])] =
              clients.map { client =>
                if ((options.AppointmentAttendedRate / Random.nextDouble()).toInt > 0) {
                  (client, (AppointmentState.Attended, None))
                } else {
                  (client, (AppointmentState.Cancelled, Some(randomEnum(AppointmentCancellationReason))))
                }
              }.toMap

            appointments.recordOutcomes(
              appointment.id,
              attendance,
              randomEnum(AppointmentOutcome),
              appointment.lastUpdated,
            ).serviceValue -> clients
          }
        } else appointment -> clients
      }
    } match {
      case Success(_) =>
      case Failure(t) =>
        logger.error("Something went wrong!", t)
        throw new JobExecutionException(t)
    }
  }

}
