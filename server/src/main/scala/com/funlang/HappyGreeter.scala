package com.funlang

import cats.Applicative
import cats.syntax.applicative._
import com.funlang.hello._
import cats.effect._

class HappyGreeter extends Greeter[IO] {

  def SayHello(req: HelloRequest): IO[HelloResponse] =
    HelloResponse(s"Hello, ${req.name}!", happy = true).pure[IO]

}
