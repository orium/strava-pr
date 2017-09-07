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

package stravapr.gnuplot.plots

import java.io.File

import stravapr.Utils.RichDuration
import stravapr.gnuplot.{DataFileContent, Plot}
import stravapr.{PersonalRecords, Run, Runs}

class PacePerDistancePersonalRecordsPlot private (personalRecords: PersonalRecords) extends Plot {
  override protected def gnuplotScript(dataFiles: Map[String, File]): Seq[String] =
    s"""set title "Pace by distance for personal records"
       |set ylabel "pace (m:s/km)"
       |set xlabel "distance (m)"
       |
       |set grid ytics xtics
       |
       |set ydata time
       |set timefmt "%s"
       |
       |set ytics  15
       |set xtics 500
       |
       |set yrange [120:] # TODO configuration
       |set offset graph 0, graph 0, graph .05, graph 0
       |
       |plot "${dataFiles("plot-pr-pace")}" using 1:4 ls 1 title "pace" with lines rgb "red"
       |
     """.stripMargin.lines.toSeq

  private def dataRows: Seq[String] = {
    val header = "# distance      time       pace     pace secs     date"

    personalRecords.distances flatMap { distance =>
      val bestTimes = personalRecords.bestTimes(distance)

      if (bestTimes.nonEmpty) {
        val bestTime = bestTimes.bestTime.get
        val duration = bestTime.duration.formatHMS().filterNot(_.isSpaceChar)
        val date     = bestTime.run.date
        val pace     = bestTime.pace

        Some(f"$distance%10d   $duration%9s   $pace%8s  ${pace.durationPerKm.toSeconds}%10d  $date%6s")
      } else {
        None
      }
    }
  }

  override protected def data: Set[DataFileContent] = Set(
    DataFileContent(
      alias = "plot-pr-pace",
      rows = dataRows
    )
  )
}

object PacePerDistancePersonalRecordsPlot {
  // Start at 200 meters since it is unlikely that we have accurate GPS information for such short distances.
  private val StartDistance: Int = 200
  private val DefaultDistanceStep: Int = 25

  private def fromTo(start: Int, end: Int, step: Int = 1): Seq[Int] =
    Stream.iterate(start)(_ + step).takeWhile(_ <= end)

  def apply(runs: Runs, distanceStep: Int = DefaultDistanceStep): PacePerDistancePersonalRecordsPlot = {
    val distances = fromTo(500, 1000000, 50)
    val personalRecords = PersonalRecords.fromRuns(runs, distances, showNBest = 1, onlyBestOfEachRun = false)

    new PacePerDistancePersonalRecordsPlot(personalRecords)
  }
}