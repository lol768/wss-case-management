package system

import play.api.Logger

trait Logging { self =>

  @transient lazy val logger = Logger(self.getClass)

  def log(message: String): Unit = {
    logger.info(message)
  }

}
