package testproxy.api

import java.util.Base64

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.util.ByteString
import spray.json.{
  JsBoolean,
  JsFalse,
  JsString,
  JsTrue,
  JsValue,
  JsonFormat,
  RootJsonFormat,
  deserializationError
}

trait JsonSupport extends SprayJsonSupport {

  import spray.json.DefaultJsonProtocol._

  implicit object ByteStringFormat extends JsonFormat[ByteString] {
    def write(x: ByteString) =
      JsString(Base64.getEncoder.withoutPadding().encodeToString(x.toArray))

    def read(value: JsValue) = value match {
      case JsString(b) => ByteString(Base64.getDecoder.decode(b))
      case x           => deserializationError("Expected JsString, but got " + x)
    }
  }

  implicit val headerFormat: RootJsonFormat[Header]               = jsonFormat2(Header)
  implicit val proxyRequestFormat: RootJsonFormat[ProxyRequest]   = jsonFormat5(ProxyRequest)
  implicit val proxyResponseFormat: RootJsonFormat[ProxyResponse] = jsonFormat4(ProxyResponse)
}
