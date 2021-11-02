package io.blindsend

import cats.effect.IO
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*

case class Constraints(numOfFiles: Int, totalSize: String, singleSize: String)
given Encoder[Constraints] =
  Encoder.forProduct3("num_of_files", "total_size", "single_size")(r =>
    (r.numOfFiles, r.totalSize, r.singleSize)
  )

case class RespStatusShare()
case class RespStatusRequest(stage: String, pk: String)

given Encoder[RespStatusShare] =
  new Encoder[RespStatusShare]:
    def apply(a: RespStatusShare): Json =
      Json.obj(("workflow", Json.fromString("s")))

given Encoder[RespStatusRequest] =
  new Encoder[RespStatusRequest]:
    def apply(a: RespStatusRequest): Json =
      Json.obj(
        ("workflow", Json.fromString("r")),
        ("stage", Json.fromString(a.stage)),
        ("key", Json.fromString(a.pk))
      )

case class ReqGetLink(
    salt: Option[String],
    wrappedSk: Option[String],
    pk: String,
    passwordless: Boolean
)

given Decoder[ReqGetLink] =
  Decoder.forProduct4("salt", "wrapped_sk", "pk", "passwordless")(
    ReqGetLink.apply
  )

case class RespGetLink(linkId: String)
given Encoder[RespGetLink] =
  Encoder.forProduct1("link_id")(r => r.linkId)

case class FileEncData(id: String, fullUpload: Boolean)
given Decoder[FileEncData] =
  Decoder.forProduct2("id", "full_upload")(FileEncData.apply)

case class ReqStoreMetadata(
    seedHash: String,
    publicKey: String,
    encryptedMetadata: String,
    files: List[FileEncData],
    linkId: String
)

given Decoder[ReqStoreMetadata] =
  Decoder.forProduct5(
    "seed_hash",
    "public_key",
    "encrypted_metadata",
    "files",
    "link_id"
  )(ReqStoreMetadata.apply)

case class UploadLink(id: String, link: String, customTime: String)
given Encoder[UploadLink] =
  Encoder.forProduct3("id", "link", "custom_time_header")(r =>
    (r.id, r.link, r.customTime)
  )

case class RespStoreMetadata(uploadLinks: List[UploadLink])

given Encoder[RespStoreMetadata] =
  Encoder.forProduct1("upload_links")(r => r.uploadLinks)

case class FileUploadData(uploadId: String, fileId: String, numParts: Int)
given Decoder[FileUploadData] =
  Decoder.forProduct3("upload_id", "file_id", "parts")(FileUploadData.apply)

case class ReqSignUploadPartLinks(files: List[FileUploadData])
given Decoder[ReqSignUploadPartLinks] =
  Decoder.forProduct1("files")(ReqSignUploadPartLinks.apply)

case class SignedFileUploadLinks(
    id: String,
    partLinks: List[String],
    finLink: String
)

given Encoder[SignedFileUploadLinks] =
  Encoder.forProduct3("id", "part_links", "fin_link")(r =>
    (r.id, r.partLinks, r.finLink)
  )

case class RespSignUploadPartLinks(files: List[SignedFileUploadLinks])
given Encoder[RespSignUploadPartLinks] =
  Encoder.forProduct1("files")(r => r.files)

case class ReqFinishUploadFile(fileId: String)
given Decoder[ReqFinishUploadFile] =
  Decoder.forProduct1("file_id")(ReqFinishUploadFile.apply)

case class ReqFinishUploadLink(linkId: String)
given Decoder[ReqFinishUploadLink] =
  Decoder.forProduct1("link_id")(ReqFinishUploadLink.apply)

case class RespGetMetadata(
    encMetadata: String,
    seedHash: String,
    publicKey: String,
    passwordless: Boolean,
    salt: String,
    wrappedSk: String,
    numFiles: Short
)

given Encoder[RespGetMetadata] =
  Encoder.forProduct7(
    "enc_metadata",
    "seed_hash",
    "public_key",
    "passwordless",
    "salt",
    "wrapped_sk",
    "num_files"
  )(r =>
    (
      r.encMetadata,
      r.seedHash,
      r.publicKey,
      r.passwordless,
      r.salt,
      r.wrappedSk,
      r.numFiles
    )
  )

case class RespGetSignedDownloadLink(link: String)
given Encoder[RespGetSignedDownloadLink] =
  Encoder.forProduct1("link")(r => r.link)

case class ReqGetAllSignedDownloadLinks(fileIds: List[String])
given Decoder[ReqGetAllSignedDownloadLinks] =
  Decoder.forProduct1("file_ids")(ReqGetAllSignedDownloadLinks.apply)

case class SignedLinkId(fileId: String, link: String)
given Encoder[SignedLinkId] =
  Encoder.forProduct2("file_id", "link")(r => (r.fileId, r.link))

case class RespGetAllSignedDownloadLinks(links: List[SignedLinkId])
given Encoder[RespGetAllSignedDownloadLinks] =
  Encoder.forProduct1("links")(r => r.links)

case class ReqShareInitStoreMetadata(
    salt: String,
    passwordless: Boolean,
    seedHash: String,
    encryptedMetadata: String,
    files: List[FileEncData]
)

given Decoder[ReqShareInitStoreMetadata] =
  Decoder.forProduct5(
    "salt",
    "passwordless",
    "seed_hash",
    "encrypted_metadata",
    "files"
  )(ReqShareInitStoreMetadata.apply)

case class RespShareInitStoreMetadata(
    linkId: String,
    uploadLinks: List[UploadLink]
)

given Encoder[RespShareInitStoreMetadata] =
  Encoder.forProduct2("link_id", "upload_links")(r => (r.linkId, r.uploadLinks))

given EntityDecoder[IO, ReqGetLink] = jsonOf[IO, ReqGetLink]

given EntityDecoder[IO, ReqStoreMetadata]       = jsonOf[IO, ReqStoreMetadata]
given EntityDecoder[IO, ReqSignUploadPartLinks] =
  jsonOf[IO, ReqSignUploadPartLinks]

given EntityDecoder[IO, ReqFinishUploadFile] = jsonOf[IO, ReqFinishUploadFile]
given EntityDecoder[IO, ReqFinishUploadLink] = jsonOf[IO, ReqFinishUploadLink]

given EntityDecoder[IO, ReqGetAllSignedDownloadLinks] =
  jsonOf[IO, ReqGetAllSignedDownloadLinks]

given EntityDecoder[IO, ReqShareInitStoreMetadata] =
  jsonOf[IO, ReqShareInitStoreMetadata]
