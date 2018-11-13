package testproxy.api

import java.util.Base64

import akka.util.ByteString
import play.api.libs.json._

sealed trait ProxyMessage

case class ProxyRequest(id: Long,
                        method: String,
                        path: String,
                        headers: Seq[Header],
                        contentType: String,
                        body: ByteString)
    extends ProxyMessage

case class ProxyResponse(id: Long,
                         status: Int,
                         headers: Seq[Header],
                         contentType: String,
                         body: ByteString)
    extends ProxyMessage

case class ProxyBind(port: Int) extends ProxyMessage

case object ProxyUnbind extends ProxyMessage

case object ProxyPing extends ProxyMessage

case object ProxyPong extends ProxyMessage

object ProxyMessage {
  implicit object ByteStringFormat extends Format[ByteString] {
    override def reads(json: JsValue): JsResult[ByteString] = json match {
      case JsString(b) => JsSuccess(ByteString(Base64.getDecoder.decode(b)))
      case _           => JsError("Expected string")
    }

    override def writes(o: ByteString): JsValue =
      JsString(Base64.getEncoder.encodeToString(o.toArray))
  }

  private val proxyRequestFormat: OFormat[ProxyRequest]   = Json.format[ProxyRequest]
  private val proxyResponseFormat: OFormat[ProxyResponse] = Json.format[ProxyResponse]
  private val proxyBindFormat: OFormat[ProxyBind]         = Json.format[ProxyBind]

  implicit val jsonWrites: OWrites[ProxyMessage] = OWrites[ProxyMessage] {
    case request: ProxyRequest   => Json.obj("request"  -> proxyRequestFormat.writes(request))
    case response: ProxyResponse => Json.obj("response" -> proxyResponseFormat.writes(response))
    case bind: ProxyBind         => Json.obj("bind"     -> proxyBindFormat.writes(bind))
    case ProxyUnbind             => Json.obj("unbind"   -> Json.obj())
    case ProxyPing               => Json.obj("ping"     -> Json.obj())
    case ProxyPong               => Json.obj("pong"     -> Json.obj())
  }

  implicit val jsonReads: Reads[ProxyMessage] = Reads[ProxyMessage] {
    case JsObject(fields) if fields.nonEmpty =>
      fields.head match {
        case ("request", inner)  => proxyRequestFormat.reads(inner)
        case ("response", inner) => proxyResponseFormat.reads(inner)
        case ("bind", inner)     => proxyBindFormat.reads(inner)
        case ("unbind", _)       => JsSuccess(ProxyUnbind)
        case ("ping", _)         => JsSuccess(ProxyPing)
        case ("pong", _)         => JsSuccess(ProxyPong)
        case _                   => JsError("Invalid proxy mession")
      }
    case _ => JsError("Invalid proxy mession")
  }
}
