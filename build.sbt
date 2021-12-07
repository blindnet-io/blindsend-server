val CirceVersion  = "0.14.1"
val http4sVersion = "1.0.0-M23"

Global / excludeLintKeys += SettingKey("scalafixDependencies")

lazy val root = project
  .in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name              := "blindsend",
    organization      := "blindnet",
    version           := "1.0.0",
    scalaVersion      := "3.1.0",
    semanticdbEnabled := true,
    scalacOptions ++= Seq(
      "-language:postfixOps"
    ),
    scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0",
    resolvers ++= Seq(
      "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/",
      "JCenter" at "https://jcenter.bintray.com/"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel"         %% "cats-effect"          % "3.2.1",
      "io.circe"              %% "circe-core"           % CirceVersion,
      "io.circe"              %% "circe-parser"         % CirceVersion,
      "io.circe"              %% "circe-generic"        % CirceVersion,
      "org.http4s"            %% "http4s-dsl"           % http4sVersion,
      "org.http4s"            %% "http4s-server"        % http4sVersion,
      "org.http4s"            %% "http4s-circe"         % http4sVersion,
      "org.http4s"            %% "http4s-blaze-server"  % http4sVersion,
      "org.http4s"            %% "http4s-blaze-client"  % http4sVersion,
      "com.github.pureconfig" %% "pureconfig-core"      % "0.16.0",
      "org.tpolecat"          %% "skunk-core"           % "0.2.2",
      "org.bouncycastle"       % "bcprov-jdk15to18"     % "1.66",
      "ch.qos.logback"         % "logback-classic"      % "1.2.3",
      "org.codehaus.janino"    % "janino"               % "2.6.1",
      "com.google.cloud"       % "google-cloud-storage" % "2.1.0"
    ),
    assembly / mainClass             := Some("io.blindsend.Main"),
    assembly / assemblyJarName       := "blindsend.jar",
    // format: off
    assembly / assemblyMergeStrategy := {
      case x if Assembly.isConfigFile(x) =>
        MergeStrategy.concat
      case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
        MergeStrategy.rename
      case PathList("META-INF", xs @ _*) =>
        (xs map {_.toLowerCase}) match {
          case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
            MergeStrategy.discard
          case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
            MergeStrategy.discard
          case "plexus" :: xs =>
            MergeStrategy.discard
          case "services" :: xs =>
            MergeStrategy.filterDistinctLines
          case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
            MergeStrategy.filterDistinctLines
          case _ => MergeStrategy.deduplicate
        }
      case "module-info.class" => MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.deduplicate
    }
  // format: on
  )
