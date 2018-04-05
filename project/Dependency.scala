import sbt._

object Dependency {
  private object Version {
    val Scrava         = "1.3.0"
    val TypesafeConfig = "1.3.3"
    val PlayJson       = "2.6.9"
    val Scrimage       = "3.0.0-alpha4"
    val ScalaTest      = "3.0.5"
    val Scopt          = "3.7.0"
  }

  val Scrava         = "com.github.kiambogo"    %% "scrava"        % Version.Scrava
  val TypesafeConfig = "com.typesafe"           %  "config"        % Version.TypesafeConfig
  val PlayJson       = "com.typesafe.play"      %% "play-json"     % Version.PlayJson
  val Scrimage       = "com.sksamuel.scrimage"  %% "scrimage-core" % Version.Scrimage
  val ScalaTest      = "org.scalatest"          %% "scalatest"     % Version.ScalaTest
  val Scopt          = "com.github.scopt"       %% "scopt"         % Version.Scopt
}
