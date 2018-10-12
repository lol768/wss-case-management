package specs

import helpers.OneServerPerSuite
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mockito.MockitoSugar
import org.scalatest.selenium.WebBrowser
import org.scalatestplus.play.PlaySpec

/**
  * Base class to run against a real browser.
  *
  * We don't currently have any tests that use this because
  * it requires work to allow authentication - you can't
  * just drop stuff into the Request as we can in BaseSpec.
  */
abstract class BaseSeleniumSpec extends PlaySpec
  with MockitoSugar
  with OneServerPerSuite
  with HtmlNavigation
  with WebBrowser
  with BeforeAndAfterAll {

  implicit val webDriver: WebDriver = new ChromeDriver()

  setCaptureDir("target/screenshots/")

  def screenshot(title: String): Unit = {
    if (isScreenshotSupported) {
      capture to title
    }
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    webDriver.close()
  }

}
