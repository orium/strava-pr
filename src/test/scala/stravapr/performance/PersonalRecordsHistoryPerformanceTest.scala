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

package stravapr.performance

import org.scalatest.FlatSpec
import stravapr.RunTestUtils
import stravapr.Utils.fromTo

class PersonalRecordsHistoryPerformanceTest extends FlatSpec {
  private val RunDistance: Int = 2000

  behavior of "Personal records history performance"

  ignore must "scale nicely with the number of runs (informative only)" in {
    info(f"  # runs      time")

    fromTo(100, 1000, step = 100) foreach { runNumber =>
      val runs = RunTestUtils.dummyRuns(runNumber, RunDistance)
      val distances = fromTo(0, RunDistance, step = 25)

      val duration = Benchmarker.benchmark {
        runs.runHistory.personalRecordsHistory foreach { h =>
          distances foreach { d =>
            h.personalRecords.bestTime(d)
          }
        }
      }

      info(f"    $runNumber%4d   ${duration.toMillis}%8d ms")
    }
  }
}
