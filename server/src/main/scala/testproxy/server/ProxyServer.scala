package testproxy.server

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import play.api.libs.json.Json
import testproxy.api.ProxyMessage

import scala.concurrent.ExecutionContext

class ProxyServer(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext) {
  val proxyActor: ActorRef = system.actorOf(ProxyActor.props)
  val responseSink: Sink[Message, Any] = Sink
    .foreach[Message] {
      case message: TextMessage =>
        message.textStream.runWith(Sink.reduce[String](_ + _)).foreach { text =>
          proxyActor ! Json.parse(text).as[ProxyMessage]
        }
      case message: BinaryMessage =>
        message.dataStream.runWith(Sink.ignore)
    }
    .mapMaterializedValue { closed =>
      closed.foreach { _ =>
        proxyActor ! ProxyActor.Disconnect
      }
    }

  val requestSource: Source[Message, Any] = Source
    .queue[ProxyMessage](100, OverflowStrategy.backpressure)
    .map { request =>
      TextMessage(Json.stringify(Json.toJson(request)))
    }
    .mapMaterializedValue { queue =>
      proxyActor ! ProxyActor.ConnectSource(queue)
      queue.watchCompletion().foreach { _ =>
        proxyActor ! ProxyActor.Disconnect
      }
    }

  val listenFlow: Flow[Message, Message, NotUsed] =
    Flow.fromSinkAndSource(responseSink, requestSource)
}
