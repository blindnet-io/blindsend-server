package io.blindsend.storage

trait FileStorage:

  def getSignedUploadLink(fileId: String): String

  def getSignedInitUploadLink(fileId: String): String

  def getSignedDownloadLink(fileId: String): String

end FileStorage
