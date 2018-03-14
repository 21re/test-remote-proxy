package testproxy.api

import java.util.Base64

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import spray.json._

trait JsonSupport extends SprayJsonSupport {

  import spray.json.DefaultJsonProtocol._

  implicit object ByteStringFormat extends JsonFormat[ByteString] {
    override def write(x: ByteString): JsValue =
      JsString(Base64.getEncoder.withoutPadding().encodeToString(x.toArray))

    override def read(value: JsValue): ByteString = value match {
      case JsString(b) => ByteString(Base64.getDecoder.decode(b))
      case x           => deserializationError("Expected JsString, but got " + x)
    }
  }

  implicit val headerFormat: RootJsonFormat[Header]      = jsonFormat2(Header)
  val proxyRequestFormat: RootJsonFormat[ProxyRequest]   = jsonFormat6(ProxyRequest)
  val proxyResponseFormat: RootJsonFormat[ProxyResponse] = jsonFormat5(ProxyResponse)
  val proxyBindFormat: RootJsonFormat[ProxyBind]         = jsonFormat1(ProxyBind)

  implicit object ProxyMessageFormat extends RootJsonFormat[ProxyMessage] {
    override def read(json: JsValue): ProxyMessage = json match {
      case JsObject(fields) if fields.nonEmpty =>
        fields.head match {
          case ("request", inner: JsValue) => inner.convertTo[ProxyRequest](proxyRequestFormat)
          case ("response", inner)         => inner.convertTo[ProxyResponse](proxyResponseFormat)
          case ("bind", inner)             => inner.convertTo[ProxyBind](proxyBindFormat)
          case ("unbind", _)               => ProxyUnbind
          case ("ping", _)                 => ProxyPing
          case ("pong", _)                 => ProxyPong
          case (t, _)                      => deserializationError("Unknown message type " + t)
        }
      case x => deserializationError("Expected nonEmpty JsObject, but got " + x)
    }

    override def write(obj: ProxyMessage): JsValue = obj match {
      case request: ProxyRequest   => JsObject("request"  -> request.toJson(proxyRequestFormat))
      case response: ProxyResponse => JsObject("response" -> response.toJson(proxyResponseFormat))
      case bind: ProxyBind         => JsObject("bind"     -> bind.toJson(proxyBindFormat))
      case ProxyPing               => JsObject("ping"     -> JsObject())
      case ProxyPong               => JsObject("pong"     -> JsObject())
      case ProxyUnbind             => JsObject("unbind"   -> JsObject())
    }
  }
}
