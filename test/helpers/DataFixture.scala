package helpers

/**
  * Class that can be passed to withData (current in AbstractDao)
  */
trait DataFixture[A] {
  /**
    * Set up the fixture.
    * @return anything, which is passed as an argument to the test block.
    */
  def setup(): A

  /**
    * Destroy anything you created in the fixture.
    */
  def teardown(): Unit
}