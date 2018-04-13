enablePlugins(ScalaJSPlugin)
enablePlugins(BuildInfoPlugin)

name := "EP API Script"

organization := "com.lkroll.ep"

version := "0.4.4"

scalaVersion := "2.12.4"

libraryDependencies += "com.lkroll.roll20" %%% "roll20-api-framework" % "0.5.+"
libraryDependencies += "com.lkroll.ep" %%% "epcompendium-core" % "0.7.+"
libraryDependencies += "com.lkroll.ep" %%% "ep-model" % "1.6.0"
libraryDependencies += "com.lihaoyi" %%% "fastparse" % "1.+"
libraryDependencies += "org.rogach" %%% "scallop" % "3.1.+"
libraryDependencies += "org.scalactic" %%% "scalactic" % "3.0.4"
libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.4" % "test"

lazy val submitScript = taskKey[Unit]("Submit the script that assembles the API script");
lazy val openScript = taskKey[Unit]("Opens the API script");
lazy val submit = taskKey[Unit]("Assemble and fastOpt the API script");
lazy val submitScriptFull = taskKey[Unit]("Submit the script that assembles the API script in fullOpt");
lazy val submitFull = taskKey[Unit]("Assemble and fullOpt the API script");

submitScript := {
  s"./assemble.sc --version ${version.value}" !
}

submitScriptFull := {
  s"./assemble.sc --version ${version.value} --full true" !
}

openScript := {
  Seq("/Applications/Sublime Text.app/Contents/SharedSupport/bin/subl", "ep-script.js").!!
}

submit in Compile := Def.sequential(
  fastOptJS in Compile,
  submitScript in Compile,
  openScript in Compile
).value

submitFull in Compile := Def.sequential(
  fullOptJS in Compile,
  submitScriptFull in Compile,
  openScript in Compile
).value

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "com.lkroll.ep.api"