name := "FunctionalTetris"

version := "rolling_release"

scalaVersion := "2.12.8"

enablePlugins(ScalaJSPlugin)
scalaJSUseMainModuleInitializer := false

scalacOptions += "-Ypartial-unification"
scalacOptions += "-language:reflectiveCalls" // for echo! "str"
scalacOptions += "-deprecation"
scalacOptions ++= Seq("-release", "8")

libraryDependencies += "org.typelevel" %%% "cats-core" % "2.0.0-M4"
libraryDependencies += "org.typelevel" %%% "cats-effect" % "1.3.1"
libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.7"
