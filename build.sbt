val ProjectName  = "strava-pr"
val Version      = "0.1.0-SNAPSHOT"
val ScalaVersion = "2.12.3"

lazy val stravaPR = (project in file("."))
  .settings(stravaPRSettings: _*)

lazy val stravaPRSettings = Seq(
  name         := ProjectName,
  version      := Version,
  scalaVersion := ScalaVersion,

  libraryDependencies ++= Seq(
    Dependency.Scrava,
    Dependency.TypesafeConfig,
    Dependency.PlayJson,
    Dependency.Scrimage,
    Dependency.Scopt,

    Dependency.ScalaTest
  )
)

enablePlugins(JavaAppPackaging)

addCommandAlias("dist", "universal:packageXzTarball")
