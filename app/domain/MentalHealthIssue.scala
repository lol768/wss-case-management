package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed abstract class MentalHealthIssue(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = this.entryName
}
object MentalHealthIssue extends PlayEnum[MentalHealthIssue] {
  case object ADHDADD extends MentalHealthIssue("ADHD / ADD")
  case object Addiction extends MentalHealthIssue("Addiction")
  case object Anxiety extends MentalHealthIssue("Anxiety")
  case object ASD extends MentalHealthIssue("ASD")
  case object Behavioural extends MentalHealthIssue("Behavioural")
  case object BipolarDisorder extends MentalHealthIssue("Bipolar disorder")
  case object BodyDysmorphicDisorder extends MentalHealthIssue("Body dysmorphic disorder")
  case object BorderlineEUPDDiagnosis extends MentalHealthIssue("Borderline personality disorder / EUPD")
  case object Complex extends MentalHealthIssue("Complex")
  case object Depression extends MentalHealthIssue("Depression")
  case object DissociativeDisorder extends MentalHealthIssue("Dissociative disorder")
  case object EatingDisorder extends MentalHealthIssue("Eating disorder")
  case object GenderDysphoria extends MentalHealthIssue("Gender dysphoria")
  case object HealthAcute extends MentalHealthIssue("Health, acute")
  case object HealthChronic extends MentalHealthIssue("Health, chronic")
  case object HxAbuse extends MentalHealthIssue("History of abuse")
  case object HxTrauma extends MentalHealthIssue("History of trauma")
  case object OCD extends MentalHealthIssue("OCD")
  case object PanicDisorder extends MentalHealthIssue("Panic disorder")
  case object Phobia extends MentalHealthIssue("Phobia")
  case object Psychosis extends MentalHealthIssue("Psychosis")
  case object PTSD extends MentalHealthIssue("PTSD")
  case object SelfInjury extends MentalHealthIssue("Self injury")
  case object Sexuality extends MentalHealthIssue("Sexuality")
  case object SleepDisorder extends MentalHealthIssue("Sleep disorder")
  case object Stress extends MentalHealthIssue("Stress")
  case object SubstanceMisuse extends MentalHealthIssue("Substance misuse")
  case object SuicidalIdeation extends MentalHealthIssue("Suicidal ideation")
  case object SuicidalBehaviour extends MentalHealthIssue("Suicidal behaviour")
  case object Wellbeing extends MentalHealthIssue("Wellbeing")

  override def values: immutable.IndexedSeq[MentalHealthIssue] = findValues
}

