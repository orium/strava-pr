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

sealed trait BestTimes extends Traversable[RunSlice] {
  def distance: Int
  def bestTime: Option[RunSlice]

  def merge(other: BestTimes): BestTimes =
    if (other.distance == distance) {
      new BestTimes.Merged(distance, this, other)
    } else {
      this
    }
}

object BestTimes {
  private class Base(
    override val distance: Int,
    runSlices: Array[RunSlice]
  ) extends BestTimes {
    private lazy val bestTimes: Array[RunSlice] =
      runSlices.sorted

    override lazy val bestTime: Option[RunSlice] =
      if (runSlices.nonEmpty) Some(runSlices.min) else None

    override def foreach[U](f: (RunSlice) => U): Unit = bestTimes.foreach(f)
  }

  private class Merged(
    override val distance: Int,
    parent1: BestTimes,
    parent2: BestTimes
  ) extends BestTimes {
    private lazy val bestTimes: Array[RunSlice] = {
      // Using a set removes possible duplicates.
      (parent1.toSet ++ parent2)
        .toArray
        .sorted
    }

    override lazy val bestTime: Option[RunSlice] =
      (parent1.bestTime, parent2.bestTime) match {
        case (None, b2) => b2
        case (b1, None) => b1
        case (Some(b1), Some(b2)) => Some(implicitly[Ordering[RunSlice]].min(b1, b2))
      }

    override def foreach[U](f: (RunSlice) => U): Unit = bestTimes.foreach(f)
  }

  def apply(distance: Int, runSlices: Array[RunSlice]): BestTimes =
    new Base(distance, runSlices)

  def empty(distance: Int): BestTimes =
    BestTimes(distance, Array.empty)

  def fromRunSlice(runSlice: RunSlice): BestTimes =
    BestTimes(runSlice.distance, Array(runSlice))

  def fromRun(run: Run, distance: Int): BestTimes = {
    val runSlices = run.runSlices(distance)
      // Within the same run it is silly to keep the same time record multiple times.  We drop those duplicates.
      .groupBy(_.duration).values.map(_.min)

    BestTimes(distance, runSlices.toArray)
  }

  def fromRuns(runs: Runs, distance: Int): BestTimes =
    runs.map(fromRun(_, distance))
      .foldLeft(BestTimes.empty(distance))(_ merge _)
}

sealed trait Records {
  var bestTimeCache: Map[Int, Option[RunSlice]] = Map.empty
  var bestTimesCache: Map[Int, BestTimes] = Map.empty

  def runs: Runs

  final def bestTime(distance: Int): Option[RunSlice] =
    bestTimeCache.getOrElse(distance, {
      val bestTime = computeBestTime(distance)

      bestTimeCache = bestTimeCache + (distance -> bestTime)

      bestTime
    })

  final def bestTimes(distance: Int): BestTimes =
    bestTimesCache.getOrElse(distance, {
      val bestTimes = computeBestTimes(distance)

      bestTimesCache = bestTimesCache + (distance -> bestTimes)

      bestTimes
    })

  def computeBestTime(distance: Int): Option[RunSlice]
  def computeBestTimes(distance: Int): BestTimes

  def merge(other: Records): Records = {
    new Records.Merged(this, other)
  }

  override def equals(o: Any) = o match {
    case other: Records => runs == other.runs
    case _ => false
  }
  override def hashCode = runs.hashCode
}

object Records {
  private class Base(
    override val runs: Runs
  ) extends Records {
    def computeBestTime(distance: Int): Option[RunSlice] =
      bestTimes(distance).bestTime

    def computeBestTimes(distance: Int): BestTimes =
      if (distance > runs.stats.maxDistance) {
        BestTimes.empty(distance)
      } else {
        runs
          .map(BestTimes.fromRun(_, distance))
          // We only consider the best time of each run.
          .flatMap(_.bestTime)
          .map(BestTimes.fromRunSlice)
          .foldLeft(BestTimes.empty(distance))(_ merge _)
      }
  }

  private class Merged(
    parent1: Records,
    parent2: Records
  ) extends Records {
    override val runs: Runs = parent1.runs merge parent2.runs

    def computeBestTime(distance: Int): Option[RunSlice] =
      (parent1.bestTime(distance), parent2.bestTime(distance)) match {
        case (None, b2) => b2
        case (b1, None) => b1
        case (Some(b1), Some(b2)) => Some(implicitly[Ordering[RunSlice]].min(b1, b2))
      }

    def computeBestTimes(distance: Int): BestTimes =
      parent1.bestTimes(distance) merge parent2.bestTimes(distance)
  }

  val empty: Records = new Records.Base(Runs.empty)

  def fromRun(run: Run): Records =
    fromRuns(Runs(Set(run)))

  def fromRuns(runs: Runs): Records =
    new Records.Base(runs)
}
