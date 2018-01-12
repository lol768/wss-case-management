import javax.inject.{Inject, Singleton}

import org.databrary.PlayLogbackAccessFilter
import play.api.http.DefaultHttpFilters
import play.filters.csrf.CSRFFilter
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersFilter

@Singleton
class Filters @Inject()(
  csrfFilter: CSRFFilter,
  securityHeadersFilter: SecurityHeadersFilter,
  accessLogFilter: PlayLogbackAccessFilter,
  gzipFilter: GzipFilter
) extends DefaultHttpFilters(csrfFilter, securityHeadersFilter, accessLogFilter, gzipFilter)