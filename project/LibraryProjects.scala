import Dependencies._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

object LibraryProjects {
  private[this] def libraryProject[T <: Project](p: T) = {
    p.settings(Common.settings: _*).disablePlugins(AssemblyPlugin)
  }

  private[this] lazy val `projectile-lib-core` = crossProject(JSPlatform, JVMPlatform).withoutSuffixFor(JVMPlatform).crossType(CrossType.Pure).in(
    file("libraries/projectile-lib-core")
  ).settings(Common.settings: _*).settings(
    description := "Classes and utilities shared between Scala and Scala.js",
    libraryDependencies ++= {
      val enumeratum = "com.beachape" %%% "enumeratum-circe" % Serialization.enumeratumCirceVersion
      val boopickle = "io.suzaku" %%% "boopickle" % Serialization.booPickleVersion
      Serialization.projects.map(c => "io.circe" %%% c % Serialization.version) :+ enumeratum :+ boopickle
    },
    (sourceGenerators in Compile) += ProjectVersion.writeConfig(Common.projectId, Common.projectName, Common.projectPort, "com.kyleu.projectile.util").taskValue
  ).jsSettings(libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3").disablePlugins(AssemblyPlugin)

  lazy val `projectile-lib-core-jvm` = `projectile-lib-core`.jvm.withId("projectile-lib-core")
  lazy val `projectile-lib-core-js` = `projectile-lib-core`.js.withId("projectile-lib-core-js")

  lazy val `projectile-lib-scala` = libraryProject(project in file("libraries/projectile-lib-scala")).settings(
    description := "Common classes relating to core models and utilities",
    libraryDependencies ++= Logging.all :+ Utils.commonsCodec,
  ).dependsOn(`projectile-lib-core-jvm`)

  lazy val `projectile-lib-tracing` = libraryProject(project in file("libraries/projectile-lib-tracing")).settings(
    description := "Common OpenTracing classes used by code generated from Projectile",
    libraryDependencies ++= Seq(
      Metrics.micrometerCore, Metrics.micrometerStatsd, Metrics.micrometerPrometheus,
      Tracing.datadogTracing, Tracing.jaegerCore, Tracing.jaegerThrift, Tracing.jaegerMetrics,
      Utils.javaxInject, Utils.typesafeConfig
    ) ++ Compiler.all,
    scalacOptions ++= Common.silencerOptions(baseDirectory.value.getCanonicalPath, messageFilters = Seq(".*Nullable.*"))
  ).dependsOn(`projectile-lib-scala`)

  lazy val `projectile-lib-jdbc` = libraryProject(project in file("libraries/projectile-lib-jdbc")).settings(
    description := "Common database classes used by code generated from Projectile",
    libraryDependencies ++= Seq(Database.postgres, Database.hikariCp, Database.flyway, Utils.commonsCodec, Utils.typesafeConfig)
  ).dependsOn(`projectile-lib-scala`)

  lazy val `projectile-lib-doobie` = libraryProject(project in file("libraries/projectile-lib-doobie")).settings(
    description := "Common Doobie classes used by code generated from Projectile",
    libraryDependencies ++= Database.Doobie.all
  ).dependsOn(`projectile-lib-jdbc`)

  lazy val `projectile-lib-slick` = libraryProject(project in file("libraries/projectile-lib-slick")).settings(
    description := "Common Slick classes used by code generated from Projectile",
    libraryDependencies ++= Database.Slick.all
  ).dependsOn(`projectile-lib-jdbc`)

  lazy val `projectile-lib-thrift` = libraryProject(project in file("libraries/projectile-lib-thrift")).settings(
    description := "Common Thrift classes used by code generated from Projectile",
    libraryDependencies ++= Thrift.all
  ).dependsOn(`projectile-lib-tracing`)

  lazy val `projectile-lib-service` = libraryProject(project in file("libraries/projectile-lib-service")).settings(
    description := "Common service classes used by code generated from Projectile",
    libraryDependencies ++= Seq(Utils.csv, Utils.javaxInject, Utils.scalaGuice)
  ).dependsOn(`projectile-lib-jdbc`, `projectile-lib-tracing`)

  lazy val `projectile-lib-graphql` = libraryProject(project in file("libraries/projectile-lib-graphql")).settings(
    description := "Common GraphQL classes used by code generated from Projectile",
    libraryDependencies ++= Seq(GraphQL.circe, GraphQL.sangria)
  ).dependsOn(`projectile-lib-service`)

  lazy val `projectile-lib-scalajs` = libraryProject(project in file("libraries/projectile-lib-scalajs")).settings(
    description := "Common Scala.js classes used by code generated from Projectile",
    libraryDependencies ++= {
      import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
      val jQuery = "be.doeraene" %%% "scalajs-jquery" % "0.9.5"
      val javaTime = "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3"
      val jsDom = "org.scala-js" %%% "scalajs-dom" % "0.9.7"
      Seq(jQuery, javaTime, jsDom)
    }
  ).dependsOn(`projectile-lib-core-js`).enablePlugins(org.scalajs.sbtplugin.ScalaJSPlugin, webscalajs.ScalaJSWeb)

  lazy val `projectile-lib-admin` = libraryProject(project in file("libraries/projectile-lib-admin")).settings(
    description := "A full-featured admin web app with a lovely UI",
    libraryDependencies ++= Authentication.all ++ WebJars.all ++ Seq(
      Play.cache, Play.filters, Play.guice, Play.json, Play.mailer, Play.twirl, Play.ws, Utils.betterFiles, Utils.commonsLang, Utils.reftree
    ) ++ Compiler.all,
    scalacOptions ++= Common.silencerOptions(baseDirectory.value.getCanonicalPath, pathFilters = Seq(".*html", ".*routes"))
  ).enablePlugins(play.sbt.PlayScala).dependsOn(`projectile-lib-graphql`, `projectile-lib-service`)

  lazy val all = Seq(
    `projectile-lib-core-jvm`, `projectile-lib-core-js`,
    `projectile-lib-scala`, `projectile-lib-tracing`, `projectile-lib-thrift`,
    `projectile-lib-jdbc`, `projectile-lib-doobie`, `projectile-lib-slick`,
    `projectile-lib-service`, `projectile-lib-graphql`, `projectile-lib-scalajs`,
    `projectile-lib-admin`
  )

  lazy val allReferences = all.map(_.project)
}
