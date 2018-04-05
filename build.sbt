name := "FunctionalTetris"

version := "rolling_release"

scalaVersion := "2.12.4"

lazy val global = project
  .in(file("."))
  .settings(
    settings,
    (compile in Compile) := ((compile in Compile)
      dependsOn (fastOptJS in(frontend, Compile))
      dependsOn (fastOptJS in(backend, Compile))).value
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
