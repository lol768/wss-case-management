package system

import javax.inject.{Inject, Singleton}
import play.filters.csrf.{CSRF, CSRFConfig}

@Singleton
class CSRFPageHelperFactory @Inject() (csrfConfig: CSRFConfig) {

  def getInstance(token: Option[CSRF.Token]): CSRFPageHelper = {
    new CSRFPageHelper(csrfConfig, token)
  }

}
