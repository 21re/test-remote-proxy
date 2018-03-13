package testproxy.client
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import testproxy.api.{Header, ProxyRequest, ProxyResponse}

import scala.concurrent.Future

class ForwardProxyProxy(proxyEndpoint: String, target: String) extends RemoteProxy(proxyEndpoint) {
  def mangleRequest: PartialFunction[HttpRequest, HttpRequest] = {
    case request => request
  }

  def mangleResponse: PartialFunction[HttpResponse, HttpResponse] = {
    case response => response
  }

  override def handler: PartialFunction[ProxyRequest, Future[ProxyResponse]] = {
    case proxyRequest =>
      val request = HttpRequest(
        method = HttpMethods
          .getForKeyCaseInsensitive(proxyRequest.method)
          .getOrElse(HttpMethod.custom(proxyRequest.method)),
        uri = Uri(target + proxyRequest.path),
        headers = proxyRequest.headers
          .flatMap(header =>
            HttpHeader.parse(header.name, header.value) match {
              case HttpHeader.ParsingResult.Ok(httpHeader, _) => Seq(httpHeader)
              case _                                          => Seq.empty
          })
          .to[collection.immutable.Seq],
        entity = HttpEntity(proxyRequest.body)
      )
      val effectiveRequest = mangleRequest.applyOrElse(request, identity[HttpRequest])
      Http()
        .singleRequest(effectiveRequest)
        .flatMap { response =>
          val effectiveResponse = mangleResponse.applyOrElse(response, identity[HttpResponse])
          effectiveResponse.entity.dataBytes.runWith(Sink.reduce[ByteString](_ ++ _)).map { body =>
            ProxyResponse(
              id = proxyRequest.id,
              status = effectiveResponse.status.intValue(),
              headers =
                effectiveResponse.headers.map(header => Header(header.name(), header.value())),
              body = body
            )
          }
        }
        .recover {
          case e => ProxyResponse(proxyRequest.id, 503, Seq.empty, ByteString(e.getMessage))
        }
  }
}
