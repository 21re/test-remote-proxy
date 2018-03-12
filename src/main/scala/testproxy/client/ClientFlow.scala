package testproxy.client

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{KillSwitches, Materializer, UniqueKillSwitch}
import akka.util.ByteString
import spray.json._
import testproxy.api.{JsonSupport, ProxyRequest, ProxyResponse}

import scala.concurrent.ExecutionContext

trait ClientFlow extends JsonSupport {

  def clientFlow(implicit materializer: Materializer,
                 ec: ExecutionContext): Flow[Message, Message, UniqueKillSwitch] = {
    Flow[Message].flatMapMerge(
      1, {
        case message: TextMessage =>
          Source.fromFuture(
            message.textStream.runWith(Sink.reduce[String](_ + _)).map { text =>
              val request = text.parseJson.convertTo[ProxyRequest]
              println(s"Request: $request")
              val response = ProxyResponse(request.id, 200, Seq.empty, ByteString("BlaBla"))
              TextMessage(response.toJson.compactPrint)
            }
          )
        case message: BinaryMessage =>
          message.dataStream.runWith(Sink.ignore)
          Source.empty
      }
    ).viaMat(KillSwitches.single)(Keep.right)
  }
}
