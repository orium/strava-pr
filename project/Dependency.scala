import sbt._

object Dependency {
  private object Version {
    val Scrava         = "1.2.3"
    val TypesafeConfig = "1.3.1"
    val PlayJson       = "2.6.3"
  }

  val Scrava         = "com.github.kiambogo"    %% "scrava"    % Version.Scrava
  val TypesafeConfig = "com.typesafe"           %  "config"    % Version.TypesafeConfig
  val PlayJson       = "com.typesafe.play"      %% "play-json" % "2.6.3"
}
