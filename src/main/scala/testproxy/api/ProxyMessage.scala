package testproxy.api

import akka.util.ByteString

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
