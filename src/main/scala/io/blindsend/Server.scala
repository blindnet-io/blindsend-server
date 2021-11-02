package io.blindsend

import java.security.{ MessageDigest, SecureRandom }
import java.time.format.DateTimeFormatter
import java.time.{ Instant, LocalDateTime, ZoneId, ZonedDateTime }
import java.util.concurrent.TimeUnit

import scala.concurrent.duration.{ Duration, FiniteDuration }

import cats.effect.*
import com.google.api.client.util.Base64
import io.blindsend.links.LinkRepository
import io.blindsend.storage.FileStorage
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.server.Router
import skunk.implicits.*
import skunk.~

object Server:

  val rng = new SecureRandom();

  def randomId(len: Byte) = IO {
    val bytes  = Array.fill[Byte](32)(0)
    rng.nextBytes(bytes)
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(bytes)
    Base64.encodeBase64URLSafeString(digest.digest().take(len))
  }

  def getDatetimeUTC(offset: FiniteDuration = Duration.Zero) =
    Clock[IO].realTime.map(curTimeMillis =>
      Instant
        .ofEpochMilli((curTimeMillis + offset).length)
        .atZone(ZoneId.of("UTC"))
    )

  def service(
      linkRepo: LinkRepository,
      fileStorage: FileStorage
  ) =
    val requestRoutes =
      HttpRoutes.of[IO] {
        case r @ POST -> Root / "get-link" =>
          for
            req <- r.as[ReqGetLink]

            datetime <- getDatetimeUTC().map(_.toLocalDateTime)
            linkId   <- randomId(16)

            _ <- linkRepo.initLink(
              linkId,
              "r",
              req.salt,
              req.wrappedSk,
              Some(req.pk),
              req.passwordless,
              datetime
            )

            resp <- Ok(RespGetLink(linkId).asJson)
          yield resp

        case r @ POST -> Root / "store-metadata" =>
          for
            req        <- r.as[ReqStoreMetadata]
            customTime <- getDatetimeUTC(FiniteDuration(168, TimeUnit.HOURS))
              .map(
                _.format(
                  DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
                )
              )

            links = req.files.map(f =>
              if f.fullUpload then
                UploadLink(
                  f.id,
                  fileStorage.getSignedUploadLink(f.id, customTime),
                  customTime
                )
              else
                UploadLink(
                  f.id,
                  fileStorage.getSignedInitUploadLink(f.id, customTime),
                  customTime
                )
            )
            _    <- linkRepo.storeMetadata(
              req.linkId,
              req.encryptedMetadata,
              req.files.map(_.id),
              req.seedHash,
              req.publicKey
            )
            resp <- Ok(RespStoreMetadata(links).asJson)
          yield resp
      }

    val shareRoutes =
      HttpRoutes.of[IO] { case r @ POST -> Root / "init-store-metadata" =>
        for
          req <- r.as[ReqShareInitStoreMetadata]

          datetime   <- getDatetimeUTC().map(_.toLocalDateTime)
          linkId     <- randomId(16)
          customTime <- getDatetimeUTC(FiniteDuration(168, TimeUnit.HOURS))
            .map(
              _.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
              )
            )

          links = req.files.map(f =>
            if f.fullUpload then
              UploadLink(
                f.id,
                fileStorage.getSignedUploadLink(f.id, customTime),
                customTime
              )
            else
              UploadLink(
                f.id,
                fileStorage.getSignedInitUploadLink(f.id, customTime),
                customTime
              )
          )

          _    <- linkRepo.initLink(
            linkId,
            "s",
            Some(req.salt),
            None,
            None,
            req.passwordless,
            datetime
          )
          _    <- linkRepo.storeMetadata(
            linkId,
            req.encryptedMetadata,
            req.files.map(_.id),
            req.seedHash
          )
          resp <- Ok(RespShareInitStoreMetadata(linkId, links).asJson)
        yield resp
      }

    val commonRoutes =
      HttpRoutes.of[IO] {
        case GET -> Root / "link-status" / linkId =>
          for
            status <- linkRepo.getLinkStatus(linkId)
            resp   <-
              status match
                case None                                   =>
                  NotFound()
                case Some(status) if status.workflow == "r" =>
                  Ok(RespStatusRequest(status.stage.toString, status.pk).asJson)
                case Some(status) if status.workflow == "s" =>
                  Ok(RespStatusShare().asJson)
                case _                                      =>
                  InternalServerError()
          yield resp

        case r @ POST -> Root / "finish-upload" =>
          for
            req  <- r.as[ReqFinishUploadLink]
            _    <- linkRepo.storeLinkFinishedUploading(req.linkId)
            resp <- Ok()
          yield resp

        case GET -> Root / "metadata" / linkId =>
          for
            m    <- linkRepo.getMetadata(linkId)
            resp <- Ok(
              RespGetMetadata(
                m.encryptedMetadata,
                m.seedHash,
                m.senderPk,
                m.passwordless,
                m.salt,
                m.wrappedRequesterSk,
                m.nFiles
              ).asJson
            )
          yield resp

        case GET -> Root / "signed-download-link" / fileId =>
          for
            resp <- Ok(
              RespGetSignedDownloadLink(
                fileStorage.getSignedDownloadLink(fileId)
              ).asJson
            )
          yield resp

        case r @ POST -> Root / "get-all-signed-download-links" =>
          for
            req <- r.as[ReqGetAllSignedDownloadLinks]
            links = req.fileIds.map(id =>
              SignedLinkId(id, fileStorage.getSignedDownloadLink(id))
            )
            resp <- Ok(RespGetAllSignedDownloadLinks(links).asJson)
          yield resp
      }

    Router(
      "/request" -> requestRoutes,
      "/share"   -> shareRoutes,
      "/"        -> commonRoutes
    )
  end service

end Server
