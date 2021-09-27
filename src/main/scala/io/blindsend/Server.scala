package io.blindsend

import java.security.{ MessageDigest, SecureRandom }
import java.time.{ Instant, LocalDateTime, ZoneId }

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

  val getDatetime = Clock[IO].realTime.map(curTimeMillis =>
    Instant
      .ofEpochMilli(curTimeMillis.length)
      .atZone(ZoneId.systemDefault())
      .toLocalDateTime()
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

            datetime <- getDatetime
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
            req <- r.as[ReqStoreMetadata]
            links = req.files.map(f =>
              if f.fullUpload then
                UploadLink(f.id, fileStorage.getSignedUploadLink(f.id))
              else UploadLink(f.id, fileStorage.getSignedInitUploadLink(f.id))
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

    val sendRoutes =
      HttpRoutes.of[IO] { case r @ POST -> Root / "init-store-metadata" =>
        for
          req <- r.as[ReqSendInitStoreMetadata]

          datetime <- getDatetime
          linkId   <- randomId(16)

          links = req.files.map(f =>
            if f.fullUpload then
              UploadLink(f.id, fileStorage.getSignedUploadLink(f.id))
            else UploadLink(f.id, fileStorage.getSignedInitUploadLink(f.id))
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
          resp <- Ok(RespSendInitStoreMetadata(linkId, links).asJson)
        yield resp
      }

    val commonRoutes =
      HttpRoutes.of[IO] {
        case GET -> Root / "link-status" / linkId =>
          for
            status <- linkRepo.getLinkStatus(linkId)
            resp   <-
              if status.workflow == "r" then
                Ok(RespStatusRequest(status.stage.toString, status.pk).asJson)
              else Ok(RespStatusSend().asJson)
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
      "/send"    -> sendRoutes,
      "/"        -> commonRoutes
    )
  end service

end Server
