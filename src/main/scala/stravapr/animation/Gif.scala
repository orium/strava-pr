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

package stravapr.animation

import java.io.File

import scala.collection.JavaConverters._

object Gif {
  def createGif(
    gifFilename: File,
    images: Seq[File],
    delay: Int,
    loop: Option[Int] = None
  ): Unit = {
    val cmd = Seq("convert", "-delay", delay.toString, "-loop", loop.getOrElse(0).toString) ++
      images.map(_.toString) ++ Seq(gifFilename.toString)

    val process =
      new ProcessBuilder(cmd.asJava)
        .start()

    process.waitFor()
  }
}
