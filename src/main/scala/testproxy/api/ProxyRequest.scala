package testproxy.api

import akka.util.ByteString

case class ProxyRequest(id: Long,
                        method: String,
                        path: String,
                        headers: Seq[Header],
                        body: ByteString)
