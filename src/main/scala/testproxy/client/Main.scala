package testproxy.client

object Main extends App {
  val remoteProxy = new RemoteProxy("ws://localhost:8888/v1/listen/8000")
}
