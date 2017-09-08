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

class BestTimes private (val distance: Int, bestTimes: Array[RunSlice]) extends Traversable[RunSlice] {
  def bestTime: Option[RunSlice] = bestTimes.headOption

  def merge(other: BestTimes): BestTimes =
    if (other.distance == distance) {
      val runSlices = (bestTimes ++ other)
          .toSet // Remove possible repetitions (e.g. if adding a bestTimes already here)

      new BestTimes(distance, runSlices.toArray.sorted)
    } else {
      this
    }

  override def foreach[U](f: (RunSlice) => U): Unit = bestTimes.foreach(f)
}

object BestTimes {
  def empty(distance: Int): BestTimes =
    new BestTimes(distance, Array.empty)

  def fromRunSlice(runSlice: RunSlice): BestTimes =
    new BestTimes(runSlice.distance, Array(runSlice))

  def fromRun(run: Run, distance: Int): BestTimes = {
    val runSlices = run.runSlices(distance)
      // Within the same run it is silly to keep have the same time record multiple times.  We drop those duplicates.
      .groupBy(_.duration).values.map(_.min)

    new BestTimes(distance, runSlices.toArray.sorted)
  }

  def fromRuns(runs: Runs, distance: Int): BestTimes =
    runs.map(fromRun(_, distance))
      .foldLeft(BestTimes.empty(distance))(_ merge _)
}

class PersonalRecords private (val runs: Runs) {
  private var prMap: Map[Int, BestTimes] = Map.empty

  def bestTime(distance: Int): Option[RunSlice] = bestTimes(distance).headOption

  def bestTimes(distance: Int): BestTimes =
    if (distance > runs.stats.maxDistance) {
      BestTimes.empty(distance)
    } else {
      prMap.getOrElse(distance, {
        val bestTimes = runs.map(BestTimes.fromRun(_, distance))
          // We only consider the best time of each run.
          .flatMap(_.headOption)
          .map(BestTimes.fromRunSlice)
          .foldLeft(BestTimes.empty(distance))(_ merge _)

        prMap = prMap + (distance -> bestTimes)

        bestTimes
      })
    }

  def merge(other: PersonalRecords): PersonalRecords = {
    val mergedPrMap = (prMap.keySet ++ other.prMap.keySet).map { distance =>
      val t = bestTimes(distance)
      val o = other.bestTimes(distance)

      distance -> t.merge(o)
    }.toMap

    val mergedPr = new PersonalRecords(runs merge other.runs)

    mergedPr.prMap = mergedPrMap

    mergedPr
  }
}

object PersonalRecords {
  val empty: PersonalRecords = new PersonalRecords(Runs.empty)

  def fromRun(run: Run): PersonalRecords =
    new PersonalRecords(Runs(Set(run)))

  def fromRuns(runs: Runs): PersonalRecords =
    new PersonalRecords(runs)
}
