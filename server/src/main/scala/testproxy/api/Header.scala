package testproxy.api
import play.api.libs.json.{Json, OFormat}

case class Header(name: String, value: String)

object Header {
  implicit val jsonFormat: OFormat[Header] = Json.format[Header]
}
