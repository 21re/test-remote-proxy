package testproxy.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.RootJsonFormat

trait JsonSupport extends SprayJsonSupport {
  import spray.json.DefaultJsonProtocol._

  implicit val proxyRequestFormat: RootJsonFormat[ProxyRequest] = jsonFormat3(ProxyRequest)
  implicit val proxyResponseFormat: RootJsonFormat[ProxyResponse] = jsonFormat1(ProxyResponse)
}
