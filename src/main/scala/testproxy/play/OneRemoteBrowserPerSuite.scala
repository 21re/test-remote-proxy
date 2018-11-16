package testproxy.play
import akka.util.ByteString
import org.openqa.selenium.WebDriver
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.selenium.WebBrowser
import org.scalatest._
import org.scalatestplus.play.{AppProvider, BrowserFactory}
import org.scalatestplus.play.BrowserFactory.UnavailableDriver
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.util.Try

trait OneRemoteBrowserPerSuite
    extends TestSuiteMixin
    with WebBrowser
    with Eventually
    with IntegrationPatience
    with BrowserFactory { this: TestSuite with AppProvider =>

  def remoteProxyHost: String

  def mangleRequest(request: FakeRequest[ByteString]): FakeRequest[ByteString] = request

  def mangleResult(result: Result): Result = result

  /**
    * An implicit instance of `WebDriver`, created by calling `createWebDriver`.
    * If there is an error when creating the `WebDriver`, `UnavailableDriver` will be assigned
    * instead.
    */
  implicit lazy val webDriver: WebDriver = createWebDriver()

  lazy val remoteProxy =
    new ForwardToApp(s"ws://$remoteProxyHost/v1/listen", app, mangleRequest, mangleResult)

  lazy val remotePort: Int = await(remoteProxy.bind)

  /**
    * Automatically cancels tests with an appropriate error message when the `webDriver` field is a `UnavailableDriver`,
    * else calls `super.withFixture(test)`
    */
  abstract override def withFixture(test: NoArgTest): Outcome = {
    webDriver match {
      case UnavailableDriver(ex, errorMessage) =>
        ex match {
          case Some(e) => cancel(errorMessage, e)
          case None    => cancel(errorMessage)
        }
      case _ => super.withFixture(test)
    }
  }

  /**
    * Places the `WebDriver` provided by `webDriver` into the `ConfigMap` under the key
    * `org.scalatestplus.play.webDriver` to make
    * it available to nested suites; calls `super.run`; and lastly ensures the `WebDriver` is stopped after
    * all tests and nested suites have completed.
    *
    * @param testName an optional name of one test to run. If `None`, all relevant tests should be run.
    *                 I.e., `None` acts like a wildcard that means run all relevant tests in this `Suite`.
    * @param args the `Args` for this run
    * @return a `Status` object that indicates when all tests and nested suites started by this method have completed, and whether or not a failure occurred.
    */
  abstract override def run(testName: Option[String], args: Args): Status = {
    val cleanup: Try[Boolean] => Unit = { _ =>
      remoteProxy.stop()
      webDriver match {
        case _: UnavailableDriver => // do nothing for UnavailableDriver
        case _                    => webDriver.quit()
      }
    }
    try {
      val newConfigMap = args.configMap + ("org.scalatestplus.play.webDriver" -> webDriver)
      val newArgs      = args.copy(configMap = newConfigMap)
      val status       = super.run(testName, newArgs)
      status.whenCompleted(cleanup)
      status
    } catch {
      case ex: Throwable =>
        cleanup(Try(false))
        throw ex
    }
  }
}
