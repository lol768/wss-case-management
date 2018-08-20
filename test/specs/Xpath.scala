package specs

object Xpath {

  def hasClass(className: String, tagName: String = "*"): String =
    s"$tagName[contains(@class,'$className')]"

}
