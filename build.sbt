name := "FunctionalTetris"

version := "0.1"

scalaVersion := "2.12.4"

enablePlugins(ScalaJSPlugin)
scalaJSUseMainModuleInitializer := false

scalacOptions += "-Ypartial-unification"
libraryDependencies += "org.typelevel" %%% "cats-core" % "1.0.1"
//libraryDependencies += "org.typelevel" %% "kittens" % "1.0.0-RC2"
libraryDependencies += "org.typelevel" %% "cats-effect" % "0.9"
//libraryDependencies += "org.typelevel" %% "mouse" % "0.16"
//libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.3"

libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "0.9.2"
