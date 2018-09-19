package views.b3.wellbeing

import play.twirl.api.HtmlFormat
import views.html.b3.{B3FieldConstructor, B3FieldInfo}
import views.html.b3.wellbeing.vertical._

/** Override default VerticalFieldConstructor to enable more layout options in Twirl template
  */
package object vertical {

  import play.api.i18n.MessagesProvider
  import play.api.mvc.{Call, RequestHeader}
  import play.twirl.api.Html
  import views.html.bs.Args.isTrue

  /**
    * Declares the class for the Horizontal FieldConstructor.
    * It needs the column widths for the corresponding Bootstrap3 form-group
    */
  case class VerticalFieldConstructor(withFeedbackIcons: Boolean = false) extends B3FieldConstructor {

    /* Define the class of the corresponding form */
    val formClass = "form-vertical form-wellbeing"
    /* Renders the corresponding template of the field constructor */
    def apply(fieldInfo: B3FieldInfo, inputHtml: Html)(implicit msgsProv: MessagesProvider): Html = bsFieldConstructor(fieldInfo, inputHtml)(this, msgsProv)
    /* Renders the corresponding template of the form group */
    def apply(contentHtml: Html, argsMap: Map[Symbol, Any])(implicit msgsProv: MessagesProvider): Html = bsFormGroup(contentHtml, argsMap)(msgsProv)
  }

  /**
    * Creates a new VerticalFieldConstructor to use for specific forms or scopes (don't use it as a default one).
    * If a default B3FieldConstructor and a specific VerticalFieldConstructor are within the same scope, the more
    * specific will be chosen.
    */
  def fieldConstructorSpecific(withFeedbackIcons: Boolean = false) = VerticalFieldConstructor(withFeedbackIcons)

  /**
    * Returns it as a B3FieldConstructor to use it as default within a template
    */
  def fieldConstructor(withFeedbackIcons: Boolean = false): B3FieldConstructor = fieldConstructorSpecific(withFeedbackIcons)

  /**
    * **********************************************************************************************************************************
    * SHORTCUT HELPERS
    * *********************************************************************************************************************************
    */
  def form(action: Call, args: (Symbol, Any)*)(body: VerticalFieldConstructor => Html): HtmlFormat.Appendable = {
    val hfc = fieldConstructorSpecific(withFeedbackIcons = isTrue(args, '_feedbackIcons))
    views.html.b3.form(action, args: _*)(body(hfc))(hfc)
  }
  def formCSRF(action: Call, args: (Symbol, Any)*)(body: VerticalFieldConstructor => Html)(implicit request: RequestHeader): HtmlFormat.Appendable = {
    val hfc = fieldConstructorSpecific(withFeedbackIcons = isTrue(args, '_feedbackIcons))
    views.html.b3.formCSRF(action, args: _*)(body(hfc))(hfc, request)
  }

}