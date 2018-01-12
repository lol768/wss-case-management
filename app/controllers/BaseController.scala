package controllers

import play.api.i18n.I18nSupport
import play.api.mvc.InjectedController
import system.{ImplicitRequestContext, Logging}

trait BaseController extends InjectedController
  with I18nSupport
  with ControllerHelper
  with Logging
  with ImplicitRequestContext
