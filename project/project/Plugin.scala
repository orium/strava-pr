import sbt._

object Plugin {
  private object Version {
    val SbtUpdates        = "0.3.1"
    val SbtNativePackager = "1.2.2"
    val SbtDynVer         = "2.0.0"
  }

  val SbtUpdates        = "com.timushev.sbt" %% "sbt-updates"         % Version.SbtUpdates
  val SbtNativePackager = "com.typesafe.sbt" %  "sbt-native-packager" % Version.SbtNativePackager
  val SbtDynVer         = "com.dwijnand"     %  "sbt-dynver"          % Version.SbtDynVer
}
