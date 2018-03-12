package testproxy.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

object Main extends App {

  implicit val system: ActorSystem        = ActorSystem("test-proxy-server")
  implicit def ec: ExecutionContext       = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  lazy val routes: Route = pathPrefix("v1") {
    pathPrefix("listen") {
      path(IntNumber) { port =>
        val proxyServer = new ProxyServer(port)

        handleWebSocketMessages(proxyServer.listenFlow)
      }
    }
  }

  Http().bindAndHandle(routes, "0.0.0.0", 8888).map { binding =>
    system.log.info(s"Server listening on $binding")
  }

  Await.result(system.whenTerminated, Duration.Inf)
}
