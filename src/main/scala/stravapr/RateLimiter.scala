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
import scala.util.control.NonFatal

class RateLimiter(isRateLimitExceeded: Throwable => Boolean) {
  private def run[T](f: => T, backoff: Duration = 1.minute): T = try {
    f
  } catch {
    case NonFatal(e) if isRateLimitExceeded(e) =>
      println(s"Rate limit exceeded.  Sleeping for $backoff...")

      Thread.sleep(backoff.toMillis)

      run(f, backoff * 2)
  }

  RateLimitException.getClass

  def apply[T](f: => T): T =
    run(f)
}

object RateLimiter {
  import scala.reflect.{ClassTag, classTag}

  def byException[T <: Throwable: ClassTag]: RateLimiter =
    new RateLimiter(exceptionCausedBy[T])

  def exceptionCausedBy[T <: Throwable: ClassTag](exception: Throwable): Boolean = {
    classTag[T].runtimeClass.isInstance(exception) match {
      case true => true
      case false => Option(exception.getCause).exists(exceptionCausedBy[T])
    }
  }
}
