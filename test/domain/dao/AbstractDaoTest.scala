package domain.dao

import helpers.{MorePatience, OneAppPerSuite}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec

abstract class AbstractDaoTest extends PlaySpec with MockitoSugar with OneAppPerSuite with ScalaFutures with MorePatience
