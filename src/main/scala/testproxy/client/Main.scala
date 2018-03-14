package testproxy.client

import akka.util.ByteString
import testproxy.api.{ProxyRequest, ProxyResponse}

import scala.concurrent.{ExecutionContext, Future}

object Main extends App {
  println("Start client")

  val remoteProxy = new ForwardRemoteProxy("ws://localhost:8888/v1/listen", "https://21re.works")
//  val remoteProxy = new RemoteProxy("ws://localhost:8888/v1/listen") {
//    override def handler = {
//      case ProxyRequest(id, "GET", "/", _, _) =>
//        Future.successful(ProxyResponse(id, 200, Seq.empty, ByteString("Hubba")))
//    }
//  }

  implicit def ec: ExecutionContext = remoteProxy.ec

  remoteProxy.bind.foreach { port =>
    println(s"Server is listening on port: $port")
  }

  Console.in.read()

  println("Stopping")

  remoteProxy.stop()

  println("Done")
}
