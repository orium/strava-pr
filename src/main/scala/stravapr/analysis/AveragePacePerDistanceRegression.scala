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

package stravapr.analysis

import stravapr.Pace
import scala.concurrent.duration._

object AveragePacePerDistanceRegression {
  private implicit class RichNumericWithSquare[A: Numeric](val n: A) {
    def squared: A = implicitly[Numeric[A]].times(n, n)
  }

  case class LogarithmicRegression(a: Double, b: Double) extends ((Int) => Pace) {
    def apply(distance: Int): Pace =
      Pace(distance, (a + b * Math.log(distance.toDouble)).seconds)

    override def toString(): String =
      s"$a + $b * log(x)"
  }

  /** Performs a logarithm based on the given points and returns a logarithmic regression. */
  def regression(distancePace: Map[Int, Pace]): LogarithmicRegression = {
    // See http://mathworld.wolfram.com/LeastSquaresFittingLogarithmic.html

    val xys: Seq[(Int, Long)] = distancePace.toSeq
      .map { case (d, p) => d -> p.durationPerKm.toSeconds }

    val xs = xys.map { case (x, _) => x }
    val ys = xys.map { case (_, y) => y }
    val n = xys.length

    val sum_y = ys.sum
    val sum_logx = xs.map(x => Math.log(x.toDouble)).sum

    val sum_ylogx = xys.map { case (x, y) => y*Math.log(x.toDouble) }.sum
    val sum_logx_squared = xs.map(x => Math.log(x.toDouble).squared).sum

    val b = (n * sum_ylogx - sum_y * sum_logx) / (n * sum_logx_squared - sum_logx.squared)
    val a = (sum_y - b * sum_logx) / n

    LogarithmicRegression(a, b)
  }
}
