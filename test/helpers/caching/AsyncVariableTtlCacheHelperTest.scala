package helpers.caching

import java.time.ZonedDateTime

import helpers.JavaTimeTest
import javax.inject.Provider
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Logger

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class AsyncVariableTtlCacheHelperTest extends PlaySpec with MockitoSugar with ScalaFutures {

  import scala.concurrent.ExecutionContext.Implicits.global

  def answerNumberStrings(): Answer[String] = new Answer[String] {
    private var i = 0
    override def answer(invocation: InvocationOnMock): String = {
      i += 1
      s"Value $i"
    }
  }

  def ttlRule(v: String) = Ttl(10.seconds, 10.minutes, 1.hour)

  "AsyncSoftTtlCacheHelper" should {

    // This test partially relies on the fact that the fake cache API doesn't
    // use actual futures and completes immediately. Otherwise the background
    // update might not have triggered yet.
    "call once within soft TTL and do a background update when outside the soft TTL" in {
      val provider = mock[Provider[String]]
      when(provider.get).thenAnswer(answerNumberStrings())
      val wrapper = VariableTtlCacheHelper.async(new NeverExpiringMemoryAsyncCacheApi(), Logger("test"), ttlRule)
      def fetchData() = wrapper.getOrElseUpdate("mydata") { Future.successful(provider.get) }

      fetchData().futureValue mustBe "Value 1"
      fetchData().futureValue mustBe "Value 1"
      JavaTimeTest.withMockDateTime(ZonedDateTime.now.plusMinutes(1).toInstant){
        fetchData().futureValue mustBe "Value 1" // returns the cached value but does a background update
      }
      fetchData().futureValue mustBe "Value 2"
      verify(provider, times(2)).get
      verifyNoMoreInteractions(provider)
    }

    "return a fresh value once outside the medium TTL" in {
      val provider = mock[Provider[String]]
      when(provider.get).thenAnswer(answerNumberStrings())
      val wrapper = VariableTtlCacheHelper.async(new NeverExpiringMemoryAsyncCacheApi(), Logger("test"), ttlRule)
      def fetchData() = wrapper.getOrElseUpdate("mydata") { Future.successful(provider.get) }

      fetchData().futureValue mustBe "Value 1"
      fetchData().futureValue mustBe "Value 1"

      JavaTimeTest.withMockDateTime(ZonedDateTime.now.plusMinutes(12).toInstant) {
        fetchData().futureValue mustBe "Value 2"
      }

      verify(provider, times(2)).get
      verifyNoMoreInteractions(provider)
    }

    "return the stale value outside the medium TTL if the update fails" in {
      val provider = mock[Provider[String]]
      when(provider.get).thenAnswer(answerNumberStrings())
      val wrapper = VariableTtlCacheHelper.async(new NeverExpiringMemoryAsyncCacheApi(), Logger("test"), ttlRule)
      var firstCall = true
      def fetchData() = wrapper.getOrElseUpdate("mydata") {
        if(firstCall) {
          firstCall = false
          Future.successful(provider.get)
        } else {
          Future.failed(new Throwable("Herons are inside your computer!"))
        }
      }

      fetchData().futureValue mustBe "Value 1"
      JavaTimeTest.withMockDateTime(ZonedDateTime.now.plusMinutes(12).toInstant){
        fetchData().futureValue mustBe "Value 1"
      }
      verify(provider).get
      verifyNoMoreInteractions(provider)
    }

    class ClassChangeContext {
      val cache = new NeverExpiringMemoryAsyncCacheApi()

      // get a String value put in the cache
      private val oldWrapper = VariableTtlCacheHelper.async[String](cache, Logger("test"), 1.second, 1.minute, 1.hour)
      Await.ready(
        oldWrapper.getOrElseUpdate("mykey")(Future.successful("oldvalue")),
        Duration.Inf
      )

      val wrapper: AsyncVariableTtlCacheHelper[Option[String]] = VariableTtlCacheHelper.async[Option[String]](cache, Logger("test"), 1.second, 1.minute, 1.hour)
    }

    "handle a class change in getOrElseUpdateElement" in new ClassChangeContext {
      val result: CacheElement[Option[String]] = wrapper.getOrElseUpdateElement("mykey")(Future.successful(Some("newvalue")))(CacheOptions.default).futureValue
      result.value mustBe Some("newvalue")
    }

    "handle a class change in getOrElseUpdate" in new ClassChangeContext {
      val result: Option[String]  = wrapper.getOrElseUpdate("mykey")(Future.successful(Some("newvalue"))).futureValue
      result mustBe Some("newvalue")
    }

  }
}
