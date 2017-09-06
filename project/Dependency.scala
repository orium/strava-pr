import sbt._

object Dependency {
  private object Version {
    val Scrava         = "1.2.3"
    val TypesafeConfig = "1.3.1"
  }

  val Scrava         = "com.github.kiambogo" %% "scrava" % Version.Scrava
  val TypesafeConfig = "com.typesafe"        %  "config" % Version.TypesafeConfig
}
