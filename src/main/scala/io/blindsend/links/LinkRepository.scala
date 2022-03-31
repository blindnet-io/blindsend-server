package io.blindsend.links

import java.time.LocalDateTime

import cats.effect.IO

case class LinkStatus(
    workflow: String,
    stage: Short,
    pk: String
)

case class Metadata(
    encryptedMetadata: String,
    seedHash: String,
    senderPk: String,
    passwordless: Boolean,
    salt: String,
    wrappedRequesterSk: String,
    nFiles: Short
)

trait LinkRepository:
  def getLinkStatus(id: String): IO[Option[LinkStatus]]

  def initLink(
      id: String,
      workflow: String,
      salt: Option[String],
      wrappedSk: Option[String],
      pk: Option[String],
      passwordless: Boolean,
      date: LocalDateTime
  ): IO[Unit]

  def storeMetadata(
      linkId: String,
      encryptedMetadata: String,
      files: List[String],
      seedHash: String,
      publicKey: String
  ): IO[Unit]

  def storeMetadata(
      linkId: String,
      encryptedMetadata: String,
      files: List[String],
      seedHash: String
  ): IO[Unit]

  def storeLinkFinishedUploading(linkId: String): IO[Unit]

  def getMetadata(linkId: String): IO[Metadata]

end LinkRepository
