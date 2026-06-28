/*
 * Copyright 2020-2026 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect
import scala.scalajs.wit
import scala.scalajs.wasi

/**
 * IOApp entrypoint for `wasi:cli/run@0.2.0`
 *
 * This trait provides a shorthand for exporting the `wasi:cli/run@0.2.0`
 * interface. Instead of accessing IOApp's `main` method manually, you can
 * create an object that extends this trait and delegates to the [[runApp]]
 * function.
 *
 * {{{
 * import cats.effect.*
 *
 * import componentmodel.exports.cli.run.Run
 * import scala.scalajs.wit.annotation.*
 *
 * object Main extends IOApp {
 *    def run = ???
 * }
 *
 * @WitImplementation
 * object Entrypoint extends IOAppWasi with Run {
 *   override def run() = runApp(Main)
 * }
 * }}}
 */
trait IOAppWasi {
  /**
   * Runs specified [[IOApp]] with arguments sourced from environment.
   * @return [[scala.sclajs.wit.Ok wit.Ok(())]] on success or
   *         [[scala.scalajs.wit.Err wit.Err(())]] on failure
   */
  def runApp(app: IOApp): wit.Result[Unit, Unit] = {
    val args = wasi.cli.environment.getArguments()
    try {
      app.main(args)
      wit.Ok(())
    } catch { case _: Throwable =>
      wit.Err(())
    }
  }
}
