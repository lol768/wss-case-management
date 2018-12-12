import java.net.InetAddress
import java.time.LocalDateTime

import sbt._
import spray.json._
import DefaultJsonProtocol._

import scala.sys.process.Process
import scala.xml.Elem
import scala.xml.XML._

object NodePackageAudit {
  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any): JsValue = x match {
      case n: Int => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
      case m: Map[String @unchecked, Any @unchecked] => JsObject(m.mapValues(write))
      case s: Seq[Any @unchecked] => JsArray(s.map(write).toVector)
      case null => JsNull
    }
    def read(value: JsValue): Any = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
      case JsObject(fields) => fields.mapValues(read)
      case JsArray(elements) => elements.map(read)
      case JsNull => null
    }
  }

  def audit(baseDirectory: File): Unit = {
    val start = System.currentTimeMillis()
    val results: Map[String, Any] = Process("npm audit --json", baseDirectory).lineStream_!.mkString.parseJson.convertTo[Map[String, Any]]
    val timeTakenSecs: Double = (System.currentTimeMillis() - start) / 1000.0

    val testCases: Seq[Elem] =
      if (results.contains("advisories")) {
        results("advisories").asInstanceOf[Map[String, Map[String, Any]]].toSeq.map { case (id, advisory) =>
          <testcase name={ id } classname="npm.audit" time={ timeTakenSecs.formatted("%.3f") }>
            <failure message={ advisory("title").asInstanceOf[String] } type={ advisory("severity").asInstanceOf[String] }>
Package: { advisory("module_name").asInstanceOf[String] }
Patched in: { advisory("patched_versions").asInstanceOf[String] }
Path: { advisory("findings").asInstanceOf[Seq[Map[String, Any]]].head("paths").asInstanceOf[Seq[String]].head }
More info: { advisory("url").asInstanceOf[String] }

{ advisory("overview").asInstanceOf[String] }
            </failure>
          </testcase>
        }
      } else {
        Seq(<testcase name="audit" classname="npm.audit" time={ timeTakenSecs.formatted("%.3f") }></testcase>)
      }

    val output = new File(
      new File(
        new File(baseDirectory, "target"),
        "test-reports"
      ), "TEST-npm.audit.xml")

    val xml =
      <testsuite
        hostname={ InetAddress.getLocalHost.getHostName }
        name="npm.audit"
        tests={ testCases.size.toString }
        errors="0"
        failures={ if (results.contains("advisories")) testCases.size.toString else "0" }
        skipped="0"
        time={ timeTakenSecs.formatted("%.3f") }
        timestamp={ LocalDateTime.now().toString }>
        { testCases }
        <system-out><![CDATA[]]></system-out>
        <system-err><![CDATA[]]></system-err>
      </testsuite>

    output.getParentFile.mkdirs()
    save(output.getAbsolutePath, xml, "UTF-8", xmlDecl = true)
  }
}
