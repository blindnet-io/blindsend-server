package io.blindsend.links

import java.time.LocalDateTime
import java.util.UUID

import cats.*
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.*
import io.blindsend.DbParams
import natchez.Trace.Implicits.noop
import skunk.*
import skunk.codec.all.*
import skunk.codec.numeric
import skunk.data.Arr
import skunk.implicits.*

object PgLinkRepository:

  def pgSession(dbParams: DbParams): Resource[IO, Session[IO]] =
    Session.single(
      host = dbParams.host,
      port = dbParams.port,
      user = dbParams.user,
      database = dbParams.dbName,
      password = Some(dbParams.password)
    )

  val statusQuery: Query[String, LinkStatus] =
    sql"""
      SELECT workflow, stage, requester_pk
      FROM links
      WHERE id = $varchar
     """
      .query(varchar ~ int2 ~ varchar)
      .gmap[LinkStatus]

  val encMetadataQuery: Query[String, Metadata] =
    sql"""
       SELECT enc_metadata, seed_hash, sender_pk, passwordless, salt, wrapped_requester_sk, num_files
       FROM links
       WHERE id = $varchar
    """
      .query(
        varchar ~ varchar ~ varchar ~ bool ~ varchar ~ varchar ~ int2
      )
      .map { case em ~ sh ~ pk ~ pl ~ s ~ ws ~ nf =>
        Metadata(em, sh, pk, pl, s, ws, nf)
      }

  val initPasswordlessCommand: Command[String ~ LocalDateTime] =
    sql"""
       INSERT INTO links (id, workflow, passwordless, date, stage, finished)
       VALUES ($varchar, 'r', true, $timestamp, 1, false)
     """.command

  val initCommand: Command[
    String ~ String ~ String ~ String ~ String ~ Boolean ~ LocalDateTime
  ] =
    sql"""
       INSERT INTO links (id, workflow, salt, wrapped_requester_sk, requester_pk, passwordless, date, stage, finished)
       VALUES ($varchar, $varchar, $varchar, $varchar, $varchar, $bool, $timestamp, 1, false)
     """.command

  val storeMetadataCommand
      : Command[String ~ String ~ String ~ Arr[String] ~ Short ~ String] =
    sql"""
       UPDATE links
       SET enc_metadata = $varchar, seed_hash = $varchar, sender_pk = $varchar, file_ids = ${_varchar}, num_files = $int2
       WHERE id = $varchar
     """.command

  val storeLinkFinCommand: Command[String] =
    sql"""
        UPDATE links
        SET finished = true, stage = 2
        WHERE id = $varchar
      """.command

  def init(session: Session[IO]): Resource[cats.effect.IO, LinkRepository] =
    for
      statusPQ           <- session.prepare(PgLinkRepository.statusQuery)
      encMetadataPQ      <- session.prepare(PgLinkRepository.encMetadataQuery)
      initPasswordlessPC <- session.prepare(
        PgLinkRepository.initPasswordlessCommand
      )
      initPC             <- session.prepare(PgLinkRepository.initCommand)
      storeMetadataPC    <- session.prepare(
        PgLinkRepository.storeMetadataCommand
      )
      storeLinkUpFinPC   <- session.prepare(
        PgLinkRepository.storeLinkFinCommand
      )
    yield new LinkRepository:
      def getLinkStatus(id: String): IO[LinkStatus] =
        statusPQ.unique(id)

      def initLink(
          id: String,
          workflow: String,
          salt: Option[String],
          wrappedSk: Option[String],
          pk: Option[String],
          passwordless: Boolean,
          date: LocalDateTime
      ): IO[Unit] =
        initPC
          .execute(
            id ~ workflow ~ salt.getOrElse("") ~ wrappedSk.getOrElse("") ~
              pk.getOrElse("") ~ passwordless ~ date
          )
          .void

      def storeMetadata(
          linkId: String,
          encryptedMetadata: String,
          files: List[String],
          seedHash: String,
          publicKey: String
      ): IO[Unit] =
        storeMetadataPC
          .execute(
            encryptedMetadata ~ seedHash ~ publicKey ~ Arr.fromFoldable(
              files
            ) ~ files.length.toShort ~ linkId
          )
          .void

      def storeMetadata(
          linkId: String,
          encryptedMetadata: String,
          files: List[String],
          seedHash: String
      ): IO[Unit] =
        storeMetadataPC
          .execute(
            encryptedMetadata ~ seedHash ~ seedHash ~ Arr.fromFoldable(
              files
            ) ~ files.length.toShort ~ linkId
          )
          .void

      def storeLinkFinishedUploading(linkId: String): IO[Unit] =
        storeLinkUpFinPC.execute(linkId).void

      def getMetadata(linkId: String): IO[Metadata] =
        encMetadataPQ.unique(linkId)

end PgLinkRepository
