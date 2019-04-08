package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed abstract class MentalHealthIssue(val description: String) extends EnumEntry with IdAndDescription {
  override val id: String = this.entryName
}
object MentalHealthIssue extends PlayEnum[MentalHealthIssue] {
  case object ADHDADD extends MentalHealthIssue("ADHD/ADD")
  case object Addiction extends MentalHealthIssue("Addiction")
  case object Anxiety extends MentalHealthIssue("Anxiety")
  case object ASD extends MentalHealthIssue("ASD")
  case object Behavioural extends MentalHealthIssue("Behavioural")
  case object BipolarDisorder extends MentalHealthIssue("Bipolar Disorder")
  case object BodyDysmorphicDisorder extends MentalHealthIssue("Body Dysmorphic Disorder")
  case object BorderlineEUPDDiagnosis extends MentalHealthIssue("Borderline/EU PD diagnosis")
  case object Complex extends MentalHealthIssue("Complex")
  case object Depression extends MentalHealthIssue("Depression")
  case object DissociativeDisorder extends MentalHealthIssue("Dissociative Disorder")
  case object EatingDisorder extends MentalHealthIssue("Eating Disorder")
  case object GenderDysphoria extends MentalHealthIssue("Gender Dysphoria")
  case object HealthAcute extends MentalHealthIssue("Health, Acute")
  case object HealthChronic extends MentalHealthIssue("Health, Chronic")
  case object HxAbuse extends MentalHealthIssue("Hx Abuse")
  case object HxTrauma extends MentalHealthIssue("Hx Trauma")
  case object OCD extends MentalHealthIssue("OCD")
  case object PanicDisorder extends MentalHealthIssue("Panic Disorder")
  case object Phobia extends MentalHealthIssue("Phobia")
  case object Psychosis extends MentalHealthIssue("Psychosis")
  case object PTSD extends MentalHealthIssue("PTSD")
  case object SelfInjury extends MentalHealthIssue("Self Injury")
  case object Sexuality extends MentalHealthIssue("Sexuality")
  case object SleepDisorder extends MentalHealthIssue("Sleep Disorder")
  case object Stress extends MentalHealthIssue("Stress")
  case object SubstanceMisuse extends MentalHealthIssue("Substance Misuse")
  case object SuicidalIdeation extends MentalHealthIssue("Suicidal Ideation")
  case object SuicidalBehaviour extends MentalHealthIssue("Suicidal Behaviour")
  case object Wellbeing extends MentalHealthIssue("Wellbeing")

  override def values: immutable.IndexedSeq[MentalHealthIssue] = findValues
}

