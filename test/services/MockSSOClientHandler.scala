package services

import org.apache.commons.configuration.Configuration
import uk.ac.warwick.sso.client.cache.UserCache
import uk.ac.warwick.sso.client.core.{HttpRequest, OnCampusService, Response}
import uk.ac.warwick.sso.client.{AttributeAuthorityResponseFetcher, SSOClientHandler}
import uk.ac.warwick.userlookup.UserLookupInterface

import scala.beans.{BeanProperty, BooleanBeanProperty}

class MockSSOClientHandler extends SSOClientHandler {
  // Always an empty response - we populate a request attribute to get users
  override def handle(request: HttpRequest): Response = {
    val r = new Response
    r.setContinueRequest(true)
    r
  }

  @BeanProperty
  var aaFetcher: AttributeAuthorityResponseFetcher = _

  @BeanProperty
  var cache: UserCache = _

  @BeanProperty
  var config: Configuration = _

  @BooleanBeanProperty
  var detectAnonymousOnCampusUsers: Boolean = false

  @BooleanBeanProperty
  var redirectToRefreshSession: Boolean = true

  @BeanProperty
  var userLookup: UserLookupInterface = _

  @BeanProperty
  var onCampusService: OnCampusService = _
}
