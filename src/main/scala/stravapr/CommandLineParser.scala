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

import java.io.File

import scopt.OptionParser
import stravapr.CommandLineParser.Defaults
import stravapr.gnuplot.Resolution

import scala.concurrent.duration._
import scala.util.Try

sealed trait Command
object Command {
  sealed trait Strava extends Command

  object Strava {
    case class Fetch(invalidateCache: Boolean) extends Command.Strava
    case class AddDescriptionHistory(
      resolution: Resolution,
      startDistance: Int,
      forceRefresh: Boolean
    ) extends Command.Strava
  }

  case object List extends Command

  case class Show(runNumber: Option[Int] = None, startDistance: Int, mode: Command.Show.Mode) extends Command
  object Show {
    sealed trait Mode
    object Mode {
      case object Display extends Mode
      case class CreateImage(imageFile: File, resolution: Resolution) extends Mode
    }
  }

  case class History(
    imageFile: File,
    startDistance: Int,
    frameDuration: Duration,
    resolution: Resolution,
    animationLoop: Boolean
  ) extends Command

  case class Table(bestN: Int, distances: Seq[Int]) extends Command

  sealed trait Type
  object Type {
    trait Strava extends Command.Type
    object Strava {
      object Fetch                 extends Command.Type.Strava
      object AddDescriptionHistory extends Command.Type.Strava
    }
    object List        extends Command.Type
    object Show        extends Command.Type
    object History     extends Command.Type
    object Table       extends Command.Type
  }

  case class Builder(
    cmdType: Option[Command.Type] = None,

    stravaFetchInvalidateCache: Boolean = false,

    stravaAddDescriptionHistoryResolution: Option[Resolution] = None,
    stravaAddDescriptionHistoryStartDistance: Option[Int] = None,
    stravaAddDescriptionHistoryForceRefresh: Boolean = false,

    showRunN: Option[Int] = None,
    showStartDistance: Option[Int] = None,
    showCreateImageFilename: Option[File] = None,
    showCreateImageResolution: Option[Resolution] = None,

    historyStartDistance: Option[Int] = None,
    historyFrameDuration: Option[Duration] = None,
    historyImageFilename: Option[File] = None,
    historyImageResolution: Option[Resolution] = None,
    historyAnimationLoop: Boolean = false,

    tableBestN: Option[Int] = None,
    tableDistances: Option[Seq[Int]] = None
  ) {
    def withCommandType(`type`: Command.Type): Command.Builder =
      this.copy(cmdType = Some(`type`))

    def withStravaFetchInvalidateCache(): Command.Builder =
      this.copy(stravaFetchInvalidateCache = true)

    def withStravaAddDescriptionHistoryResolution(resolution: Resolution): Command.Builder =
      this.copy(stravaAddDescriptionHistoryResolution = Some(resolution))

    def withStravaAddDescriptionHistoryStartDistance(startDistance: Int): Command.Builder =
      this.copy(stravaAddDescriptionHistoryStartDistance = Some(startDistance))

    def withStravaAddDescriptionHistoryForceRefresh(): Command.Builder =
      this.copy(stravaAddDescriptionHistoryForceRefresh = true)

    def withShowRunN(runN: Int): Command.Builder =
      this.copy(showRunN = Some(runN))

    def withShowStartDistance(startDistance: Int): Command.Builder =
      this.copy(showStartDistance = Some(startDistance))

    def withShowImageFile(file: File): Command.Builder =
      this.copy(showCreateImageFilename = Some(file))

    def withShowImageResolution(resolution: Resolution): Command.Builder =
      this.copy(showCreateImageResolution = Some(resolution))

    def withHistoryStartDistance(startDistance: Int): Command.Builder =
      this.copy(historyStartDistance = Some(startDistance))

    def withHistoryImageResolution(resolution: Resolution): Command.Builder =
      this.copy(historyImageResolution = Some(resolution))

    def withHistoryFrameDuration(frameDuration: Duration): Command.Builder =
      this.copy(historyFrameDuration = Some(frameDuration))

    def withHistoryAnimationLoop(): Command.Builder =
      this.copy(historyAnimationLoop = true)

    def withHistoryImageFile(file: File): Command.Builder =
      this.copy(historyImageFilename = Some(file))

    def withTableBestN(bestN: Int): Command.Builder =
      this.copy(tableBestN = Some(bestN))

    def withTableDistances(distances: Seq[Int]): Command.Builder =
      this.copy(tableDistances = Some(distances))

    def build: Option[Command] = cmdType.collect {
      case Command.Type.Strava.Fetch => Command.Strava.Fetch(stravaFetchInvalidateCache)
      case Command.Type.Strava.AddDescriptionHistory =>
        Command.Strava.AddDescriptionHistory(
          stravaAddDescriptionHistoryResolution.getOrElse(Defaults.Resolution),
          stravaAddDescriptionHistoryStartDistance.getOrElse(Defaults.MinimumDistancePlot),
          stravaAddDescriptionHistoryForceRefresh
        )
      case Command.Type.List        => Command.List
      case Command.Type.Show        => Command.Show(
        showRunN,
        showStartDistance.getOrElse(Defaults.MinimumDistancePlot),
        showCreateImageFilename match {
          case Some(imageFilename) => Command.Show.Mode.CreateImage(
            imageFilename,
            showCreateImageResolution.getOrElse(Defaults.Resolution)
          )
          case None => Command.Show.Mode.Display
        }
      )
      case Command.Type.History =>
        Command.History(
          historyImageFilename.get,
          historyStartDistance.getOrElse(Defaults.MinimumDistancePlot),
          historyFrameDuration.getOrElse(Defaults.History.FrameDuration),
          historyImageResolution.getOrElse(Defaults.Resolution),
          historyAnimationLoop
        )
      case Command.Type.Table => Command.Table(
        tableBestN.getOrElse(Defaults.Table.BestN),
        tableDistances.getOrElse(Defaults.Table.Distances)
      )
    }
  }

