package testproxy.client

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches, Materializer, UniqueKillSwitch}
import akka.util.ByteString
import testproxy.api.{JsonSupport, ProxyRequest, ProxyResponse}
import spray.json._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

abstract class RemoteProxy(proxyEndpoint: String) extends JsonSupport {
  implicit val system: ActorSystem        = ActorSystem("test-proxy-client")
  implicit def ec: ExecutionContext       = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  def handler: PartialFunction[ProxyRequest, Future[ProxyResponse]]

  val (upgradeResponse, killSwitches) =
    Http().singleWebSocketRequest(WebSocketRequest(proxyEndpoint), clientFlow)

  val connected = upgradeResponse.map { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  def stop(): Unit = {
    killSwitches.shutdown()

    Await.result(system.terminate(), Duration.Inf)
  }

  def clientFlow(implicit materializer: Materializer,
                 ec: ExecutionContext): Flow[Message, Message, UniqueKillSwitch] = {
    Flow[Message]
      .flatMapMerge(
        1, {
          case message: TextMessage =>
            Source.fromFuture(
              message.textStream.runWith(Sink.reduce[String](_ + _)).flatMap { text =>
                val request = text.parseJson.convertTo[ProxyRequest]
                handleRequest(request).map { response =>
                  TextMessage(response.toJson.compactPrint)
                }
              }
            )
          case message: BinaryMessage =>
            message.dataStream.runWith(Sink.ignore)
            Source.empty
        }
      )
      .viaMat(KillSwitches.single)(Keep.right)
  }

  def handleRequest(proxyRequest: ProxyRequest): Future[ProxyResponse] =
    handler.applyOrElse(
      proxyRequest,
      (_: ProxyRequest) =>
        Future.successful(
          ProxyResponse(proxyRequest.id, 500, Seq.empty, ByteString("Not implemented"))))
}
