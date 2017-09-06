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
  private def formatDuration(duration: Duration): String = {
    val s = duration.toSeconds % 60
    val m = duration.toSeconds / 60 % 60
    val h = duration.toSeconds / 60 / 60

    f"$h h $m%02d m $s%02d s"
  }

  private def outputPersonalRecords(personalRecords: PersonalRecords): Unit = {
    var first = true

    personalRecords.distances foreach { distance =>
      val bestTimes = personalRecords.bestTimes(distance)

      if (bestTimes.nonEmpty) {
        if (!first) {
          (0 to 3).foreach(_ => println())
        }

        println(s"Best $distance meters")
        println()
        println("         time            date        pace        start at (m)   total run dist (m)   url")

        bestTimes foreach { distanceDuration: DistanceDuration =>
          val run = distanceDuration.run
          val formatedDuration = formatDuration(distanceDuration.duration)
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

  private def isSetupDone(): Boolean =
    ConfigFile.exists()

  private def createConfigFile(): Unit = {
    ConfigDir.mkdirs()
    val p = new PrintWriter(ConfigFile)
    try {
      p.write {
        s"""auth-token = "put a token here"
           |
           |show-n-best = 5
           |only-best-of-each-run = true
           |
           |pr-distances = [
           |  1000,
           |  1500,
           |  1610,
           |  3000,
           |  3219,
           |  5000,
           |  7500,
           |  10000,
           |  16094,
           |  15000,
           |  20000,
           |  21098,
           |  30000,
           |  42195,
           |  50000,
           |  80468,
           |  100000,
           |  160935
           |]
         """.stripMargin
      }
    } finally {
      p.close()
    }
  }

  def benchmark[T](f: => T): T = {
    val before = System.nanoTime()
    val r = f
    val after = System.nanoTime()

    println(f"Executed in ${(after - before)/1000000.0}%.2f")

    r
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

    benchmark {
      val runCache = if (CacheFile.exists()) RunCache.fromFile(CacheFile).get else RunCache.empty
      val client = new ScravaClient(config.accessToken)

      val runs: Set[Run] = RateLimiter(client.listAthleteActivities(retrieveAll = true)).flatMap { activitySummary =>
        runCache.get(activitySummary.id).orElse {
          Some(RateLimiter(client.retrieveActivity(activitySummary.id)))
            .collect {
              case personalActivity: PersonalDetailedActivity => personalActivity
            }.filter(_.`type` == "Run")
            .map(activity => Run.fetch(client, activity))
        }
      }
        .toSet

      // Populate and save cache.
      runs.foreach(runCache.add)
      runCache.save(CacheFile)

      // WIP
      if (false) {
        def fromTo(start: Int, end: Int, step: Int = 1): Seq[Int] =
          Stream.iterate(start)(_ + step).takeWhile(_ <= end)

        // Start at 500 meters since it is unlikely that we have accurate GPS information for such short distances.
        val distances = fromTo(500, 1000000, 50)
        val personalRecords = PersonalRecords.fromRuns(runs, distances, showNBest = 1, onlyBestOfEachRun = false)

        println("# distance      time       pace     pace secs     date")

        distances foreach { distance =>
          val bestTimes = personalRecords.bestTimes(distance)

          if (bestTimes.nonEmpty) {
            val bestTime = bestTimes.bestTime.get
            val duration = formatDuration(bestTime.duration).filterNot(_.isSpaceChar)
            val date     = bestTime.run.date
            val pace     = bestTime.pace

            println(f"$distance%10d   $duration%9s   $pace%8s  ${pace.durationPerKm.toSeconds}%10d  $date%6s")
          }
        }

        // WIP
        // outputFinegrainedRecords()
      } else {
        val personalRecords = PersonalRecords.fromRuns(runs, config.prDistances, config.showNBest, config.onlyBestOfEachRun)

        outputPersonalRecords(personalRecords)
      }
    }
  }
}
