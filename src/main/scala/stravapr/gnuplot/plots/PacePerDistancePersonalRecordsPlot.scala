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

import stravapr.Utils.{RichDuration, fromTo}
import stravapr.gnuplot.plots.PacePerDistancePersonalRecordsPlot.Config
import stravapr.gnuplot.Plot
import stravapr.{Pace, Records, Run, Runs}

import scala.concurrent.duration._

case class PacePerDistancePersonalRecordsPlot(
  recordsSet: Set[Records],
  config: Config
) extends Plot {
  def withConfig(newConfig: Config): PacePerDistancePersonalRecordsPlot =
    copy(config = newConfig)

  override protected def gnuplotScript(dataFiles: Map[String, File]): Seq[String] = {
    val preamble =
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
         |set ytics  10
         |set xtics 500
         |
         |set xrange [${config.plotMinDistance}:${config.plotMaxDistance(recordsSet)}]
         |set yrange [${config.plotMinTime.map(_.toSeconds).getOrElse("")}:${config.plotMaxTime.map(_.toSeconds).getOrElse("")}]
         |set offset graph 0, graph 0, graph .05, graph .05
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
       """.stripMargin.lines.toSeq

    val plotCmd = dataFiles.values
      .map(f => s""""$f" using 1:4:6 ls 1 with lines""")
      .mkString("plot ", ", \\\n  ", "").lines

    preamble ++ plotCmd
  }

  private lazy val distances: Seq[Int] = {
    val allRuns = recordsSet.flatMap(_.runs)

    val d = fromTo(config.plotMinDistance, config.plotMaxDistance(recordsSet), config.distanceStep) ++
      // We also add the distances of each run.  This will make the gnuplot line interpolation not do weird things.
      allRuns.map(_.stats.distance)

    d.toSet.toArray.sorted
  }

  private def dataRows(records: Records): Seq[String] = {
    val header = "# distance      time       pace      pace secs     date     color"

    val data = distances flatMap { distance =>
      records.bestTime(distance) map { bestTime =>
        val duration = bestTime.duration.formatHMS.filterNot(_.isSpaceChar)
        val date     = bestTime.run.date
        val pace     = bestTime.pace

        val color = Math.abs(bestTime.run.datetime.hashCode()) % 12

        f"$distance%10d   $duration%9s   $pace%8s  ${pace.durationPerKm.toSeconds}%10d  $date%6s     $color"
      }
    }

    header +: data
  }

  lazy val (minPace: Pace, maxPace: Pace) = {
    val paces: Set[Pace] = recordsSet flatMap { records =>
      distances flatMap { distance =>
        records.bestTime(distance).map(_.pace)
      }
    }

    (paces.min, paces.max)
  }

  override protected def data: Set[Plot.DataFileContent] = {
    recordsSet.map(dataRows).zipWithIndex map { case (rows, i) =>
      Plot.DataFileContent(
        alias = s"plot-pr-pace-$i",
        rows = rows
      )
    }
  }
}

object PacePerDistancePersonalRecordsPlot {
  val Version = 2

  // Start at 100 meters since it is unlikely that we have accurate GPS information for such short distances.
  private val DefaultStartDistance: Int = 500
  private val DefaultDistanceStep: Int = 25

  case class Config(
    plotMinTime: Option[Duration] = None,
    plotMaxTime: Option[Duration] = None,
    plotMinDistance: Int = DefaultStartDistance,
    plotMaxDistanceOpt: Option[Int] = None,
    distanceStep: Int = DefaultDistanceStep
  ) {
    def plotMaxDistance(personalRecordsSet: Set[Records]): Int =
      plotMaxDistanceOpt.getOrElse {
        personalRecordsSet.map(_.runs.stats.maxDistance).max
      }
  }

  object Config {
    val Default: Config = Config()
  }

  def fromRuns(
    runs: Runs,
    config: Config = Config.Default
  ): PacePerDistancePersonalRecordsPlot =
    fromPersonalRecords(runs.personalRecords, config)

  def fromPersonalRecords(
    personalRecords: Records,
    config: Config = Config.Default
  ): PacePerDistancePersonalRecordsPlot =
    fromMultiplePersonalRecords(Set(personalRecords), config)

  def fromMultipleRuns(
    runsSet: Set[Runs],
    config: Config = Config.Default
  ): PacePerDistancePersonalRecordsPlot =
    fromMultiplePersonalRecords(runsSet.map(_.personalRecords), config)

  def fromMultiplePersonalRecords(
    personalRecordsSet: Set[Records],
    config: Config = Config.Default
  ): PacePerDistancePersonalRecordsPlot =
    new PacePerDistancePersonalRecordsPlot(personalRecordsSet, config)

  def fromMultiplePersonalRecordsAndRun(
    personalRecords: Records,
    run: Run,
    config: Config = Config.Default
  ): PacePerDistancePersonalRecordsPlot =
    fromMultiplePersonalRecords(Set(personalRecords, run.records), config)
}
