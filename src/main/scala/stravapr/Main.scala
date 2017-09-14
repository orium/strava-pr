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
import java.net.URL

import kiambogo.scrava.ScravaClient
import kiambogo.scrava.models._
import stravapr.Utils.RichDuration
import stravapr.animation.Gif
import stravapr.gnuplot.plots.PacePerDistancePersonalRecordsPlot
import stravapr.gnuplot.{Plot, Resolution}

import scala.concurrent.duration._
import scala.util.Properties

object Main {
  private object Files {
    val ConfigDir:  File = new File(new File(Properties.userHome, ".config"), "strava-pr")
    val ConfigFile: File = new File(ConfigDir, "strava-pr.conf")
    val RunCacheFile:  File = new File(ConfigDir, "run-cache")
  }

  private object RateLimiters {
    val stravaRateLimiter: RateLimiter = new RateLimiter(_.isInstanceOf[RateLimitException])
    val imgurRateLimiter: RateLimiter = new RateLimiter(_.isInstanceOf[ImgurUploader.RateLimitingExceeded.type])
  }

  private def isSetupDone(): Boolean =
    Files.ConfigFile.exists()

  private def createConfigFile(): Unit = {
    Files.ConfigDir.mkdirs()
    val p = new PrintWriter(Files.ConfigFile)
    try {
      p.write(Config.DefaultConfigFileContent)
    } finally {
      p.close()
    }
  }

  private def cachedRuns(): Runs = {
    val runCache: RunCache = if (Files.RunCacheFile.exists()) RunCache.fromFile(Files.RunCacheFile).get else RunCache.empty
    runCache.allRuns
  }

  private def stravaFetch(accessToken: String, invalidateCache: Boolean): Unit = {
    val strava = Strava(
      new ScravaClient(accessToken),
      Files.RunCacheFile,
      RateLimiters.stravaRateLimiter
    )

    val Strava.PopulateCacheResult(runs, fetchedRuns) = strava.populateRunCache(Files.RunCacheFile, invalidateCache) { run =>
      println(s"Fetched run of ${run.date}.")
    }

    println(s"Fetched $fetchedRuns runs from Strava.")
    println(s"You have now a total of ${runs.size} runs locally.")
  }

  private def stravaAddDescriptionHistory(
    stravaAccessToken: String,
    ImgurClientIdOpt: Option[String],
    resolution: Resolution,
    startDistance: Int,
    forceRefresh: Boolean
  ): Unit = {
    val runs: Runs = cachedRuns()

    ImgurClientIdOpt match {
      case Some(imgurClientId) =>
        val stravaClient = new ScravaClient(stravaAccessToken)
        val strava = Strava(
          stravaClient,
          Files.RunCacheFile,
          RateLimiters.stravaRateLimiter
        )

        val imgurUploader: ImgurUploader = new ImgurUploader(imgurClientId)

        val runActivityMap: Map[Run, PersonalDetailedActivity] = {
          val allRunActivities = strava.fetchRunActivities()

          allRunActivities.map { activity =>
            runs.get(activity.id) match {
              case Some(run) => run -> activity
              case None => // Cache miss, lets fetch it fully.
                strava.activityToRun(activity) -> activity
            }
          }.toMap
        }

        val allRuns = Runs(runActivityMap.keySet)

        allRuns.runHistory.personalRecordsHistory
          .foreach { case RunHistory.PersonalRecordsAtRun(run, runRecords, previousPersonalRecords, _) =>
            val activity = runActivityMap(run)
            val descriptionLineStart = s"Record pace per distance v"
            val descriptionLine = s"$descriptionLineStart${PacePerDistancePersonalRecordsPlot.Version}: "
            val description: Option[Seq[String]] = activity.description.map(_.lines.toSeq)
              .filter(_.nonEmpty)
            val missingPlot: Boolean = !description.getOrElse(Seq.empty).exists(_.startsWith(descriptionLine))

            if (missingPlot || forceRefresh) {
              val prAndRacePlot = PacePerDistancePersonalRecordsPlot.fromMultiplePersonalRecords(
                Set(previousPersonalRecords, runRecords),
                PacePerDistancePersonalRecordsPlot.Config(
                  plotMinDistance = startDistance
                )
              )

              val imageUrl: URL =
                RateLimiters.imgurRateLimiter(imgurUploader.upload(prAndRacePlot.createPNGImage(resolution)).get)

              val newDescription = description match {
                case Some(lines) =>
                  val linesWithoutPacePerDistance = lines.filterNot(_.startsWith(descriptionLineStart))

                  linesWithoutPacePerDistance ++ Seq("", s"$descriptionLine$imageUrl")
                case None => Seq(s"$descriptionLine$imageUrl")
              }

              try {
                stravaClient.updateActivity(
                  activity.id,
                  description = Some(newDescription.mkString("\n"))
                )
              } catch {
                case e: Exception =>
                  // Unfortunately scrava doesn't tell us the error reason, but a likely scenario is that we do not
                  // have write permission.

                  // TODO add better error handling to scrava

                  println(
                    s"Failed to update activity ${activity.id} of ${activity.start_date}.  " +
                      "Maybe we do not have write permission?\n\n" +
                      "See here how to obtain an access token with write permission: " +
                      "http://yizeng.me/2017/01/11/get-a-strava-api-access-token-with-write-permission/"
                  )
                  throw e
              }

              println(s"Added plot to run of ${run.date}.")
            }
          }
      case None =>
        println("No imgur client id defined.")
    }
  }

