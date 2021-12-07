package io.blindsend

import java.security.*

import scala.concurrent.duration.*
import scala.concurrent.{ Await, ExecutionContext }
import scala.language.postfixOps

import cats.effect.{ IO, * }
import cats.implicits.*
import io.blindsend.links.PgLinkRepository
import io.blindsend.storage.GcpStorage
import io.blindsend.{ Config, Server }
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import pureconfig.*
import skunk.Session

object Main extends IOApp:
  val logger: org.log4s.Logger = org.log4s.getLogger

  val app =
    for
      _ <- Resource.pure[IO, Unit](())
      _ = logger.info(s"Starting app")

      config <- Resource.eval(
        IO.fromEither(
          ConfigSource
            .file("secrets/application.conf")
            .load[Config](Config.confReader)
            .leftMap(e =>
              new Throwable(s"Error reading config: ${e.prettyPrint()}")
            )
        )
      )

      session    <- PgLinkRepository.pgSession(config.db)
      pgLinkRepo <- PgLinkRepository.init(session)
      fileStorage = GcpStorage.init(
        config.storage.project,
        config.storage.bucket
      )

      apiVersion =
        buildinfo.BuildInfo.version
          .split("\\.")
          .take(2)
          .mkString(".")

      server <- BlazeServerBuilder[IO](ExecutionContext.global)
        .bindHttp(9000, "0.0.0.0")
        .withHttpApp(
          Router(
            s"v$apiVersion" -> CORS(Server.service(pgLinkRepo, fileStorage))
          ).orNotFound
        )
        .withResponseHeaderTimeout(2 minutes)
        .withIdleTimeout(5 minutes)
        .resource
    yield server

  def run(args: List[String]): IO[ExitCode] =
    app.use(_ => IO.never).as(ExitCode.Success)
