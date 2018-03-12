package testproxy.server

import akka.pattern.ask
import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.Timeout
import spray.json._
import testproxy.api.{JsonSupport, ProxyRequest, ProxyResponse}
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext

class ProxyServer(port :Int)(implicit system: ActorSystem, materializer: Materializer, ec : ExecutionContext) extends JsonSupport {
  val proxyActor: ActorRef = system.actorOf(Props[ProxyActor])
  val responseSink = Sink.foreach[Message] {
    case message : TextMessage =>
      message.textStream.runWith(Sink.reduce[String](_ + _)).foreach { text =>
        proxyActor ! text.parseJson.convertTo[ProxyResponse]
      }
    case message : BinaryMessage =>
      message.dataStream.runWith(Sink.ignore)
  }
  val requestSource: Source[Message, Any] = Source.queue[ProxyRequest](100, OverflowStrategy.backpressure).map { request =>
    TextMessage(request.toJson.compactPrint)
  }.mapMaterializedValue { queue =>
    proxyActor ! ProxyActor.ConnectSource(queue)

    implicit val timeout : Timeout = Timeout(1.minute)
    println("Starting client server")

    Http().bindAndHandleAsync({ request =>
      (proxyActor ? request).map(_.asInstanceOf[HttpResponse])
    }, "0.0.0.0", port)
  }

  val listenFlow: Flow[Message, Message, NotUsed] = Flow.fromSinkAndSource(responseSink, requestSource)
}
