package testproxy.play

import akka.util.ByteString
import play.api.Application
import play.api.mvc.{Headers, Result}
import play.api.test.{FakeRequest, Helpers}
import testproxy.api.{Header, ProxyRequest, ProxyResponse}
import testproxy.client.RemoteProxy

import scala.concurrent.Future

class ForwardToApp(proxyEndpoint: String, target: Application) extends RemoteProxy(proxyEndpoint) {
  def mangleRequest: PartialFunction[FakeRequest[ByteString], FakeRequest[ByteString]] = {
    case request => request
  }

  def mangleResult: PartialFunction[Result, Result] = {
    case response => response
  }

  override def handler: PartialFunction[ProxyRequest, Future[ProxyResponse]] = {
    case proxyRequest => {
      val request = FakeRequest(
        proxyRequest.method,
        proxyRequest.path,
        proxyRequest.headers.foldLeft(Headers.create()) { (headers, header) =>
          headers.add(header.name -> header.value)
        },
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
              contentType = effectiveResult.body.contentType.getOrElse(""),
              body = body
            )
          }
      }
    }
  }
}
