import sbt._

object Dependency {
  private object Version {
    val Scrava = "1.2.3"
  }

  val Scrava = "com.github.kiambogo" %% "scrava"   % Version.Scrava
}