  private def list(): Unit = {
    val runs: Runs = cachedRuns()

    println("run #      date      distance (m)        pace   url")
    runs.toIndexedSeq.zipWithIndex foreach { case (run, runNumber) =>
      println(f"$runNumber%5d   ${run.date}%10s         ${run.stats.distance}%6d   ${run.stats.pace}%8s   ${run.stravaUrl}")
    }
  }

  private def show(runNumber: Option[Int], startDistance: Int, mode: Command.Show.Mode): Unit = {
    val runs: Runs = cachedRuns()

    val plotConfig = PacePerDistancePersonalRecordsPlot.Config(plotMinDistance = startDistance)

    val plot: Option[Plot] = runNumber match {
      case Some(runN) if runN < runs.size =>
        Some {
          PacePerDistancePersonalRecordsPlot.fromMultiplePersonalRecordsAndRun(
            runs.personalRecords,
            runs.toIndexedSeq(runN),
            plotConfig
          )
        }
      case Some(runN) =>
        println(s"There is no run $runN")
        None
      case None =>
        Some {
          PacePerDistancePersonalRecordsPlot.fromRuns(runs, plotConfig)
        }
    }

    plot foreach { p =>
      mode match {
        case Command.Show.Mode.CreateImage(imageFile, resolution) => p.createPNGImage(imageFile, resolution)
        case Command.Show.Mode.Display => p.show()
      }
    }
  }

  private def history(
    imageFile: File,
    startDistance: Int,
    frameDuration: Duration,
    resolution: Resolution,
    animationLoop: Boolean
  ): Unit = {
    val runs: Runs = cachedRuns()

    val allPlots = runs.runHistory.personalRecordsHistory
      .flatMap { case RunHistory.PersonalRecordsAtRun(_, runRecords, previousPersonalRecords, personalRecords) =>
        val prAndRacePlot = PacePerDistancePersonalRecordsPlot.fromMultiplePersonalRecords(
          Set(previousPersonalRecords, runRecords)
        )

        Seq(
          prAndRacePlot,
          PacePerDistancePersonalRecordsPlot.fromPersonalRecords(personalRecords)
        )
      }

    val plotConfig = {
      val minPace = allPlots.map(_.minPace).min
      val maxPace = allPlots.map(_.maxPace).max

      PacePerDistancePersonalRecordsPlot.Config(
        plotMinTimeOpt = Some(minPace.durationPerKm),
        plotMaxTimeOpt = Some(maxPace.durationPerKm),
        plotMinDistance = startDistance,
        plotMaxDistanceOpt = Some(runs.stats.maxDistance),
      )
    }

    val images = allPlots
      .drop(1) // The first two frames will are the same.
      .map(_.withConfig(plotConfig))
      .map(_.createPNGImage(resolution))

    Gif.createGif(imageFile, images, delay = frameDuration, loop = animationLoop)

    images.foreach(_.delete())
  }

  private def table(bestN: Int, distances: Seq[Int]): Unit = {
    val runs: Runs = cachedRuns()
    val personalRecords = runs.personalRecords

    distances.zipWithIndex foreach { case (distance, index) =>
      val bestTimes = personalRecords.bestTimes(distance)

      if (bestTimes.nonEmpty) {
        if (index > 0) {
          (0 to 3).foreach(_ => println())
        }

        println(s"Best $distance meters")
        println()
        println("         time            date         pace       start at (m)   total run dist (m)   url")

        bestTimes.take(bestN) foreach { distanceDuration: RunSlice =>
          val run = distanceDuration.run
          val formatedDuration = distanceDuration.duration.formatHMS
          val runDist = run.stats.distance
          val pace = distanceDuration.pace

          println(f"    $formatedDuration%14s    ${run.date}%6s    $pace%8s          ${distanceDuration.startAt}%6d               $runDist%6d   ${run.stravaUrl}")
        }
      }
    }
  }

  def main(args: Array[String]): Unit = {
    if (!isSetupDone()) {
      createConfigFile()

      println("There was no configuration file.  I have created one.  Please configure it properly:")
      println()
      println(s"    ${Files.ConfigFile}")

      System.exit(0)
    }

    val config: Config = Config.fromFile(Files.ConfigFile).get
    val command: Option[Command] = CommandLineParser.parse(args)

    command foreach {
      case Command.Strava.Fetch(invalidateCache) => stravaFetch(config.accessToken, invalidateCache)
      case Command.Strava.AddDescriptionHistory(resolution, startDistance, forceRefresh) =>
        stravaAddDescriptionHistory(config.accessToken, config.imgurClientId, resolution, startDistance, forceRefresh)
      case Command.List => list()
      case Command.Show(runNumber, startDistance, mode) => show(runNumber, startDistance, mode)
      case Command.History(imageFile, startDistance, frameDuration, resolution, animationLoop) =>
        history(imageFile, startDistance, frameDuration, resolution, animationLoop)
      case Command.Table(bestN, distances) => table(bestN, distances)
    }
  }
}
