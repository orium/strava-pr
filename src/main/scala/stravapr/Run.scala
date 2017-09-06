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

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Arrays
import java.util.concurrent.TimeUnit

import kiambogo.scrava.ScravaClient
import kiambogo.scrava.models.{PersonalDetailedActivity, Streams}

import scala.concurrent.duration.Duration

case class DistanceDuration(distance: Int, startAt: Int, duration: Duration, run: Run)

object DistanceDuration {
  implicit object DistanceDurationIsOrdered extends Ordering[DistanceDuration] {
    def compare(a: DistanceDuration, b: DistanceDuration): Int = a.duration compare b.duration
  }
}

class Run private (val activity: PersonalDetailedActivity, timeDistance: Seq[Streams]) {
  import DistanceDuration.DistanceDurationIsOrdered

  private val times: Array[Int] = timeDistance(0).data.map(_.asInstanceOf[Int]).toArray
  private val distances: Array[Int] = timeDistance(1).data.map(_.asInstanceOf[Float].round).toArray

  def date: LocalDate = LocalDate.parse(activity.start_date, DateTimeFormatter.ISO_ZONED_DATE_TIME)

  private def interpolate(d1: Int, t1: Int, d2: Int, t2: Int, d: Int): Int = {
    val m = (t1 - t2).toDouble / (d1 - d2).toDouble
    val b = t2 - m * d2

    (m * d + b).round.toInt
  }

  def timeAt(distance: Int): Option[Duration] = {
    val time: Option[Int] = Arrays.binarySearch(distances, distance) match {
      case i if i >= 0 => Some(times(i))
      case i =>
        val indexBefore = -i - 2
        val indexAfter = indexBefore + 1

        if (indexAfter >= distances.length) {
          None
        } else {
          val distanceBefore = distances(indexBefore)
          val timeBefore     = times(indexBefore)
          val distanceAfter  = distances(indexAfter)
          val timeAfter      = times(indexAfter)

          Some(interpolate(distanceBefore, timeBefore, distanceAfter, timeAfter, distance))
        }
    }

    time.map(Duration(_, TimeUnit.SECONDS))
  }

  def totalDistance: Int = distances.last

  private def allTimesForDistance(distance: Int): Seq[DistanceDuration] = {
    (0 to (totalDistance - distance)).map { startDistance =>
      val time = timeAt(startDistance + distance).get - timeAt(startDistance).get

      DistanceDuration(distance, startDistance, time, this)
    }
  }

  def bestTimes(distance: Int): Seq[DistanceDuration] =
    allTimesForDistance(distance).sorted

  def bestTime(distance: Int): Option[DistanceDuration] =
    bestTimes(distance).headOption
}

object Run {
  def fetch(client: ScravaClient, activity: PersonalDetailedActivity): Run = {
    val timeDistance: Seq[Streams] = RateLimiter {
      client.retrieveActivityStream(activity.id.toString, Some("time,distance"))
    }

    new Run(activity, timeDistance)
  }
}

class BestTimes private (bestTimes: Seq[DistanceDuration]) extends Traversable[DistanceDuration] {
  override def foreach[U](f: (DistanceDuration) => U): Unit = bestTimes.foreach(f)
}

object BestTimes {
  def fromRuns(runs: Set[Run], distance: Int, bestN: Int): BestTimes = {
    val bestTimes: Seq[DistanceDuration] =
      runs.flatMap(run => run.bestTimes(distance)).toSeq.sorted

    new BestTimes(bestTimes.take(bestN))
  }
}
