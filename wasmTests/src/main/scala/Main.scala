package cats.effect
package wasmTests

import componentmodel.exports.wasi.cli.Run

import scala.scalajs.wit

import cats.implicits.*
import cats.data.Chain

object Main {
  val x = Resource.make[IO, Int](IO(5))(x => IO.println(s"releasing ${x}"))

  def run: IO[Unit] = {
    val words = Chain("Hiiii", "from", "Wasm/WASI")
    val joined = words.intercalate(" ") + "!"

    x.use { num =>
      for {
        start <- IO.monotonic
        _     <- IO.println(joined)
        _     <- IO.println(s"got $num")
        end   <- IO.monotonic
        _     <- IO.println(s"time difference ${end - start}")
      } yield ()
    }
  }
}

@wit.annotation.WitImplementation
object Runner extends Run {
  def run(): wit.Result[Unit, Unit] = {
    import cats.effect.unsafe.implicits.global

    Main.run.unsafeRunSyncWasi() match {
      case Left(e) =>
        e.printStackTrace()
        wit.Err(())
      case Right(_) => wit.Ok(())
    }

    wit.Ok(())
  }
}
