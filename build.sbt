name := "FunctionalTetris"

version := "rolling_release"

scalaVersion := "2.12.4"

enablePlugins(ScalaJSPlugin)
scalaJSUseMainModuleInitializer := false

scalacOptions += "-Ypartial-unification"
scalacOptions += "-language:reflectiveCalls" // for echo! "str"
scalacOptions += "-deprecation"

libraryDependencies += "org.typelevel" %%% "cats-core" % "1.0.1"
libraryDependencies += "org.typelevel" %%% "cats-effect" % "0.9"
libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.2"
