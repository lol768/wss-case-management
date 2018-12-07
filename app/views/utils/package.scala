package views

import play.api.data.Form
import play.twirl.api._

/**
  * Miscellaneous view functions, as you might imagine.
  *
  * Nothing app-specific in here, just generic form handling etc.
  */
package object utils {

  private val BR: Html = html"<br />"


  /**
    * If you are using a set of checkboxes to build a seq type field, this
    * returns whether the value is present (and so whether the checkbox should
    * be checked)
    *
    * @param form The form
    * @param fieldName The field name _without_ any [] brackets
    * @param value The value of this checkbox
    */
  def checked[A](form: Form[A], fieldName: String, value: Option[Any]): Boolean =
    value.exists(v => seqValues(form,fieldName).contains(v))

  /**
    * For a seq-type field, the locations of the values are stored under field x
    * but each actual value is stored under field[x], so this does the legwork.
    *
    * @param fieldName The field name _without_ any [] brackets
    */
  def seqValues[A](form: Form[A], fieldName: String): Seq[String] =
    form(fieldName).indexes.flatMap { i =>
      form(s"$fieldName[$i]").value
    }

  def nl2br(text: String): Content = HtmlFormat.fill(text.trim.split("\n").flatMap { line =>
    Seq(HtmlFormat.escape(line), BR)
  }.toList)
}