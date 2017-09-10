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

import java.time.{LocalDate, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

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

case class Run(
  id: Int,
  datetime: LocalDateTime,
  times: Seq[Int],
  distances: Seq[Int]
) {
  require(distances.head == 0, "First distance must be zero")

  def date: LocalDate = datetime.toLocalDate

  private def interpolate(d1: Int, t1: Int, d2: Int, t2: Int, d: Int): Int = {
    val m = (t1 - t2).toDouble / (d1 - d2).toDouble
    val b = t2 - m * d2

    (m * d + b).round.toInt
  }

  private lazy val timeAtDistance: Array[Duration] = {
    val t = 0 +:
      (distances zip times).toArray.sliding(2).flatMap { case Array((distanceBefore, timeBefore), (distanceAfter, timeAfter)) =>
        ((distanceBefore + 1) to distanceAfter) map { distance =>
          interpolate(distanceBefore, timeBefore, distanceAfter, timeAfter, distance)
        }
      }.toSeq

    t.map(Duration(_, TimeUnit.SECONDS)).toArray
  }

  def timeAt(distance: Int): Option[Duration] =
    if (0 <= distance && distance <= stats.distance) Some(timeAtDistance(distance)) else None

  def stats: Run.Stats = Run.Stats(
    distance = distances.last
  )

  def records: Records = Records.fromRuns(Runs(this))

  def runSlices(distance: Int): Seq[RunSlice] = {
    (0 to (stats.distance - distance)).map { startDistance =>
      val time = timeAt(startDistance + distance).get - timeAt(startDistance).get

      RunSlice(distance, startDistance, time, this)
    }
  }
}

object Run {
  case class Stats(
    distance: Int
  )
}

case class TimeSpan(start: LocalDateTime, end: LocalDateTime)

/** Collection of runs.  This is always sorted chronologically. */
class Runs private (runs: Seq[Run]) extends Traversable[Run] {
  lazy val runMap: Map[Int, Run] = runs.map(r => r.id -> r).toMap

  def get(runId: Int): Option[Run] =
    runMap.get(runId)

  def dropAfter(dateTime: LocalDateTime): Runs =
    new Runs(runs.takeWhile(_.datetime.compareTo(dateTime) <= 0))

  def stats: Runs.Stats =
    Runs.Stats(
      maxDistance = runs.map(_.stats.distance).foldLeft(0)(_ max _)
    )

  def timeSpan: Option[TimeSpan] = for {
    first <- runs.headOption
    last  <- runs.lastOption
  } yield TimeSpan(first.datetime, last.datetime)

  def runHistory: RunHistory = new RunHistory(this)

  def personalRecords: Records = Records.fromRuns(this)

  def merge(other: Runs): Runs =
    Runs(runs.toSet ++ other.toSet)

  override def foreach[U](f: (Run) => U): Unit =
    runs.foreach(f)
}

object Runs {
  val empty: Runs = apply(Set.empty[Run])

  def apply(run: Run): Runs = apply(Set(run))

  def apply(runSet: Set[Run]): Runs =
    new Runs(runSet.toSeq.sortBy(_.datetime.toEpochSecond(ZoneOffset.UTC)))

  case class Stats(
    maxDistance: Int
  )
}

class RunHistory(runs: Runs) {
  def runHistory: Seq[Runs] =
    runs.inits.map(runs => Runs(runs.toSet)).toSeq.reverse
      .drop(1) // Drop empty

  def personalRecordsHistory: Seq[RunHistory.PersonalRecordsAtRun] = {
    // We use personal record merge here since it will re-use results from runs subsets thus avoiding extra computation.
    runs.foldLeft(Vector.empty[RunHistory.PersonalRecordsAtRun]) { case (personalRecordsList, run) =>
      val previousPersonalRecords: Records = personalRecordsList.lastOption.map(_.personalRecords).getOrElse(Records.empty)
      val runRecords = run.records
      val personalRecords: Records = previousPersonalRecords merge runRecords

      val personalRecordsAtRun = RunHistory.PersonalRecordsAtRun(
        run,
        runRecords,
        previousPersonalRecords,
        personalRecords
      )

      personalRecordsList :+ personalRecordsAtRun
    }
  }
}

object RunHistory {
  case class PersonalRecordsAtRun(
    run: Run,
    runRecords: Records,
    previousPersonalRecords: Records,
    personalRecords: Records
  )
}
