package testproxy.client

import akka.Done
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete}

import scala.concurrent.Future

trait ClientFlow {
  val printSink: Sink[Message, Future[Done]] =
    Sink.foreach {
      case message: TextMessage.Strict =>
        println(message.text)
    }

  val helloSource: Source[Message, SourceQueueWithComplete[Message]] = Source.queue[Message](100, OverflowStrategy.backpressure)

  def clientFlow : Flow[Message, Message, Future[Done]] =   Flow.fromSinkAndSourceMat(printSink, helloSource)(Keep.left)
}
