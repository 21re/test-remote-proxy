package testproxy.client

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches, Materializer, UniqueKillSwitch}
import akka.util.ByteString
import testproxy.api._
import spray.json._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

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

  private val bindPromise = Promise[Int]()

  def bind: Future[Int] = bindPromise.future

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
            Source.fromFutureSource(
              message.textStream.runWith(Sink.reduce[String](_ + _)).flatMap { text =>
                val message = text.parseJson.convertTo[ProxyMessage]
                handleMessage(message).map {
                  case Some(message) =>
                    Source(TextMessage(message.toJson.compactPrint) :: Nil)
                  case None => Source.empty
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

  def handleMessage(message: ProxyMessage): Future[Option[ProxyMessage]] = message match {
    case request: ProxyRequest =>
      handler
        .applyOrElse(request,
                     (_: ProxyRequest) =>
                       Future.successful(
                         ProxyResponse(request.id, 500, Seq.empty, ByteString("Not implemented"))))
        .map(Some(_))
    case bind: ProxyBind =>
      bindPromise.success(bind.port)
      Future.successful(None)
    case _ =>
      Future.successful(None)
  }
}