  object Builder {
    def empty: Command.Builder = Command.Builder()
  }
}

object CommandLineParser {
  val StravaPrVersion: String = getClass.getPackage.getImplementationVersion

  object Defaults {
    val Resolution: Resolution = stravapr.gnuplot.Resolution.Resolution1080
    val MinimumDistancePlot: Int = 400

    object History {
      val FrameDuration: Duration = 500.milliseconds
    }

    object Table {
      val BestN: Int = 5
      val Distances: Seq[Int] = Seq(
           400,
           805,
          1000,
          1500,
          1610,
          2000,
          3000,
          3219,
          5000,
          7500,
         10000,
         15000,
         16094,
         20000,
         21098,
         30000,
         42195,
         50000,
         80468,
        100000,
        160935
      )
    }
  }

  private implicit val resolutionReader: scopt.Read[Resolution] = new scopt.Read[Resolution] {
    override def arity: Int = 1

    override def reads: String => Resolution = { string =>
      val resolution = string.split("x").toSeq match {
        case Seq(columns, lines) => Try(Resolution(columns.toInt, lines.toInt)).toOption
        case _ => None
      }

      resolution.getOrElse {
        throw new IllegalArgumentException(s"""Resolution "$string" does not have the correct format (e.g. "1920x1080")""")
      }
    }
  }

