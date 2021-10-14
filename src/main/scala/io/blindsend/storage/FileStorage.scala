package io.blindsend.storage

trait FileStorage:

  def getSignedUploadLink(fileId: String, customTime: String): String

  def getSignedInitUploadLink(fileId: String, customTime: String): String

  def getSignedDownloadLink(fileId: String): String

end FileStorage
