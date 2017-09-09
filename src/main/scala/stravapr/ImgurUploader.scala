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

import java.io._
import java.net.{HttpURLConnection, URL}
import java.nio.file.Files

import play.api.libs.json.Json

import scala.util.Try

class ImgurUploader(clientId: String) {
  def upload(imageFilename: File): Try[URL] = Try {
    val url = new URL("https://api.imgur.com/3/image")
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]

    connection.setDoOutput(true)
    connection.setDoInput(true)
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Authorization", "Client-ID " + clientId)
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "content-type: image/*")

    connection.connect()

    val writer = connection.getOutputStream

    Files.copy(imageFilename.toPath, writer)
    writer.close()

    connection.getResponseCode match {
      case 200 => // OK
      case 429 => throw ImgurUploader.RateLimitingExceeded
      case e   => throw ImgurUploader.HTTPException(e)
    }

    val photoURL = (Json.parse(connection.getInputStream) \ "data" \ "link").get.as[String]

    new URL(photoURL)
  }
}

object ImgurUploader {
  case object RateLimitingExceeded extends Exception
  case class HTTPException(status: Int) extends Exception
}