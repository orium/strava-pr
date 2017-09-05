import sbt._

object Plugin {
  private object Version {
    val SbtUpdates = "0.3.1"
  }

  val SbtUpdates = "com.timushev.sbt" %% "sbt-updates" % Version.SbtUpdates
}
