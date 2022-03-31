package io.blindsend.links

import java.time.LocalDateTime
import java.util.UUID

import cats.*
import cats.effect.*
import cats.effect.kernel.Resource
import cats.implicits.*
import io.blindsend.DbParams

import doobie.*
import doobie.implicits.*
import doobie.postgres.*
import doobie.postgres.implicits.*
import doobie.hikari.HikariTransactor

object PgLinkRepository:

  def init(dbParams: DbParams): Resource[IO, LinkRepository] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        s"jdbc:postgresql://${dbParams.host}:${dbParams.port}/${dbParams.dbName}",
        dbParams.user,
        dbParams.password,
        ce
      )
    } yield new LinkRepository {
      def getLinkStatus(id: String): IO[Option[LinkStatus]] =
        sql"""
          SELECT workflow, stage, requester_pk
          FROM links
          WHERE id = $id
        """
          .query[LinkStatus]
          .option
          .transact(xa)

      def initLink(
          id: String,
          workflow: String,
          salt: Option[String],
          wrappedSk: Option[String],
          pk: Option[String],
          passwordless: Boolean,
          date: LocalDateTime
      ): IO[Unit] =
        sql"""
          INSERT INTO links (id, workflow, salt, wrapped_requester_sk, requester_pk, passwordless, date, stage, finished)
          VALUES 
          (
            $id, $workflow, ${salt.getOrElse("")}, ${wrappedSk.getOrElse("")},
            ${pk.getOrElse("")}, $passwordless, $date, 1, false
          )
        """.update.run.transact(xa).void

      def storeMetadata(
          linkId: String,
          encryptedMetadata: String,
          files: List[String],
          seedHash: String,
          publicKey: String
      ): IO[Unit] =
        sql"""
          UPDATE links
          SET enc_metadata = $encryptedMetadata, seed_hash = $seedHash, sender_pk = $publicKey, file_ids = $files, num_files = ${files.length.toShort}
          WHERE id = $linkId
        """.update.run.transact(xa).void

      def storeMetadata(
          linkId: String,
          encryptedMetadata: String,
          files: List[String],
          seedHash: String
      ): IO[Unit] =
        sql"""
          UPDATE links
          SET enc_metadata = $encryptedMetadata, seed_hash = $seedHash, sender_pk='', file_ids = $files, num_files = ${files.length.toShort}
          WHERE id = $linkId
        """.update.run.transact(xa).void

      def storeLinkFinishedUploading(linkId: String): IO[Unit] =
        sql"""
        UPDATE links
        SET finished = true, stage = 2
        WHERE id = $linkId
      """.update.run.transact(xa).void

      def getMetadata(linkId: String): IO[Metadata] =
        sql"""
          SELECT enc_metadata, seed_hash, sender_pk, passwordless, salt, wrapped_requester_sk, num_files
          FROM links
          WHERE id = $linkId
        """.query[Metadata].unique.transact(xa)

    }

end PgLinkRepository
