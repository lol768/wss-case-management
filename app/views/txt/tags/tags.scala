package views.txt

package object tags {

  def threeStateBoolean(b: Option[Boolean]): String = b match {
    case Some(true) => "YES"
    case Some(false) => "NO"
    case _ => "NOT SURE"
  }

}
