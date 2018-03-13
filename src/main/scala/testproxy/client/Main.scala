package testproxy.client

import akka.util.ByteString
import testproxy.api.{ProxyRequest, ProxyResponse}

import scala.concurrent.Future

object Main extends App {
  val remoteProxy = new RemoteProxy("ws://localhost:8888/v1/listen/8000") {
    override def handler = {
      case ProxyRequest(id, "GET", "/", _, _) =>
        Future.successful(ProxyResponse(id, 200, Seq.empty, ByteString("Hubba")))
    }
  }

  println("Start client")

  Console.in.read()

  println("Stopping")

  remoteProxy.stop()

  println("Done")
}
