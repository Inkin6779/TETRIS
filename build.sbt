import java.nio.file.{StandardCopyOption, Files}
import java.nio

name := "FunctionalTetris"

version := "rolling_release"

scalaVersion := "2.12.4"

// GLITCH BONUS

lazy val fastGlitchApp = taskKey[Unit]("build glitch app project structure from compiled sources")
lazy val fullGlitchApp = taskKey[Unit]("build glitch app project structure from compiled sources")

lazy val root = project
  .in(file("."))
  .settings(
    settings,
    fastGlitchApp := {
      def cp(p1: nio.file.Path, p2: nio.file.Path) = {
        //Files.deleteIfExists(p2)
        Files.copy(p1, p2, StandardCopyOption.REPLACE_EXISTING)
      }
      def / = baseDirectory.value.toPath resolve (_: String)
      cp(/("package.json"), /("app/package.json"))
      cp(/("backend/target/scala-2.12/backend-fastopt.js"), /("app/server.js"))
      cp(/("frontend/target/scala-2.12/frontend-fastopt.js"), /("app/public/client.js"))
      //cp(/("index.html"), /("app/views/index.html"))
    },
    fullGlitchApp := {
      def cp(p1: nio.file.Path, p2: nio.file.Path) = {
        Files.deleteIfExists(p2)
        Files.copy(p1, p2)
      }
      def / = new File(baseDirectory.value, _: String).toPath
      cp(/("package.json"), /("app/package.json"))
      cp(/("backend/target/scala-2.12/backend-fullopt.js"), /("app/server.js"))
      cp(/("frontend/target/scala-2.12/frontend-fullopt.js"), /("app/public/client.js"))
      //cp(/("index.html"), /("app/views/index.html"))
    }
//    commands ++= Seq(
//      Command.single("fastGlitchApp")(
//        (s: State, _: String) => {fastGlitchApp.value; s}
//      ),
//      Command.single("fullGlitchApp")(
//        (s: State, _: String) => {fullGlitchApp.value; s}
//    ))
  )
  .aggregate(
    common,
    frontend,
    backend
  )

lazy val common: Project = project
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "common",
    settings,
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "1.0.1",
      "org.typelevel" %%% "cats-effect" % "0.9"
    )
  )

lazy val frontend: Project = project
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
    common
  )

lazy val backend: Project = project
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "backend",
    settings,
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "1.0.1",
      "org.typelevel" %%% "cats-effect" % "0.9"
    ) ++ Seq(
      "io.scalajs" %%% "nodejs" % "0.4.2",
      "io.scalajs.npm" %%% "express" % "0.4.2"
    )
  )
  .dependsOn(
    common
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
