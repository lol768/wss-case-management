package specs

import org.dom4j.{Document, Element, Node}
import org.dom4j.io.DOMReader
import org.htmlcleaner.{DomSerializer, HtmlCleaner}
import play.api.mvc.Result
import play.api.test.Helpers._
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
    val props = cleaner.getProperties()
    cleaner
  }

  /**
    * Parse with HTMLCleaner (because it's not XML) then
    * output as a W3C Document so that Dom4j can read it.
    */
  def contentAsHtml(res: Future[Result]): HtmlNavigator = {
    val tagNode = htmlCleaner.clean(contentAsString(res))
    val serializer = new DomSerializer(htmlCleaner.getProperties())
    val doc: org.w3c.dom.Document = serializer.createDOM(tagNode)
    new HtmlNavigator(new DOMReader().read(doc))
  }

  /**
    * Wrapper around a parsed document, with methods for querying on various parts of
    * the ID7scape and CASE page layouts.
    */
  class HtmlNavigator(html: Document) {
    /**
      * Return all the ID7 navigation links on the given page.
      * It is a flat list, so all the nested items in submenus and secondary and tertiary
      * items are all included. It would be possible to parse these with a bit of work.
      */
    lazy val navigationPages: Seq[(String, Uri)] =
      xpathElements(html, "//nav[@role='navigation']//a").map { link =>
        link.getText() -> Uri.parse(link.asInstanceOf[Element].attributeValue("href"))
      }

    lazy val pageHeading: String =
      html.selectSingleNode("//div[@class='id7-page-title']/h1").getText
  }

  private def xpathNodes(html: Document, path: String): Seq[Node] =
    html.selectNodes(path).asScala.toSeq

  private def xpathElements(html: Document, path: String): Seq[Element] =
    xpathNodes(html, path).map(_.asInstanceOf[Element])
}