  private val scoptParser = new OptionParser[Command.Builder]("strava-pr") {
    head("strava-pr", StravaPrVersion)

    version("version")
      .text("output version information and exit")

    help("help")
      .text("display this help and exit")

    note("") // Add an empty line to the help message.

    cmd("strava")
      .text("strava subcommands")
      .children(
        cmd("fetch")
          .text("fetch runs from strava")
          .action((_, b) => b.withCommandType(Command.Type.Strava.Fetch))
          .children(
            opt[Unit]("invalidate-cache")
              .text("do not use cache, fetch every run from strava")
              .optional
              .action((_, b) => b.withStravaFetchInvalidateCache())
          ),

        cmd("add-description-history")
          .text(
            "adds a plot to each strava run with the all-time record pace per distance at that point in time\n" +
              "against the record pace per distance of that run (the plot is added as a url appended to the\n" +
              "description of the run)"
          )
          .action((_, b) => b.withCommandType(Command.Type.Strava.AddDescriptionHistory))
          .children(
            opt[Unit]("force-refresh")
              .text("refresh all plots of all runs")
              .optional
              .action((_, b) => b.withStravaAddDescriptionHistoryForceRefresh()),
            opt[Resolution]("resolution")
              .text("resolution of the images")
              .valueName("<resolution>")
              .optional
              .action((resolution, b) => b.withStravaAddDescriptionHistoryResolution(resolution))
              .validate { resolution =>
                if (resolution.lines <= 0 || resolution.columns <= 0) failure("invalid resolution")
                else success
              },
            opt[Int]("start-distance")
              .text("the start distance in the plot")
              .valueName("<distance>")
              .optional
              .action((distance, b) => b.withStravaAddDescriptionHistoryStartDistance(distance))
              .validate { distance =>
                if (distance < 0) failure("distance must be non-negative")
                else success
              }
          )
    )

    note("") // Add an empty line to the help message.

    cmd("list")
      .action((_, b) => b.withCommandType(Command.Type.List))
      .text("list all runs")

    note("") // Add an empty line to the help message.

    cmd("show")
      .text("shows the all-time record pace per distance plot")
      .action((_, b) => b.withCommandType(Command.Type.Show))
      .children(
        opt[File]("to-file")
          .text("create an png image file instead of showing")
          .valueName("<png-image-file>")
          .optional
          .action((file, b) => b.withShowImageFile(file)),
        opt[Resolution]("resolution")
          .text("resolution in case of image creation")
          .valueName("<resolution>")
          .optional
          .action((resolution, b) => b.withShowImageResolution(resolution))
          .validate { resolution =>
            if (resolution.lines <= 0 || resolution.columns <= 0) failure("invalid resolution")
            else success
          },
        opt[Int]("start-distance")
          .text("the start distance in the plot")
          .valueName("<distance>")
          .optional
          .action((distance, b) => b.withShowStartDistance(distance))
          .validate { distance =>
            if (distance < 0) failure("distance must be non-negative")
            else success
          },
        arg[Int]("run#")
          .text("also shows the record pace per distance of the given run")
          .valueName("[run-number]")
          .optional
          .action((runN, b) => b.withShowRunN(runN))
          .validate { runN =>
            if (runN < 0) failure("run number must be non-negative")
            else success
          }
      )

    note("")

    cmd("history")
      .text("creates a gif animation for the all-time record pace per distance evolving through each run")
      .action((_, b) => b.withCommandType(Command.Type.History))
      .children(
        opt[Resolution]("resolution")
          .text("resolution of the animation")
          .valueName("<resolution>")
          .optional
          .action((resolution, b) => b.withHistoryImageResolution(resolution))
          .validate { resolution =>
            if (resolution.lines <= 0 || resolution.columns <= 0) failure("invalid resolution")
            else success
          },
        opt[Duration]("frame-duration")
          .text("duration of each frame")
          .valueName("<duration>")
          .optional
          .action((duration, b) => b.withHistoryFrameDuration(duration))
          .validate { duration =>
            if (duration <= Duration.Zero) failure("duration must be positive")
            else success
          },
        opt[Unit]("animation-loop")
          .text("whether the animation should loop")
          .optional
          .action((_, b) => b.withHistoryAnimationLoop()),
        opt[Int]("start-distance")
          .text("the start distance in the plot")
          .valueName("<distance>")
          .optional
          .action((distance, b) => b.withHistoryStartDistance(distance))
          .validate { distance =>
            if (distance < 0) failure("distance must be non-negative")
            else success
          },
        arg[File]("gif-image-file")
          .text("filename of the output gif")
          .required
          .action((file, b) => b.withHistoryImageFile(file))
      )

    note("")

    cmd("table")
      .text("shows a table with the personal records for the configured distances")
      .action((_, b) => b.withCommandType(Command.Type.Table))
      .children(
        opt[Seq[Int]]("distances")
          .text("")
          .valueName("<distances>")
          .optional
          .action((distances, b) => b.withTableDistances(distances.sorted) )
          .validate { distances =>
            if (distances.exists(_ <= 0)) failure("distances must be positive")
            else success
          },
        arg[Int]("best-n")
          .text("number of runs to show in each table")
          .optional
          .action((bestN, b) => b.withTableBestN(bestN))
          .validate { bestN =>
            if (bestN <= 0) failure("number of runs must be positive")
            else success
          }
      )

    checkConfig { b =>
      b.cmdType match {
        case Some(_) => success
        case None => failure("incomplete command")
      }
    }
  }

  def parse(args: Seq[String]): Option[Command] = {
    scoptParser.parse(args, Command.Builder.empty)
      .flatMap(_.build)
  }
}
