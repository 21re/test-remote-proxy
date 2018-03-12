package testproxy.server

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}

trait ListenFlow {
  implicit def materializer : Materializer

  def listenFlow: Flow[Message, Message, Any] = Flow[Message].flatMapMerge(1, {
    case message: TextMessage =>
      Source(TextMessage("hubba") :: Nil)
    case message: BinaryMessage =>
      message.dataStream.runWith(Sink.ignore)
      Source(Nil)
  })
}
