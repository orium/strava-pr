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

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.Arrays
import java.util.concurrent.TimeUnit

import kiambogo.scrava.ScravaClient
import kiambogo.scrava.models.{PersonalDetailedActivity, Streams}

import scala.concurrent.duration.Duration

class Pace private (val durationPerKm: Duration) {
  override def toString: String = {
    val secs = durationPerKm.toSeconds
    f"""${secs / 60}%02d'${secs % 60}%02d"/km"""
  }
}

object Pace {
  def apply(distance: Int, duration: Duration): Pace = {
    require(distance > 0)

    new Pace(duration / (distance / 1000.0))
  }
}

case class RunSlice(distance: Int, startAt: Int, duration: Duration, run: Run) {
  def pace: Pace = Pace(distance, duration)
}

object RunSlice {
  implicit object DistanceDurationIsOrdered extends Ordering[RunSlice] {
    def compare(a: RunSlice, b: RunSlice): Int = {
      // We disambiguate to that we have a deterministic order.
      Seq(
        a.duration compare b.duration,
        a.run.date.compareTo(b.run.date),
        a.startAt  compare b.startAt,
        a.run.id   compare b.run.id
      ).find(_ != 0).getOrElse(0)
    }
  }
}

class Run(
  val id: Int,
  val datetime: LocalDateTime,
  val times: Array[Int],
  val distances: Array[Int]
) {
  import RunSlice.DistanceDurationIsOrdered

  require(distances.head == 0, "First distance must be zero")

  def date: LocalDate = datetime.toLocalDate

  private def interpolate(d1: Int, t1: Int, d2: Int, t2: Int, d: Int): Int = {
    val m = (t1 - t2).toDouble / (d1 - d2).toDouble
    val b = t2 - m * d2

    (m * d + b).round.toInt
  }

  private lazy val timeAtDistance: Array[Duration] = {
    val t = 0 +:
      (distances zip times).sliding(2).flatMap { case Array((distanceBefore, timeBefore), (distanceAfter, timeAfter)) =>
        ((distanceBefore + 1) to distanceAfter) map { distance =>
          interpolate(distanceBefore, timeBefore, distanceAfter, timeAfter, distance)
        }
      }.toSeq

    t.map(Duration(_, TimeUnit.SECONDS)).toArray
  }

  def timeAt(distance: Int): Option[Duration] =
    if (0 <= distance && distance <= totalDistance) Some(timeAtDistance(distance)) else None

  // TODO Run.Stats
  def totalDistance: Int = distances.last

  private def allTimesForDistance(distance: Int): Seq[RunSlice] = {
    (0 to (totalDistance - distance)).map { startDistance =>
      val time = timeAt(startDistance + distance).get - timeAt(startDistance).get

      RunSlice(distance, startDistance, time, this)
    }
  }

  def bestTimes(distance: Int): Seq[RunSlice] = {
    // We ignore repeated times in the same run.
    allTimesForDistance(distance).groupBy(_.duration).values.map(_.min).toSeq.sorted
  }

  def bestTime(distance: Int): Option[RunSlice] =
    bestTimes(distance).headOption
}

object Run {
  def fetch(client: ScravaClient, activity: PersonalDetailedActivity): Run = {
    val timeDistance: Seq[Streams] = RateLimiter {
      client.retrieveActivityStream(activity.id.toString, Some("time,distance"))
    }
    val times     = timeDistance(0).data.map(_.asInstanceOf[Int]).toArray
    val distances = timeDistance(1).data.map(_.asInstanceOf[Float].round).toArray
    val datetime  = LocalDateTime.parse(activity.start_date, DateTimeFormatter.ISO_DATE_TIME)

    new Run(activity.id, datetime, times, distances)
  }
}

case class TimeSpan(start: LocalDateTime, end: LocalDateTime)

/** Collection of runs.  This is always sorted chronologically. */
class Runs private (runSet: Seq[Run]) extends Traversable[Run] {
  def dropAfter(dateTime: LocalDateTime): Runs =
    new Runs(runSet.takeWhile(_.datetime.compareTo(dateTime) <= 0))

  def stats: Option[Runs.Stats] =
    if (runSet.isEmpty) {
      None
    } else {
      Some {
        Runs.Stats(
          maxDistance = runSet.map(_.totalDistance).max
        )
      }
    }

  def timeSpan: Option[TimeSpan] = for {
    first <- runSet.headOption
    last  <- runSet.lastOption
  } yield TimeSpan(first.datetime, last.datetime)

  def runHistory: Seq[Runs] =
    runSet.inits.map(new Runs(_)).toSeq.reverse
      .drop(1) // Drop empty

  override def foreach[U](f: (Run) => U): Unit =
    runSet.foreach(f)
}

object Runs {
  val empty: Runs = apply(Set.empty)

  def apply(runSet: Set[Run]): Runs =
    new Runs(runSet.toSeq.sortBy(_.datetime.toEpochSecond(ZoneOffset.UTC)))

  case class Stats(
    maxDistance: Int
  )
}
