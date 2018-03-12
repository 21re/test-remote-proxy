package testproxy.server

import akka.actor.{ActorLogging, ActorRef, ActorSystem, FSM, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, SourceQueueWithComplete}
import akka.util.{ByteString, Timeout}
import testproxy.api.{Header, ProxyRequest, ProxyResponse}
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class ProxyActor(port: Int)(implicit materializer: Materializer)
    extends FSM[ProxyActor.State, ProxyActor.Data]
    with ActorLogging {
  import ProxyActor._

  implicit def system: ActorSystem  = context.system
  implicit def ec: ExecutionContext = context.dispatcher

  startWith(Initial, Uninitialized)

  when(Initial) {
    case Event(ConnectSource(requestQueue), Uninitialized) =>
      log.info(s"Launching new server on port $port")

      Http()
        .bindAndHandleAsync({ request =>
          (self ? request).map(_.asInstanceOf[HttpResponse])
        }, "0.0.0.0", port)
        .foreach { serverBinding =>
          self ! serverBinding
        }

      goto(Binding) using WithRequestQueue(requestQueue)
    case Event(request: HttpRequest, _) =>
      sender() ! HttpResponse(StatusCodes.BadGateway)
      stay()
  }

  when(Binding) {
    case Event(serverBinding: ServerBinding, WithRequestQueue(requestQueue)) =>
      log.info(s"Server listening on ${serverBinding.localAddress}")
      goto(Bound) using BoundServer(serverBinding, 0, requestQueue, Map.empty)
    case Event(request: HttpRequest, _) =>
      sender() ! HttpResponse(StatusCodes.BadGateway)
      stay()
  }

  when(Bound) {
    case Event(request: HttpRequest,
               BoundServer(serverBinding, requestCount, requestQueue, pendingRequests)) =>
      request.entity.dataBytes.runWith(Sink.reduce[ByteString](_ ++ _)).foreach { body =>
        requestQueue.offer(
          ProxyRequest(
            id = requestCount,
            method = request.method.value,
            path = request.uri.path
              .toString() + request.uri.rawQueryString.map("?" + _).getOrElse(""),
            headers = request.headers.map(header => Header(header.name(), header.value())),
            body = body
          ))
      }
      stay() using BoundServer(serverBinding,
                               requestCount + 1,
                               requestQueue,
                               pendingRequests + (requestCount -> sender()))
    case Event(response: ProxyResponse,
               BoundServer(serverBinding, requestCount, requestQueue, pendingRequests)) =>
      pendingRequests.get(response.id).foreach { ref =>
        ref ! HttpResponse(
          status = response.status,
          headers = response.headers
            .flatMap(header =>
              HttpHeader.parse(header.name, header.value) match {
                case HttpHeader.ParsingResult.Ok(header, _) => Seq(header)
                case _                                      => Seq.empty
            })
            .to[collection.immutable.Seq],
          entity = HttpEntity(response.body)
        )
      }
      stay() using BoundServer(serverBinding,
                               requestCount,
                               requestQueue,
                               pendingRequests - response.id)
    case Event(Disconnect,  BoundServer(serverBinding, _, _, _)) =>
      log.info(s"Unbinding server ${serverBinding.localAddress}")
      serverBinding.unbind().foreach { _ =>
        log.info(s"Server ${serverBinding.localAddress} unbound")
      }
      stop()
  }

  initialize()
}

object ProxyActor {
  sealed trait State

  case object Initial extends State

  case object Binding extends State

  case object Bound extends State

  sealed trait Data

  case object Uninitialized extends Data

  case class WithRequestQueue(requestQueue: SourceQueueWithComplete[ProxyRequest]) extends Data

  case class BoundServer(serverBinding: ServerBinding,
                         requestCount: Long,
                         requestQueue: SourceQueueWithComplete[ProxyRequest],
                         pendingRequests: Map[Long, ActorRef])
      extends Data

  case class ConnectSource(requestQueue: SourceQueueWithComplete[ProxyRequest])

  case object Disconnect

  def props(port: Int)(implicit materializer: Materializer) = Props(new ProxyActor(port))

  implicit val timeout: Timeout = Timeout(1.minute)
}
