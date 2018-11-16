package testproxy.play

import akka.util.ByteString
import play.api.Application
import play.api.mvc.{Headers, Result}
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.HeaderNames
import testproxy.api.{Header, ProxyRequest, ProxyResponse}
import testproxy.client.RemoteProxy

import scala.concurrent.Future

class ForwardToApp(proxyEndpoint: String,
                   target: Application,
                   mangleRequest: FakeRequest[ByteString] => FakeRequest[ByteString],
                   mangleResult: Result => Result)
    extends RemoteProxy(proxyEndpoint) {

  override def handler: PartialFunction[ProxyRequest, Future[ProxyResponse]] = {
    case proxyRequest =>
      var headers = proxyRequest.headers.foldLeft(Headers.create()) { (headers, header) =>
        headers.add(header.name -> header.value)
      }
      if (proxyRequest.contentType.length > 0) {
        headers = headers.add(HeaderNames.CONTENT_TYPE -> proxyRequest.contentType)
      }
      val request = FakeRequest(
        proxyRequest.method,
        proxyRequest.path,
        headers,
        proxyRequest.body
      )

      Helpers.route(target, mangleRequest(request)) match {
        case None =>
          Future.successful(
            ProxyResponse(id = proxyRequest.id,
                          status = 404,
                          headers = Seq.empty,
                          contentType = "",
                          body = ByteString("")))
        case Some(futureResult) =>
          for {
            result <- futureResult
            body   <- result.body.consumeData
            effectiveResult = mangleResult(result)
          } yield {
            ProxyResponse(
              id = proxyRequest.id,
              status = effectiveResult.header.status.intValue(),
              headers = effectiveResult.header.headers.map {
                case (name, value) =>
                  Header(name, value)
              }.toSeq,
              contentType = effectiveResult.body.contentType
                .orElse(result.header.headers.get(HeaderNames.CONTENT_TYPE))
                .getOrElse(""),
              body = body
            )
          }
      }
  }
}
