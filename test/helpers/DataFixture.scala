package helpers

trait DataFixture {
  def setup(): Unit
  def teardown(): Unit
}