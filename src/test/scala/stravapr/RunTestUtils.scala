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

import java.time.{LocalDate, LocalDateTime, LocalTime}

import scala.util.Random
import stravapr.Utils.fromTo

object RunTestUtils {
  def dummyRuns(distance: Int): Run = {
    val distances = fromTo(0, distance, step = 4)
    val datetime = LocalDateTime.of(
      LocalDate.of(2000 + Random.nextInt(10), 1 + Random.nextInt(12), 1 + Random.nextInt(25)),
      LocalTime.of(4, 20, 0)
    )
    val times = Seq.iterate(0, distances.size)(_ + Random.nextInt(5))

    Run(
      id = Random.nextInt(Int.MaxValue),
      datetime,
      times,
      distances
    )
  }

  def dummyRuns(n: Int, distance: Int): Runs = {
    Runs(Seq.fill(n)(dummyRuns(distance)).toSet)
  }
}
