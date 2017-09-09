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

import java.io.{File, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions, ConfigValueFactory, Config => TypesafeConfig}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MutableMap}
import scala.util.Try

class RunCache private (cache: MutableMap[Int, Run]) {
  def get(runId: Int): Option[Run] =
    cache.get(runId)

  def add(run: Run): Unit = cache += run.id -> run

  def add(runs: Runs): Unit = runs.foreach(add)

  def allRuns: Runs = Runs(cache.values.toSet)

  def save(file: File): Unit = {
    val runConfigs = cache.values.map { run =>
      ConfigValueFactory.fromMap(
        Map(
          "version"   -> RunCache.RunFormatVersion,
          "id"        -> run.id,
          "datetime"  -> run.datetime.toString,
          "times"     -> run.times.asJava,
          "distances" -> run.distances.asJava
        ).asJava
      )
    }.map(ConfigValueFactory.fromAnyRef(_))

    val config = ConfigFactory.empty.withValue("runs", ConfigValueFactory.fromIterable(runConfigs.toSeq.asJava))
    val renderOptions = ConfigRenderOptions.defaults()
      .setComments(false)
      .setOriginComments(false)

    val p = new PrintWriter(file)
    try {
      p.write(config.root.render(renderOptions))
    } finally {
      p.close()
    }
  }

  def size: Int = cache.size
}

object RunCache {
  private val RunFormatVersion: Int = 1

  val empty = new RunCache(MutableMap.empty)

  def fromFile(cacheFile: File): Try[RunCache] = Try {
    val config: TypesafeConfig = ConfigFactory.parseFile(cacheFile)
    val runs: Seq[TypesafeConfig] = config.getConfigList("runs").asScala

    val cache = runs.flatMap { run =>
      val version = run.getInt("version")

      if (version == RunFormatVersion) {
        val id = run.getInt("id")
        val datetime = LocalDateTime.parse(run.getString("datetime"), DateTimeFormatter.ISO_DATE_TIME)
        val times = run.getIntList("times").asScala.map(_.toInt).toArray
        val distances = run.getIntList("distances").asScala.map(_.toInt).toArray

        Some(id -> Run(id, datetime, times, distances))
      } else {
        println("Ignoring unknown run format version in run cache.")
        None
      }
    }

    new RunCache(MutableMap(cache: _*))
  }
}
