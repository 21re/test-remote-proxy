package testproxy.api

import akka.util.ByteString

case class ProxyResponse(id: Long, status: Int, headers: Seq[Header], body: ByteString)
