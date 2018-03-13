package testproxy.server

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import spray.json._
import testproxy.api.{JsonSupport, ProxyRequest, ProxyResponse}

import scala.concurrent.ExecutionContext

class ProxyServer(
    port: Int)(implicit system: ActorSystem, materializer: Materializer, ec: ExecutionContext)
    extends JsonSupport {
  val proxyActor: ActorRef = system.actorOf(ProxyActor.props(port))
  val responseSink: Sink[Message, Any] = Sink
    .foreach[Message] {
      case message: TextMessage.Strict =>
        message.textStream.runWith(Sink.reduce[String](_ + _)).foreach { text =>
          proxyActor ! text.parseJson.convertTo[ProxyResponse]
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
    .queue[ProxyRequest](100, OverflowStrategy.backpressure)
    .map { request =>
      TextMessage(request.toJson.compactPrint)
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
