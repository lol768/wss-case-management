package services

import services.timing.TimingContext

trait NoTimeTracking {

  implicit val timingContext: TimingContext = TimingContext.none

}
