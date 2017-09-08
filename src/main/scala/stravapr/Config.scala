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

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.util.Try

case class Config(
  accessToken: String,
  imgurClientId: Option[String],
  showNBest: Int,
  prDistances: Seq[Int]
)

object Config {
  val DefaultConfigFileContent: String =
    s"""auth-token = "put a token here"
       |
       |# imgur-client-id = "put a client here"
       |
       |show-n-best = 5
       |
       |pr-distances = [
       |  1000,
       |  1500,
       |  1610,
       |  3000,
       |  3219,
       |  5000,
       |  7500,
       |  10000,
       |  16094,
       |  15000,
       |  20000,
       |  21098,
       |  30000,
       |  42195,
       |  50000,
       |  80468,
       |  100000,
       |  160935
       |]
     """.stripMargin

  def fromFile(configFile: File): Try[Config] = Try {
    val c = ConfigFactory.parseFile(configFile)

    val authToken     = c.getString("auth-token")
    val imgurClientId = if (c.hasPath("imgur-client-id")) Some(c.getString("imgur-client-id")) else None
    val showNBest     = c.getInt("show-n-best")
    val prDistances   = c.getNumberList("pr-distances").asScala.map(_.intValue())

    Config(authToken, imgurClientId, showNBest, prDistances)
  }
}
