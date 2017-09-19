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

package stravapr.plot

import java.io.{File, PrintWriter}


case class Resolution(columns: Int, lines: Int)

object Resolution {
  val Resolution720  = Resolution(1280, 720)
  val Resolution1080 = Resolution(1920, 1080)
  val Resolution1440 = Resolution(2560, 1440)
  val Resolution4K   = Resolution(3840, 2160)
}

trait Plot {
  protected type ComprehensionRepType

  private def createImageGnuplotHeader(imageFilename: File, resolution: Resolution): Seq[String] =
    Seq(
      s"set terminal pngcairo size ${resolution.columns},${resolution.lines}",
      s"set output '$imageFilename'"
    )

  private def pauseForEnterTail: Seq[String] =
    Seq("pause -1 \"Hit return to continue\"")

  protected def gnuplotScript(dataFiles: Map[String, File], comprehensionsMap: Map[String, ComprehensionRepType]): Seq[String]

  protected def data: Set[Plot.Data[ComprehensionRepType]]

  private def dumpToTempFile(lines: Seq[String], filenamePrefix: String, filenameSuffix: String): File = {
    val file = File.createTempFile(filenamePrefix, filenameSuffix)
    file.deleteOnExit()

    val p = new PrintWriter(file)
    try {
      lines.foreach(p.println)
    } finally {
      p.close()
    }

    file
  }

  private def createGnuplotFile(plotType: Plot.Type): File = {
    val aliasFileMap: Map[String, File] = data.collect { case Plot.Data.Enumeration(alias, rows) =>
      alias -> dumpToTempFile(rows, s"strava-pr-plot-data-$alias-", ".dat")
    }.toMap
    val comprehensionsMap = data.collect { case Plot.Data.Comprehension(alias, d) =>
      alias -> d
    }.toMap

    val scriptBody = gnuplotScript(aliasFileMap, comprehensionsMap)

    val completeScript = plotType match {
      case Plot.Type.CreateImage(imageFilename, resolution) =>
        createImageGnuplotHeader(imageFilename, resolution) ++ scriptBody

      case Plot.Type.Show =>

        scriptBody ++ pauseForEnterTail
    }

    dumpToTempFile(completeScript, "strava-pr-plot-", ".gnuplot")
  }

  def createPNGImage(imageFilename: File, resolution: Resolution): Unit = {
    val gnuplotFile = createGnuplotFile(Plot.Type.CreateImage(imageFilename, resolution))
    runGnuplot(gnuplotFile)
  }

  def createPNGImage(resolution: Resolution): File = {
    val pngFile = File.createTempFile("strava-pr-", ".png")
    createPNGImage(pngFile, resolution)
    pngFile
  }

  private def runGnuplot(gnuplotFile: File): Unit = {
    val gnuplotProcess = new ProcessBuilder("gnuplot", gnuplotFile.toString)
      .start()

    gnuplotProcess.waitFor()
  }

  def show(): Unit = {
    val gnuplotFile = createGnuplotFile(Plot.Type.Show)
    runGnuplot(gnuplotFile)
  }
}

object Plot {
  sealed trait Type
  object Type {
    case object Show extends Type
    case class  CreateImage(imageFilename: File, resolution: Resolution) extends Type
  }

  sealed trait Data[+C] {
    def alias: String
  }
  object Data {
    case class Enumeration(alias: String, rows: Seq[String]) extends Data[Nothing]
    case class Comprehension[C](alias: String, data: C)      extends Data[C]
  }
}
