package services

trait NoTimeTracking {

  implicit val timingContext: TimingContext = TimingContext.none

}
