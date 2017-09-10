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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import kiambogo.scrava.{Client, ScravaClient}
import kiambogo.scrava.models._
import net.liftweb.json.DefaultFormats
import stravapr.Utils.RichDuration
import stravapr.animation.Gif
import stravapr.gnuplot.Resolution
import stravapr.gnuplot.plots.PacePerDistancePersonalRecordsPlot

import scala.concurrent.duration._
import scala.util.{Failure, Properties, Success, Try}
import scalaj.http.Http

class RateLimiter(isRateLimitExceeded: Exception => Boolean) {
  private def run[T](f: => T, backoff: Duration = 1.minute): T = try {
    f
  } catch {
    case e: Exception if isRateLimitExceeded(e) =>
      println(s"Rate limit exceeded.  Sleeping for $backoff...")

      Thread.sleep(backoff.toMillis)

      run(f, backoff * 2)
  }

  RateLimitException.getClass

  def apply[T](f: => T): T =
    run(f)
}

object Main {
  private val ConfigDir:  File = new File(new File(Properties.userHome, ".config"), "strava-pr")
  private val ConfigFile: File = new File(ConfigDir, "strava-pr.conf")
  private val CacheFile:  File = new File(ConfigDir, "cache")

  private val GnuplotDir:        File = new File("gnuplot")
  private val PlotPrGnuplotFile: File = new File(GnuplotDir, "plot-pr-pace.gnuplot")
  private val PlotPrDataFile:    File = new File(GnuplotDir, "plot-pr-pace.dat")

  private val stravaRateLimiter: RateLimiter = new RateLimiter(_.isInstanceOf[RateLimitException])
  private val imgurRateLimiter: RateLimiter = new RateLimiter(_.isInstanceOf[ImgurUploader.RateLimitingExceeded.type])

  private def outputPersonalRecords(personalRecords: Records, distances: Seq[Int], showNBest: Int): Unit = {
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
          val formatedDuration = distanceDuration.duration.formatHMS
          val url = s"https://www.strava.com/activities/${run.id}"
          val runDist = run.stats.distance
          val pace = distanceDuration.pace

          println(f"    $formatedDuration%14s    ${run.date}%6s    $pace%8s          ${distanceDuration.startAt}%6d               $runDist%6d   $url")
        }

