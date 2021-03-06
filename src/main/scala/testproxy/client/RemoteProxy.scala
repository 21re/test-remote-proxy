package testproxy.client

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches, Materializer, UniqueKillSwitch}
import akka.util.ByteString
import play.api.libs.json.Json
import testproxy.api._

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

abstract class RemoteProxy(proxyEndpoint: String) {
  implicit val system: ActorSystem        = ActorSystem("test-proxy-client")
  implicit def ec: ExecutionContext       = system.dispatcher
  implicit val materializer: Materializer = ActorMaterializer()

  def handler: PartialFunction[ProxyRequest, Future[ProxyResponse]]

  val (upgradeResponse, killSwitches) =
    Http().singleWebSocketRequest(WebSocketRequest(proxyEndpoint), clientFlow)

  val connected: Future[Done.type] = upgradeResponse.map { upgrade =>
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
      .flatMapConcat {
        case message: TextMessage =>
          Source.fromFutureSource(
            message.textStream.runWith(Sink.reduce[String](_ + _)).flatMap { text =>
              val message = Json.parse(text).as[ProxyMessage]
              handleMessage(message).map {
                case Some(response) =>
                  Source(TextMessage(Json.stringify(Json.toJson(response))) :: Nil)
                case None => Source.empty
              }
            }
          )
        case message: BinaryMessage =>
          message.dataStream.runWith(Sink.ignore)
          Source.empty
      }
      .viaMat(KillSwitches.single)(Keep.right)
      .keepAlive(30.seconds, () => TextMessage(Json.stringify(Json.toJson(ProxyPing))))
  }

  def handleMessage(message: ProxyMessage): Future[Option[ProxyMessage]] = message match {
    case request: ProxyRequest =>
      handler
        .applyOrElse(request,
                     (_: ProxyRequest) =>
                       Future.successful(
                         ProxyResponse(request.id,
                                       500,
                                       Seq.empty,
                                       "text/plain",
                                       ByteString("Not implemented"))))
        .map(Some(_))
    case bind: ProxyBind =>
      bindPromise.success(bind.port)
      Future.successful(None)
    case ProxyPong =>
      system.log.info("Got pong")
      Future.successful(None)
    case _ =>
      Future.successful(None)
  }
}
