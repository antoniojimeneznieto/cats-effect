package cats.effect
package wasmTests

import componentmodel.exports.wasi.cli.Run

import scala.scalajs.wit

import cats.implicits.*
import cats.effect.unsafe.WasiPollingExecutor

import scala.concurrent.duration.*

object Main {
  def run: IO[Unit] = for {
    _ <- (
      IO.sleep(3.seconds) >>
        IO.println("first task") >>
        IO.sleep(5.seconds) >>
        IO.println("first task part 2")
    ).start
    _ <- IO.println("sequential 1")
    _ <- (IO.sleep(2.seconds) >> IO.println("second task")).start
    _ <- IO.println("sequential 2")
    _ <- (IO.sleep(5.seconds) >> IO.println("third task")).start
    _ <- IO.race(
      IO.sleep(3.seconds) >> IO.println("raced 3s"),
      IO.sleep(2.seconds) >> IO.println("raced 2s"))
  } yield ()
}

@wit.annotation.WitImplementation
object Runner extends Run {
  def run(): wit.Result[Unit, Unit] = {
    import cats.effect.unsafe.implicits.global

    try {
      Main.run.unsafeRunAndForget()
      global.blocking.asInstanceOf[WasiPollingExecutor].loop()
    } catch {
      case e: Throwable =>
        e.printStackTrace()
    }

    wit.Ok(())
  }
}
