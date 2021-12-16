package com.funlang

import cats.effect._
import com.funlang.hello.Greeter
import higherkindness.mu.rpc.server._
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.ServerServiceDefinition
import higherkindness.mu.rpc.ChannelForSocketAddress
import io.netty.channel.unix.DomainSocketAddress
import io.grpc.netty.NettyServerBuilder
import io.netty.channel.epoll.EpollServerDomainSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel
import io.netty.channel.kqueue.KQueueEventLoopGroup

object Server extends IOApp {

  implicit val greeter: Greeter[IO] = new HappyGreeter

  def linuxOS: Boolean =
    sys.props.get("os.name").exists(_.equalsIgnoreCase("linux"))

  def run(args: List[String]): IO[ExitCode] = {
    /*
     * From version 0.27.0, `Greeter.bindService` returns a `Resource[F, ServerServiceDefinition]`
     * instead of an `F[ServerServiceDefinition]`, so in previous versions this block would look like:
     *
     * for {
     *   serviceDef <- Greeter.bindService[IO]
     *   server <- GrpcServer.default[IO](12345, List(AddService(serviceDef)))
     *   _ <- GrpcServer.server[IO](server)
     * } yield ExitCode.Success
     */
    val reflection = ProtoReflectionService.newInstance()
    val address =
      new DomainSocketAddress(
        "/tmp/grpc.sock"
      ) // new DomainSocketAddress("/var/run/predictor.sock")
    var serverBuilder =
      if (linuxOS)
        NettyServerBuilder
          .forAddress(address)
          .channelType(classOf[EpollServerDomainSocketChannel])
          // .channelType(classOf[NioServerSocketChannel])
          // .bossEventLoopGroup(new NioEventLoopGroup)
          .bossEventLoopGroup(new EpollEventLoopGroup)
          .workerEventLoopGroup(new EpollEventLoopGroup)
      else
        NettyServerBuilder
          .forAddress(address)
          .channelType(classOf[KQueueServerDomainSocketChannel])
          .bossEventLoopGroup(new KQueueEventLoopGroup)
          .workerEventLoopGroup(new KQueueEventLoopGroup)
    serverBuilder = serverBuilder.addService(reflection)
    def makeServer(services: List[ServerServiceDefinition]) = for {
      _ <- IO(println("Start grpc server"))
    } yield {
      for (service <- services) {
        serverBuilder = serverBuilder.addService(service)
      }
      GrpcServer.fromServer[IO](serverBuilder.build())
    }

    val resource = for {
      serviceDef <- Greeter.bindService[IO]
      services = List(
        serviceDef
      )
      server <- Resource.eval(makeServer(services))
      _ <- GrpcServer.serverResource[IO](server)
    } yield ExitCode.Success
    resource.useForever
  }
}
