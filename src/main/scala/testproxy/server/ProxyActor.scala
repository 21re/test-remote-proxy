package testproxy.server

import akka.actor.{Actor, ActorRef}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.SourceQueueWithComplete
import testproxy.api.ProxyRequest

import scala.collection.mutable

class ProxyActor extends Actor {
  import ProxyActor._

  var requestCount : Long = 0
  var proxyRequestSource : Option[SourceQueueWithComplete[ProxyRequest]] = None
  val pendingRequest : mutable.Map[Int, ActorRef] = mutable.HashMap.empty

  override def receive: Receive = {
    case ConnectSource(queue) =>
      proxyRequestSource = Some(queue)
    case request: HttpRequest =>
      proxyRequestSource match {
        case Some(queue) =>
          queue.offer(ProxyRequest(id = requestCount, method = request.method.value, uri = request.uri.toString()))
          requestCount += 1
        case None =>
          sender() ! HttpResponse(StatusCodes.BadGateway)
      }
  }
}

object ProxyActor {
  case class ConnectSource(proxyRequestSource : SourceQueueWithComplete[ProxyRequest])

  case object Disconnect
}