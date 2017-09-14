val ProjectName  = "strava-pr"
val ScalaVersion = "2.12.3"

lazy val stravaPR = (project in file("."))
  .settings(stravaPRSettings: _*)

lazy val stravaPRSettings = Seq(
  name         := ProjectName,
  scalaVersion := ScalaVersion,

  libraryDependencies ++= Seq(
    Dependency.Scrava,
    Dependency.TypesafeConfig,
    Dependency.PlayJson,
    Dependency.Scrimage,
    Dependency.Scopt,

    Dependency.ScalaTest % Test
  ),

  mappings in Universal += file("README.md") -> "README.md"
)

enablePlugins(JavaAppPackaging)

addCommandAlias("dist", "universal:packageXzTarball")
