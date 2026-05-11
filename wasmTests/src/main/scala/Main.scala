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
import cats.effect.unsafe.WasiPollingExecutor
import cats.effect.std.Supervisor
import scala.concurrent.duration.*
import scalajs.wasi
import scalajs.wit

object Main {
  def run: IO[Unit] =
    Supervisor[IO](await = true).use { sv =>
      for {
        _ <- (
          IO.sleep(3.seconds) >>
            IO.println("first task part 1 -- 3s") >>
            IO.sleep(5.seconds) >>

            // This part should not execute as we are closing after "third task"
            IO.println("first task part 2 -- 8s")
        ).start

        _ <- IO.println("foo")

        _ <- (IO.sleep(2.seconds) >> IO.println("second task -- 2s")).start

        _ <- IO.println("bar")

        _ <- sv.supervise(IO.sleep(5.seconds) >> IO.println("third task -- 5s")) // All fibers should be cancelled after this one finishes

        _ <- IO.race(
            IO.sleep(3.seconds) >> IO.println("raced 3s before 2s"),
            IO.sleep(2.seconds) >> IO.println("raced 2s before 3s")
          ).start
      } yield ()
    }
}

@wit.annotation.WitImplementation
object Runner extends Run {
  def run(): wit.Result[Unit, Unit] = {
    import cats.effect.unsafe.implicits.global

    try {
      Main.run.unsafeRunFiber(
          wasi.cli.exit.exit(wit.Err(())),
          e => {
            e.printStackTrace()
            wasi.cli.exit.exit(wit.Err(()))
          },
          c => wasi.cli.exit.exit(wit.Ok(()))
        )
    } catch {
      case e: Throwable =>
        e.printStackTrace()
    }

    wit.Ok(())
  }
}
