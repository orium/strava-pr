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

import scala.concurrent.duration.Duration

object Utils {
  implicit class RichDuration(val duration: Duration) extends AnyVal {
    def formatHMS: String = {
      val s = duration.toSeconds % 60
      val m = duration.toSeconds / 60 % 60
      val h = duration.toSeconds / 60 / 60

      f"$h h $m%02d m $s%02d s"
    }
  }

  def fromTo(start: Int, end: Int, step: Int = 1): Array[Int] =
    Stream.iterate(start)(_ + step).takeWhile(_ <= end)
      // We return an array since it is super fast to iterate through and this matters in some places where this is used.
      .toArray
}
