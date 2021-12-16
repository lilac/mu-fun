package com.funlang

import cats.effect._
import com.funlang.hello.Greeter
import higherkindness.mu.rpc.server._
import io.grpc.protobuf.services.ProtoReflectionService

object Server extends IOApp {

  implicit val greeter: Greeter[IO] = new HappyGreeter[IO]

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
    val reflection = ProtoReflectionService.newInstance().bindService()
    val resource = for {
      serviceDef <- Greeter.bindService[IO]
      services = List(
        AddService(reflection),
        AddService(serviceDef)
      )
      server <- Resource.eval(GrpcServer.default[IO](12345, services))
      _ <- GrpcServer.serverResource[IO](server)
    } yield ExitCode.Success
    resource.useForever
  }
}
