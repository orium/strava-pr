/* This file is part of strava-pr.
 *
 * strava-pr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * strava-pr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with strava-pr.  If not, see <http://www.gnu.org/licenses/>.
 */

package stravapr

import java.io.{File, PrintWriter}

import kiambogo.scrava.ScravaClient
import kiambogo.scrava.models.{PersonalDetailedActivity, RateLimitException}
import stravapr.Utils.RichDuration
import stravapr.animation.Gif
import stravapr.gnuplot.plots.PacePerDistancePersonalRecordsPlot

import scala.concurrent.duration._
import scala.util.Properties

object RateLimiter {
  private var backoff: Duration = 0.seconds

  def apply[T](f: => T): T = try {
    val r = f

    backoff = 0.seconds

    r
  } catch {
    case _: RateLimitException =>
      if (backoff == 0.seconds) {
        backoff = 1.minute
      }

      backoff = backoff * 2.0

      println(s"Rate limit exceeded.  Sleeping for $backoff...")
      Thread.sleep(backoff.toMillis)

      apply(f)
  }
}

object Main {
  private def outputPersonalRecords(personalRecords: PersonalRecords, distances: Seq[Int], showNBest: Int): Unit = {
    var first = true

    distances foreach { distance =>
      val bestTimes = personalRecords.bestTimes(distance)

      if (bestTimes.nonEmpty) {
        if (!first) {
          (0 to 3).foreach(_ => println())
        }

        println(s"Best $distance meters")
        println()
        println("         time            date         pace       start at (m)   total run dist (m)   url")

        bestTimes.take(showNBest) foreach { distanceDuration: RunSlice =>
          val run = distanceDuration.run
          val formatedDuration = distanceDuration.duration.formatHMS()
          val url = s"https://www.strava.com/activities/${run.id}"
          val runDist = run.totalDistance
          val pace = distanceDuration.pace

          println(f"    $formatedDuration%14s    ${run.date}%6s    $pace%8s          ${distanceDuration.startAt}%6d               $runDist%6d   $url")
        }

        first = false
      }
    }
  }

  private val ConfigDir:  File = new File(new File(Properties.userHome, ".config"), "strava-pr")
  private val ConfigFile: File = new File(ConfigDir, "strava-pr.conf")
  private val CacheFile:  File = new File(ConfigDir, "cache")

  private val GnuplotDir:        File = new File("gnuplot")
  private val PlotPrGnuplotFile: File = new File(GnuplotDir, "plot-pr-pace.gnuplot")
  private val PlotPrDataFile:    File = new File(GnuplotDir, "plot-pr-pace.dat")

  private def isSetupDone(): Boolean =
    ConfigFile.exists()

  private def createConfigFile(): Unit = {
    ConfigDir.mkdirs()
    val p = new PrintWriter(ConfigFile)
    try {
      p.write(Config.DefaultConfigFileContent)
    } finally {
      p.close()
    }
  }

  private def fetchRuns(client: ScravaClient): Unit = {
    val runCache: RunCache = if (CacheFile.exists()) RunCache.fromFile(CacheFile).get else RunCache.empty
    val initialCacheSize = runCache.size

    val runs = Runs {
      RateLimiter(client.listAthleteActivities(retrieveAll = true)).flatMap { activitySummary =>
        runCache.get(activitySummary.id).orElse {
          Some(RateLimiter(client.retrieveActivity(activitySummary.id)))
            .collect {
              case personalActivity: PersonalDetailedActivity => personalActivity
            }.filter(_.`type` == "Run")
            .map(activity => Run.fetch(client, activity))
        }
      }.toSet
    }

    // Populate and save cache.
    runCache.add(runs)
    runCache.save(CacheFile)

    println(s"Fetched ${runCache.size - initialCacheSize} new runs.")
    println(s"You have now a total of ${runs.size} runs.")
  }

  private def allRuns(): Runs = {
    val runCache: RunCache = if (CacheFile.exists()) RunCache.fromFile(CacheFile).get else RunCache.empty
    runCache.allRuns
  }

  def main(args: Array[String]): Unit = {
    if (!isSetupDone()) {
      createConfigFile()

      println("Strava-PR did not have a configuration file.  I have created one.  Please configure properly:")
      println()
      println(s"    $ConfigFile")

      System.exit(0)
    }

    val config: Config = Config.fromFile(ConfigFile).get
    val runs: Runs = allRuns()

    args.toSeq match {
      case Seq("fetch") =>
        val client: ScravaClient = new ScravaClient(config.accessToken)

        fetchRuns(client)
      case Seq("history") =>
        val plotConfig = PacePerDistancePersonalRecordsPlot.Config(
          plotMinTime = 3.minutes + 30.seconds,       // TODO auto discover this
          plotMaxTime = Some(8.minutes + 30.seconds), // TODO auto discover this
          plotMinDistance = 500,
          plotMaxDistanceOpt = Some(runs.stats.maxDistance),
        )

        val (_, imagesWithRuns) =
          runs.map(_.personalRecords).foldLeft((PersonalRecords.empty, Vector.empty[File])) {
            case ((previousPersonalRecords, previousImages), runPr) =>
              val plot = PacePerDistancePersonalRecordsPlot.fromMultiplePersonalRecords(
                Set(previousPersonalRecords, runPr),
                plotConfig
              )

              val personalRecords = previousPersonalRecords merge runPr

              val frames = Seq(
                plot.createPNGImage(),
                PacePerDistancePersonalRecordsPlot.fromPersonalRecords(personalRecords, plotConfig).createPNGImage()
              )

            (personalRecords, previousImages ++ frames)
        }

        val images = imagesWithRuns
            .drop(1) // The first two frames will are the same.

        Gif.createGif(
          new File("/tmp/orium/runs/animated.gif"),
          images,
          delay = 500.milliseconds,
          loop = false
        )

        images.foreach(_.delete())
      case Seq("show") =>
        val plot = PacePerDistancePersonalRecordsPlot.fromRuns(runs)

        // plot.createPNGImage(new File("/tmp/orium/p.png"))

        plot.showPlot()
      case Seq("show", n) =>
        val plot = PacePerDistancePersonalRecordsPlot.fromMultiplePersonalRecordsAndRun(
          runs.personalRecords,
          runs.toIndexedSeq(n.toInt - 1)
        )

        // plot.createPNGImage(new File("/tmp/orium/p.png"))

        plot.showPlot()
      case Seq("upload") =>
        config.imgurClientId match {
          case Some(clientId) =>
            val plot = PacePerDistancePersonalRecordsPlot.fromRuns(runs)
            val pngFile = plot.createPNGImage()

            val imgurUploader = new ImgurUploader(clientId)

            val url = imgurUploader.upload(pngFile).get

            pngFile.delete()

            println(s"url: $url")
          case None =>
            println("No imgur client id defined.")
        }
      case Seq("table") =>
        outputPersonalRecords(runs.personalRecords, config.prDistances, config.showNBest)
      case _ =>
    }
  }
}
