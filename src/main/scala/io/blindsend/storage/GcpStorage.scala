package io.blindsend.storage

import java.net.URL
import java.text.SimpleDateFormat
import java.time.{ Instant, LocalDateTime, ZoneId }
import java.util.concurrent.TimeUnit
import java.util.{ HashMap, Map, UUID }

import cats.effect.{ IO, * }
import cats.implicits.*
import com.google.cloud.storage.{
  BlobId,
  BlobInfo,
  HttpMethod,
  Storage,
  StorageException,
  StorageOptions
}
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import skunk.~

object GcpStorage:

  def init(project: String, bucket: String) =
    new FileStorage:
      val storage: Storage = StorageOptions
        .newBuilder()
        .setProjectId(project)
        .build()
        .getService()

      def getSignedUploadLink(fileId: String, customTime: String): String =
        val extensionHeaders = new HashMap[String, String]()
        extensionHeaders.put("x-goog-content-length-range", "0,5000000")
        extensionHeaders.put("x-goog-custom-time", customTime)

        storage
          .signUrl(
            BlobInfo.newBuilder(BlobId.of(bucket, fileId)).build(),
            2,
            TimeUnit.HOURS,
            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
            Storage.SignUrlOption.withExtHeaders(extensionHeaders),
            Storage.SignUrlOption.withV4Signature()
          )
          .toString
      end getSignedUploadLink

      def getSignedInitUploadLink(fileId: String, customTime: String): String =
        val extensionHeaders = new HashMap[String, String]()
        extensionHeaders.put("Content-Length", "0")
        extensionHeaders.put("x-goog-resumable", "start")
        extensionHeaders.put("x-goog-content-length-range", "0,2147483648")
        extensionHeaders.put("x-goog-custom-time", customTime)

        storage
          .signUrl(
            BlobInfo.newBuilder(BlobId.of(bucket, fileId)).build(),
            2,
            TimeUnit.HOURS,
            Storage.SignUrlOption.httpMethod(HttpMethod.POST),
            Storage.SignUrlOption.withExtHeaders(extensionHeaders),
            Storage.SignUrlOption.withV4Signature()
          )
          .toString
      end getSignedInitUploadLink

      def getSignedDownloadLink(fileId: String): String =
        val queryParams = new HashMap[String, String]()
        queryParams.put("alt", "media")

        storage
          .signUrl(
            BlobInfo.newBuilder(BlobId.of(bucket, fileId)).build(),
            2,
            TimeUnit.HOURS,
            Storage.SignUrlOption.withQueryParams(queryParams),
            Storage.SignUrlOption.withV4Signature()
          )
          .toString
      end getSignedDownloadLink

end GcpStorage
