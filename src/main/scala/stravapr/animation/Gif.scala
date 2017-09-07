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

import java.awt.image.BufferedImage
import java.io.File

import com.sksamuel.scrimage.Image
import com.sksamuel.scrimage.nio.StreamingGifWriter

import scala.concurrent.duration.Duration

object Gif {
  def createGif(
    gifFilename: File,
    images: Seq[File],
    delay: Duration,
    loop: Boolean = false
  ): Unit = {
    val writer = StreamingGifWriter(delay, loop)
    val stream = writer.prepareStream(gifFilename, BufferedImage.TYPE_INT_ARGB)

    images.map(Image.fromFile).foreach(stream.writeFrame)

    stream.finish()
  }
}
