import java.nio.file.{Files, StandardCopyOption}
import java.nio

// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{crossProject, CrossType}

name := "FunctionalTetris"

version := "rolling_release"

scalaVersion := "2.12.4"

// GLITCH BONUS

lazy val fastNodeApp = taskKey[Unit]("build node app project structure from compiled sources")
lazy val fullNodeApp = taskKey[Unit]("build node app project structure from compiled sources")

lazy val root: Project = project
  .in(file("."))
  .settings(
    settings,
    fastNodeApp := {
      clean.value
      //(fastOptJS in Compile in common).value
      (fastOptJS in Compile in frontend).value
      (fastOptJS in Compile in backend).value
      def cp(p1: nio.file.Path, p2: nio.file.Path) =
        Files.copy(p1, p2, StandardCopyOption.REPLACE_EXISTING)
      def / = baseDirectory.value.toPath resolve (_: String)
//      cp(/("package.json"), /("app/package.json"))
      cp(/("backend/target/scala-2.12/backend-fastopt.js"), /("app/server.js"))
      cp(/("frontend/target/scala-2.12/frontend-fastopt.js"), /("app/public/client.js"))
      //cp(/("index.html"), /("app/views/index.html"))
    },
    fullNodeApp := {
      clean.value
      //(fullOptJS in Compile in common).value
      (fullOptJS in Compile in frontend).value
      (fullOptJS in Compile in backend).value
      def cp(p1: nio.file.Path, p2: nio.file.Path) =
        Files.copy(p1, p2, StandardCopyOption.REPLACE_EXISTING)
      def / = new File(baseDirectory.value, _: String).toPath
//      cp(/("package.json"), /("app/package.json"))
      cp(/("backend/target/scala-2.12/backend-opt.js"), /("app/server.js"))
      cp(/("frontend/target/scala-2.12/frontend-opt.js"), /("app/public/client.js"))
      //cp(/("index.html"), /("app/views/index.html"))
    }
  )
  .aggregate(
    common.js,
    frontend,
    backend
  )

lazy val common =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("common"))
  //.enablePlugins(ScalaJSPlugin)
  .settings(
    name := "common",
    settings
  ).jsSettings(
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "1.0.1",
      "org.typelevel" %%% "cats-effect" % "0.9"
    )
  )

lazy val commonJS = common.js
lazy val commonJVM = common.jvm

lazy val frontend: Project = project.in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "frontend",
    settings,
    scalaJSUseMainModuleInitializer := false,
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "1.0.1",
      "org.typelevel" %%% "cats-effect" % "0.9"
    ) ++ Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.2"
    )
  )
  .dependsOn(
    common.js
  )

lazy val backend: Project = project.in(file("backend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "backend",
    settings,
    scalaJSModuleKind := ModuleKind.CommonJSModule,
    scalaJSUseMainModuleInitializer := true,
    //mainClass in (Compile, run) := Some("org.dractec.ftetris.node.ScoreServer"),
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "1.0.1",
      "org.typelevel" %%% "cats-effect" % "0.9"
    ) ++ Seq(
      "io.scalajs" %%% "nodejs" % "0.4.2",
      "io.scalajs.npm" %%% "express" % "0.4.2",
      "io.scalajs.npm" %%% "body-parser" % "0.4.2"
    )
  )
  .dependsOn(
    common.js
  )

// SETTINGS

lazy val settings = commonSettings

lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-Ypartial-unification",
  "-language:reflectiveCalls",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-Xfatal-warnings",
  "-encoding",
  "utf8"
)

lazy val commonSettings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  )
)
