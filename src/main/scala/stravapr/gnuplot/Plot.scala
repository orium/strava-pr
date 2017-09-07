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

package stravapr.gnuplot

import java.io.{File, PrintWriter}

case class DataFileContent(alias: String, rows: Seq[String])

trait Plot {
  private def createImageGnuplotHeader(imageFilename: File): Seq[String] =
    Seq(
      "set terminal png size 1920,1080", // TODO resolution by configuration.
      s"set output '$imageFilename'"
    )

  private def pauseForEnterTail: Seq[String] =
    Seq("pause -1 \"Hit return to continue\"")

  protected def gnuplotScript(dataFiles: Map[String, File]): Seq[String]

  protected def data: Set[DataFileContent]

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
    val aliasFileMap: Map[String, File] = data.map { case DataFileContent(alias, rows) =>
      alias -> dumpToTempFile(rows, s"strava-pr-plot-data-$alias", ".dat")
    }.toMap

    val scriptBody = gnuplotScript(aliasFileMap)

    val completeScript = plotType match {
      case Plot.Type.CreateImage(imageFilename) =>
        createImageGnuplotHeader(imageFilename) ++ scriptBody

      case Plot.Type.Show =>

        scriptBody ++ pauseForEnterTail
    }

    dumpToTempFile(completeScript, "strava-pr-plot", ".gnuplot")
  }

  def createPNGImage(imageFilename: File): Unit = {
    val gnuplotFile = createGnuplotFile(Plot.Type.CreateImage(imageFilename))
    runGnuplot(gnuplotFile)
  }

  def createPNGImage(): File = {
    val pngFile = File.createTempFile("strava-pr", ".png")
    createPNGImage(pngFile)
    pngFile
  }

  private def runGnuplot(gnuplotFile: File): Unit = {
    val gnuplotProcess = new ProcessBuilder("gnuplot", gnuplotFile.toString)
      .start()

    gnuplotProcess.waitFor()
  }

  def showPlot(): Unit = {
    val gnuplotFile = createGnuplotFile(Plot.Type.Show)
    runGnuplot(gnuplotFile)
  }
}

object Plot {
  sealed trait Type
  object Type {
    case object Show extends Type
    case class  CreateImage(imageFilename: File) extends Type
  }
}
