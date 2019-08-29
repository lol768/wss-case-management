package helpers

import com.vladsch.flexmark.ext.autolink.AutolinkExtension
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser

import scala.collection.JavaConverters._

object CommonmarkRenderer {

  private[this] val Extensions = Seq(AutolinkExtension.create()).asJava

  private[this] val MarkdownParser = Parser.builder().extensions(Extensions).build()

  private[this] val MarkdownRenderer = HtmlRenderer.builder().extensions(Extensions).build()

  def renderMarkdown(source: String): String =
    MarkdownRenderer.render(MarkdownParser.parse(source))

}
