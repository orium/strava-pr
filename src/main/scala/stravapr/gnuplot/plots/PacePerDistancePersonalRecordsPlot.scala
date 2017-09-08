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
import stravapr.gnuplot.plots.PacePerDistancePersonalRecordsPlot.fromTo
import stravapr.gnuplot.{DataFileContent, Plot}
import stravapr.{PersonalRecords, Runs}

import scala.concurrent.duration._

class PacePerDistancePersonalRecordsPlot private (
  personalRecords: PersonalRecords,
  plotMinTime: Duration = 2.minutes,
  plotMaxTime: Option[Duration] = None,
  plotMinDistance: Int = PacePerDistancePersonalRecordsPlot.DefaultStartDistance,
  plotMaxDistance: Int,
  distanceStep: Int = PacePerDistancePersonalRecordsPlot.DefaultDistanceStep
) extends Plot {
  override protected def gnuplotScript(dataFiles: Map[String, File]): Seq[String] =
    s"""set title "Pace by distance for personal records"
       |
       |set nokey
       |
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
       |set xrange [$plotMinDistance:$plotMaxDistance]
       |set yrange [${plotMinTime.toSeconds}:${plotMaxTime.map(_.toSeconds).getOrElse("")}]
       |set offset graph 0, graph 0, graph .1, graph 0
       |
       |set palette maxcolors 12
       |set palette defined ( \\
       |   0 '#a6cee3', \\
       |   1 '#1f78b4', \\
       |   2 '#b2df8a', \\
       |   3 '#33a02c', \\
       |   4 '#fb9a99', \\
       |   5 '#e31a1c', \\
       |   6 '#fdbf6f', \\
       |   7 '#ff6f00', \\
       |   8 '#cab2d6', \\
       |   9 '#6a3d9a', \\
       |  10 '#aaaa55', \\
       |  11 '#b15928'  \\
       |)
       |
       |set cbrange [0:12]
       |unset colorbox
       |
       |set style line 1 palette
       |set style line 1 linewidth 3
       |
       |plot "${dataFiles("plot-pr-pace")}" using 1:4:6 ls 1 with lines
     """.stripMargin.lines.toSeq

  private def dataRows: Seq[String] = {
    val header = "# distance      time       pace      pace secs     date     color"
    val distances = fromTo(plotMinDistance, plotMaxDistance, distanceStep)

    val data = distances flatMap { distance =>
      personalRecords.bestTime(distance) map { bestTime =>
        val duration = bestTime.duration.formatHMS().filterNot(_.isSpaceChar)
        val date     = bestTime.run.date
        val pace     = bestTime.pace

        val color = Math.abs(bestTime.run.datetime.hashCode()) % 12

        f"$distance%10d   $duration%9s   $pace%8s  ${pace.durationPerKm.toSeconds}%10d  $date%6s     $color"
      }
    }

    header +: data
  }

  override protected def data: Set[DataFileContent] = Set(
    DataFileContent(
      alias = "plot-pr-pace",
      rows = dataRows
    )
  )
}

object PacePerDistancePersonalRecordsPlot {
  // Start at 100 meters since it is unlikely that we have accurate GPS information for such short distances.
  private val DefaultStartDistance: Int = 100
  private val DefaultDistanceStep: Int = 25

  private def fromTo(start: Int, end: Int, step: Int = 1): Seq[Int] =
    Stream.iterate(start)(_ + step).takeWhile(_ <= end)

  def fromRuns(
    runs: Runs,
    plotMinTime: Duration = 2.minutes,
    plotMaxTime: Option[Duration] = None,
    plotMinDistance: Int = DefaultStartDistance,
    plotMaxDistance: Option[Int] = None,
    distanceStep: Int = DefaultDistanceStep
  ): PacePerDistancePersonalRecordsPlot =
    fromPersonalRecord(
      PersonalRecords.fromRuns(runs),
      plotMinTime,
      plotMaxTime,
      plotMinDistance,
      plotMaxDistance,
      distanceStep
    )

  def fromPersonalRecord(
    personalRecords: PersonalRecords,
    plotMinTime: Duration = 2.minutes,
    plotMaxTime: Option[Duration] = None,
    plotMinDistance: Int = DefaultStartDistance,
    plotMaxDistance: Option[Int] = None,
    distanceStep: Int = DefaultDistanceStep
  ): PacePerDistancePersonalRecordsPlot = {
    val maxDistance = personalRecords.runs.stats.get.maxDistance

    new PacePerDistancePersonalRecordsPlot(
      personalRecords,
      plotMinTime,
      plotMaxTime,
      plotMinDistance,
      plotMaxDistance.getOrElse(maxDistance),
      distanceStep
    )
  }
}
