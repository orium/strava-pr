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

package stravapr.plot.plots

import java.io.File

import stravapr.Utils.{RichDuration, fromTo}
import stravapr._
import stravapr.analysis.AveragePacePerDistanceRegression
import stravapr.analysis.AveragePacePerDistanceRegression.LogarithmicRegression
import stravapr.plot.Plot
import stravapr.plot.plots.PacePerDistancePersonalRecordsPlot.Config

import scala.concurrent.duration._

case class PacePerDistancePersonalRecordsPlot(
  recordsSet: Set[Records],
  config: Config
) extends Plot {
  protected case class AvgPaceCurve(logRegression: LogarithmicRegression, maxDistance: Int) {
    def toGnuplotFunction: String =
      s"(x <= $maxDistance) ? ${logRegression.a} + ${logRegression.b} * log(x) : 1/0"
  }

  override type ComprehensionRepType = AvgPaceCurve

  def withConfig(newConfig: Config): PacePerDistancePersonalRecordsPlot =
    copy(config = newConfig)

  override protected def gnuplotScript(
    dataFiles: Map[String, File],
    comprehensionsMap: Map[String, ComprehensionRepType]
  ): Seq[String] = {
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
         |set yrange [${config.plotMaxTime(this).toSeconds}:${config.plotMinTime(this).toSeconds}]
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
         |# For the PR curves
         |set style line 1 palette
         |set style line 1 linewidth 3
         |
         |# For the average curves
         |set style line 2 dashtype "."
         |set style line 2 linewidth 2
         |set style line 2 linecolor rgb "#505050"
       """.stripMargin.lines.toSeq

    val plotCmdLinesPrCurve = dataFiles.values
      .map(f => s""""$f" using 1:4:6 linestyle 1 with lines""")
    val plotCmdLineAvgCurve = comprehensionsMap.get("average-curve")
        .map(f => s"${f.toGnuplotFunction} linestyle 2")

    val plotCmd = (plotCmdLineAvgCurve ++ plotCmdLinesPrCurve)
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

    val data = distances.flatMap { distance =>
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

  private def averagePaceRegression(records: Records): LogarithmicRegression = {
    val distancePaces: Map[Int, Pace] = distances.flatMap { distance =>
      records.bestTime(distance).map(distance -> _.pace)
    }.toMap

    AveragePacePerDistanceRegression.regression(distancePaces)
  }

  lazy val (minPace: Pace, maxPace: Pace) = {
    val paces: Set[Pace] = recordsSet flatMap { records =>
      distances flatMap { distance =>
        records.bestTime(distance).map(_.pace)
      }
    }

    (paces.min, paces.max)
  }

  override protected def data: Set[Plot.Data[ComprehensionRepType]] = {
    val runsCurves: Set[Plot.Data[ComprehensionRepType]] = recordsSet.map(dataRows).zipWithIndex map { case (rows, i) =>
      Plot.Data.Enumeration(
        alias = s"pr-curve-$i",
        rows = rows
      )
    }
    val avgRegression: Option[Plot.Data[ComprehensionRepType]] = config.avgCurveOfRecords.map { records =>
      Plot.Data.Comprehension(
        alias = "average-curve",
        AvgPaceCurve(averagePaceRegression(records), records.runs.stats.maxDistance)
      )
    }

    runsCurves ++ avgRegression
  }
}

object PacePerDistancePersonalRecordsPlot {
  val Version: Int = 5

  // Start at here since it is unlikely that we have accurate GPS information for such short distances.
  private val DefaultStartDistance: Int = 400
  private val DefaultDistanceStep: Int = 25

  case class Config(
    plotMinTimeOpt: Option[Duration] = None,
    plotMaxTimeOpt: Option[Duration] = None,
    plotMinDistance: Int = DefaultStartDistance,
    plotMaxDistanceOpt: Option[Int] = None,
    avgCurveOfRecords: Option[Records] = None,
    distanceStep: Int = DefaultDistanceStep
  ) {
    def withAvgCurveOfRecords(avgCurveOfRecords: Records): Config =
      this.copy(avgCurveOfRecords = Some(avgCurveOfRecords))

    def plotMinTime(plot: PacePerDistancePersonalRecordsPlot): Duration =
      plotMinTimeOpt.getOrElse(plot.minPace.durationPerKm)

    def plotMaxTime(plot: PacePerDistancePersonalRecordsPlot): Duration =
      plotMaxTimeOpt.getOrElse(plot.maxPace.durationPerKm)

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
