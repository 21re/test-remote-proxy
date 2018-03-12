package testproxy.client

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.stream.{ActorMaterializer, Materializer}

import scala.concurrent.ExecutionContext

class RemoteProxy(endpoint: String) extends ClientFlow {
  implicit val system: ActorSystem        = ActorSystem("test-proxy-client")
  implicit def ec: ExecutionContext       = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  val (upgradeResponse, killSwitches) =
    Http().singleWebSocketRequest(WebSocketRequest(endpoint), clientFlow)

  val connected = upgradeResponse.map { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  connected.onComplete(println)
}
