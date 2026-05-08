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