        first = false
      }
    }
  }

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

  private def fetchStravaRunActivities(client: ScravaClient, ids: Set[Int]): Set[PersonalDetailedActivity] =
    ids.flatMap { id =>
      Some(stravaRateLimiter(client.retrieveActivity(id)))
        .collect {
          case personalActivity: PersonalDetailedActivity => personalActivity
        }.filter(_.`type` == "Run")
    }

  private def fetchStravaRunActivities(client: ScravaClient): Set[PersonalDetailedActivity] = {
    val runIds = stravaRateLimiter(client.listAthleteActivities(retrieveAll = true)).map(_.id).toSet

    fetchStravaRunActivities(client, runIds)
  }

  private def activityToRun(client: ScravaClient, activity: PersonalDetailedActivity): Run = {
    val timeDistance: Seq[Streams] = stravaRateLimiter {
      client.retrieveActivityStream(activity.id.toString, Some("time,distance"))
    }
    val times     = timeDistance(0).data.map(_.asInstanceOf[Int]).toArray
    val distances = timeDistance(1).data.map(_.asInstanceOf[Float].round).toArray
    val datetime  = LocalDateTime.parse(activity.start_date, DateTimeFormatter.ISO_DATE_TIME)

    Run(activity.id, datetime, times, distances)
  }

  private def fetchRuns(client: ScravaClient): Runs = {
    val runCache: RunCache = if (CacheFile.exists()) RunCache.fromFile(CacheFile).get else RunCache.empty
    val initialCacheSize = runCache.size

    val runs = Runs {
      stravaRateLimiter(client.listAthleteActivities(retrieveAll = true))
        .map(_.id)
        .flatMap { id =>
          runCache.get(id).orElse {
            fetchStravaRunActivities(client, Set(id)).map(activity => activityToRun(client, activity)).headOption
          }
        }.toSet
      }

    // Populate and save cache.
    runCache.add(runs)
    runCache.save(CacheFile)

    println(s"Fetched ${runCache.size - initialCacheSize} new runs.")
    println(s"You have now a total of ${runs.size} runs.")

    runs
  }

  private def cachedRuns(): Runs = {
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
    val runs: Runs = cachedRuns()

    args.toSeq match {
      case Seq("fetch") =>
        val client: ScravaClient = new ScravaClient(config.accessToken)

        fetchRuns(client)
      case Seq("history") =>
        val plotConfig = PacePerDistancePersonalRecordsPlot.Config(
          plotMinTime = Some(3.minutes + 30.seconds), // TODO config
          plotMaxTime = Some(8.minutes + 30.seconds), // TODO config
          plotMinDistance = 500,
          plotMaxDistanceOpt = Some(runs.stats.maxDistance),
        )
        val resolution = Resolution.Resolution1080 // TODO config

        val allImages = runs.runHistory.personalRecordsHistory
          .flatMap { case RunHistory.PersonalRecordsAtRun(_, runRecords, previousPersonalRecords, personalRecords) =>
            val prAndRacePlot = PacePerDistancePersonalRecordsPlot.fromMultiplePersonalRecords(
              Set(previousPersonalRecords, runRecords),
              plotConfig
            )

            val frames = Seq(
              prAndRacePlot.createPNGImage(resolution),
              PacePerDistancePersonalRecordsPlot.fromPersonalRecords(personalRecords, plotConfig)
                .createPNGImage(resolution)
            )

            frames
          }

        val images = allImages
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

        // plot.createPNGImage(new File("/tmp/orium/runs/p.png"))

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
            val resolution = Resolution.Resolution1080 // TODO config
            val plot = PacePerDistancePersonalRecordsPlot.fromRuns(runs)
            val pngFile = plot.createPNGImage(resolution)

            val imgurUploader = new ImgurUploader(clientId)

            val url = imgurRateLimiter(imgurUploader.upload(pngFile).get)

            pngFile.delete()

            println(s"url: $url")
          case None =>
            println("No imgur client id defined.")
        }
      case Seq("strava-add-history") => // TODO option to force recompute
        config.imgurClientId match {
          case Some(clientId) =>
            val client: ScravaClient = new ScravaClient(config.accessToken)
            val imgurUploader: ImgurUploader = new ImgurUploader(config.imgurClientId.get)

            val runActivityMap: Map[Run, PersonalDetailedActivity] = {
              val allRunActivities = fetchStravaRunActivities(client)

              allRunActivities.map { activity =>
                runs.get(activity.id) match {
                  case Some(run) => run -> activity
                  case None => // Cache miss, got to fetch it fully.
                    activityToRun(client, activity) -> activity
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
                val alreadyHasPlot: Boolean = description.getOrElse(Seq.empty).exists(_.startsWith(descriptionLine))

                if (!alreadyHasPlot) {
                  val prAndRacePlot = PacePerDistancePersonalRecordsPlot.fromMultiplePersonalRecords(
                    Set(previousPersonalRecords, runRecords)
                  )

                  val imageUrl: URL = {
                    val resolution = Resolution.Resolution1080 // TODO config

                    imgurRateLimiter(imgurUploader.upload(prAndRacePlot.createPNGImage(resolution)).get)
                  }

                  val newDescription = description match {
                    case Some(lines) =>
                      val linesWithoutPacePerDistance = lines.filterNot(_.startsWith(descriptionLineStart))

                      linesWithoutPacePerDistance ++ Seq("", s"$descriptionLine$imageUrl")
                    case None => Seq(s"$descriptionLine$imageUrl")
                  }

                  try {
                    client.updateActivity(
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

                  println(s"Added plot to run of ${activity.start_date}.")
                }
              }
          case None =>
            println("No imgur client id defined.")
        }
      case Seq("table") =>
        outputPersonalRecords(runs.personalRecords, config.prDistances, config.showNBest)
      case _ =>
    }
  }
}
