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

import kiambogo.scrava.models.RateLimitException

import scala.concurrent.duration._

class RateLimiter(isRateLimitExceeded: Exception => Boolean) {
  private def run[T](f: => T, backoff: Duration = 1.minute): T = try {
    f
  } catch {
    case e: Exception if isRateLimitExceeded(e) =>
      println(s"Rate limit exceeded.  Sleeping for $backoff...")

      Thread.sleep(backoff.toMillis)

      run(f, backoff * 2)
  }

  RateLimitException.getClass

  def apply[T](f: => T): T =
    run(f)
}
