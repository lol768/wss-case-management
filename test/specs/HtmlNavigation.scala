package specs

import org.dom4j.{Document, Element, Node}
import org.dom4j.io.DOMReader
import org.htmlcleaner.{DomSerializer, HtmlCleaner}
import play.api.mvc.Result
import play.api.test.Helpers._
import play.twirl.api.HtmlFormat
import uk.ac.warwick.util.web.Uri

import collection.JavaConverters._
import scala.concurrent.Future

/**
  * Trait for parsing an HTML response into a document object
  * and some helper methods for querying on the state of the page.
  *
  * Generally helper methods should be defined in here rather than being
  * written in tests, so that they can be reused and to limit the spread of XPath
  * mess throughout the tests. Tests themselves should read fairly fluently.
  */
trait HtmlNavigation {

  private lazy val htmlCleaner = {
    val cleaner = new HtmlCleaner()
    // val props = cleaner.getProperties
    cleaner
  }

  /**
    * Parse with HTMLCleaner (because it's not XML) then
    * output as a W3C Document so that Dom4j can read it.
    */
  def contentAsHtml(res: Future[Result]): HtmlNavigator = {
    val tagNode = htmlCleaner.clean(contentAsString(res))
    val serializer = new DomSerializer(htmlCleaner.getProperties)
    val doc: org.w3c.dom.Document = serializer.createDOM(tagNode)
    new HtmlNavigator(new DOMReader().read(doc))
  }

  /**
    * Wrapper around a parsed document, with methods for querying on various parts of
    * the ID7scape and CASE page layouts.
    */
  class HtmlNavigator(html: Document) {
    import X._

    case class NavigationPages(
      primary: Seq[(String, Uri)],
      secondary: Seq[(String, Uri)],
      tertiary: Seq[(String, Uri)],
      all: Seq[(String, Uri)]
    )

    /**
      * Return all the ID7 navigation links on the given page.
      */
    lazy val navigationPages: NavigationPages = {
      def linkElementsToStrings(elements: Seq[Element]) =
        elements.map(link => link.getText -> Uri.parse(link.attributeValue("href")))

      NavigationPages(
        linkElementsToStrings(xpathElements(html, "//nav[contains(@class, 'navbar-primary')][@role='navigation']//a")),
        linkElementsToStrings(xpathElements(html, "//nav[contains(@class, 'navbar-secondary')][@role='navigation']//a")),
        linkElementsToStrings(xpathElements(html, "//nav[contains(@class, 'navbar-tertiary')][@role='navigation']//a")),
        linkElementsToStrings(xpathElements(html, "//nav[@role='navigation']//a"))
      )
    }

    // A page may have a nav-tabs as part of its content.
    lazy val contentTabs: Seq[String] =
      xpathNodes(html, s"//$MAIN_CONTENT//$NAVTABS//li/a").map(_.getText.trim)

    lazy val pageHeading: String =
      html.selectSingleNode("//div[contains(@class,'id7-page-title')]/h1").getText.trim
  }

  // Bits of XPath query for re-use.
  object X {
    val MAIN_CONTENT = "div[contains(@class,'id7-main-content')]"
    val NAVTABS = "ul[contains(@class, 'nav-tabs')]"
  }

  private def xpathNodes(html: Document, path: String): Seq[Node] =
    html.selectNodes(path).asScala

  private def xpathElements(html: Document, path: String): Seq[Element] =
    xpathNodes(html, path).map(_.asInstanceOf[Element])
}
