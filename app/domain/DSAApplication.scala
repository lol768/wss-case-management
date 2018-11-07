package domain

import java.time.OffsetDateTime
import java.util.UUID

import domain.dao.DSADao.StoredDSAApplication
import enumeratum.{EnumEntry, PlayEnum}
import warwick.core.helpers.JavaTime

import scala.collection.immutable



case class DSAApplication (
  id: Option[UUID],
  applicationDate: Option[OffsetDateTime],
  fundingApproved: Option[Boolean],
  confirmationDate: Option[OffsetDateTime],
  ineligibilityReason: Option[DSAIneligibilityReason],
  fundingTypes: Set[DSAFundingType],
  version: OffsetDateTime = JavaTime.offsetDateTime
) {
  def asStoredApplication: StoredDSAApplication = {
    StoredDSAApplication(
      id = id,
      applicationDate = applicationDate,
      fundingApproved = fundingApproved,
      confirmationDate = confirmationDate,
      ineligibilityReason = ineligibilityReason,
      version = version
    )
  }
}

object DSAApplication {
  final val DSATeams: Seq[Team] = Seq(Teams.Disability, Teams.MentalHealth)

  def apply(storedApplication: StoredDSAApplication, fundingTypes: Set[DSAFundingType]): DSAApplication = {
    DSAApplication(
      id = storedApplication.id,
      applicationDate = storedApplication.applicationDate,
      fundingApproved = storedApplication.fundingApproved,
      confirmationDate = storedApplication.confirmationDate,
      ineligibilityReason = storedApplication.ineligibilityReason,
      fundingTypes = fundingTypes,
      version = storedApplication.version
    )
  }
}

sealed abstract class DSAFundingType(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = entryName
}

object DSAFundingType extends PlayEnum[DSAFundingType] {
  case object AssistiveTechnology extends DSAFundingType("Assistive technology")
  case object NmhBand12 extends DSAFundingType("NMH Band 1 & 2")
  case object NmhBand34 extends DSAFundingType("NMH Band 3 & 4")
  case object GeneralAllowance extends DSAFundingType("General allowance")
  case object TravelCosts extends DSAFundingType("Taxi/travel costs")

  override def values: immutable.IndexedSeq[DSAFundingType] = findValues
}

sealed abstract class DSAIneligibilityReason(val description: String) extends EnumEntry
object DSAIneligibilityReason extends PlayEnum[DSAIneligibilityReason] {
  case object EUStudent extends DSAIneligibilityReason("EU student")
  case object InternationalStudent extends DSAIneligibilityReason("International student")
  case object HomeStudent extends DSAIneligibilityReason("Home student")
  case object NoApplication extends DSAIneligibilityReason("Decided not to apply")
  case object InsufficientEvidence extends DSAIneligibilityReason("Insufficient evidence")

  override def values: immutable.IndexedSeq[DSAIneligibilityReason] = findValues
}
