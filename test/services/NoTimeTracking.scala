package services

import warwick.core.timing.TimingContext

trait NoTimeTracking {

  implicit val timingContext: TimingContext = TimingContext.none

}
