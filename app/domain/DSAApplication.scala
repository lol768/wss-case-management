package domain

import java.time.{LocalDate, OffsetDateTime}
import java.util.UUID

import domain.dao.DSADao.StoredDSAApplication
import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

case class DSAApplication (
  customerReference: Option[String],
  applicationDate: Option[LocalDate],
  fundingApproved: Option[Boolean],
  confirmationDate: Option[LocalDate],
  ineligibilityReason: Option[DSAIneligibilityReason],
  fundingTypes: Set[DSAFundingType],
  lastUpdated: OffsetDateTime
) {
  def asStoredApplication(id: UUID): StoredDSAApplication = {
    StoredDSAApplication(
      id = id,
      customerReference = customerReference,
      applicationDate = applicationDate,
      fundingApproved = fundingApproved,
      confirmationDate = confirmationDate,
      ineligibilityReason = ineligibilityReason,
      version = lastUpdated
    )
  }
}

object DSAApplication {
  final val DSATeams: Seq[Team] = Seq(Teams.Disability, Teams.MentalHealth)

  def apply(storedApplication: StoredDSAApplication, fundingTypes: Set[DSAFundingType]): DSAApplication = {
    DSAApplication(
      customerReference = storedApplication.customerReference,
      applicationDate = storedApplication.applicationDate,
      fundingApproved = storedApplication.fundingApproved,
      confirmationDate = storedApplication.confirmationDate,
      ineligibilityReason = storedApplication.ineligibilityReason,
      fundingTypes = fundingTypes,
      lastUpdated = storedApplication.version
    )
  }
}

case class DSAApplicationSave (
  customerReference: Option[String],
  applicationDate: Option[LocalDate],
  fundingApproved: Option[Boolean],
  confirmationDate: Option[LocalDate],
  fundingTypes: Set[DSAFundingType],
  ineligibilityReason: Option[DSAIneligibilityReason]
) {

  def asStoredApplication(id: UUID, version: OffsetDateTime): StoredDSAApplication = {
    StoredDSAApplication(
      id = id,
      customerReference = customerReference,
      applicationDate = applicationDate,
      fundingApproved = fundingApproved,
      confirmationDate = confirmationDate,
      ineligibilityReason = ineligibilityReason,
      version = version
    )
  }
}

object DSAApplicationSave {
  def apply(dsa: DSAApplication): DSAApplicationSave = {
    DSAApplicationSave(
      dsa.customerReference,
      dsa.applicationDate,
      dsa.fundingApproved,
      dsa.confirmationDate,
      dsa.fundingTypes,
      dsa.ineligibilityReason
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
