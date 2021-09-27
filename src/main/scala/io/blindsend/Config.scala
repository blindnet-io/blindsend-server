package io.blindsend

import pureconfig.*

case class GcpParams(project: String, bucket: String)

case class DbParams(
    host: String,
    port: Short,
    user: String,
    dbName: String,
    password: String
)

case class Config(
    storage: GcpParams,
    db: DbParams
)

object Config:
  val confReader = new ConfigReader[Config]:
    def from(cur: ConfigCursor) =
      for
        project <- cur.fluent.at("storage").at("project").asString
        bucket  <- cur.fluent.at("storage").at("bucket").asString
        dbHost  <- cur.fluent.at("db").at("host").asString
        dbPort  <- cur.fluent.at("db").at("port").asShort
        dbUser  <- cur.fluent.at("db").at("user").asString
        dbName  <- cur.fluent.at("db").at("database").asString
        dbPass  <- cur.fluent.at("db").at("password").asString
      yield Config(
        GcpParams(project, bucket),
        DbParams(dbHost, dbPort, dbUser, dbName, dbPass)
      )
