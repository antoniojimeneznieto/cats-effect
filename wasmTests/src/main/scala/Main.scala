/*
 * Copyright 2020-2025 Typelevel
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
package wasmTests

import componentmodel.exports.wasi.cli.Run

import cats.implicits.*
import scala.concurrent.duration.*
import scalajs.wit

object Main extends IOApp.Simple {
  def run: IO[Unit] =
    for {
      _ <- IO.println("Starting")
      f <- IO.unit.foreverM.start
      _ <- IO.println("Waiting")
      _ <- IO.sleep(5.second)
      _ <- IO.println("Ending")
      _ <- f.cancel
      _ <- IO.println("Ended")
    } yield ()
}

@wit.annotation.WitImplementation
object Runner extends Run {
  def run(): wit.Result[Unit, Unit] = {
    Main.main(Array.empty)

    wit.Ok(())
  }
}
