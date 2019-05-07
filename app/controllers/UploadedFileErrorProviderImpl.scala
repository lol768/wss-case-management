package controllers

import play.api.mvc.RequestHeader
import play.twirl.api.Html
import system.ImplicitRequestContext
import warwick.fileuploads.UploadedFileControllerHelper.{TemporaryUploadedFile, UploadedFileConfiguration}
import warwick.fileuploads.UploadedFileErrorProvider


class UploadedFileErrorProviderImpl extends UploadedFileErrorProvider with ImplicitRequestContext {

  def provideError(renderedError: Html, title: String, file: TemporaryUploadedFile, config: UploadedFileConfiguration, request: RequestHeader): Html = {
    views.html.errors.genericFileError(renderedError, title, file, config)(requestContext(request))
  }

}
