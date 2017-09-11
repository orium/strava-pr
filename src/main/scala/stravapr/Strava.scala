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

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import kiambogo.scrava.ScravaClient
import kiambogo.scrava.models.{PersonalDetailedActivity, Streams}

class Strava(
  client: ScravaClient,
  runCache: RunCache,
  rateLimiter: RateLimiter
) {
  private def fetchRunActivities(ids: Set[Int]): Set[PersonalDetailedActivity] =
    ids.flatMap { id =>
      Some(rateLimiter(client.retrieveActivity(id)))
        .collect {
          case personalActivity: PersonalDetailedActivity => personalActivity
        }.filter(_.`type` == "Run")
    }

  def fetchRunActivities(): Set[PersonalDetailedActivity] = {
    val runIds = rateLimiter(client.listAthleteActivities(retrieveAll = true)).map(_.id).toSet

    fetchRunActivities(runIds)
  }

  def activityToRun(activity: PersonalDetailedActivity): Run = {
    val timeDistance: Seq[Streams] = rateLimiter {
      client.retrieveActivityStream(activity.id.toString, Some("time,distance"))
    }
    val times     = timeDistance(0).data.map(_.asInstanceOf[Int]).toArray
    val distances = timeDistance(1).data.map(_.asInstanceOf[Float].round).toArray
    val datetime  = LocalDateTime.parse(activity.start_date, DateTimeFormatter.ISO_DATE_TIME)

    Run(activity.id, datetime, times, distances)
  }

  def populateRunCache(cacheFile: File, invalidateCache: Boolean): Strava.PopulateCacheResult = {
    if (invalidateCache) {
      runCache.invalidate()
    }

    val initialCacheSize = runCache.size

    val runs = Runs {
      rateLimiter(client.listAthleteActivities(retrieveAll = true))
        .map(_.id)
        .flatMap { id =>
          runCache.get(id).orElse {
            fetchRunActivities(Set(id)).map(activity => activityToRun(activity)).headOption
          }
        }.toSet
    }

    // Populate and save cache.
    runCache.add(runs)
    runCache.save(cacheFile)

    Strava.PopulateCacheResult(runs, runCache.size - initialCacheSize)
  }
}

object Strava {
  case class PopulateCacheResult(
    runs: Runs,
    fetchedRuns: Int
  )

  def apply(
    client: ScravaClient,
    cacheFile: File,
    rateLimiter: RateLimiter
  ): Strava = {
    val runCache: RunCache = if (cacheFile.exists()) RunCache.fromFile(cacheFile).get else RunCache.empty

    new Strava(
      client,
      runCache,
      rateLimiter
    )
  }
}
