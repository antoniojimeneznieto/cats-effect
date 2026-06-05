package cats.effect.unsafe

import scala.scalajs.wasi
import scala.scalajs.wit
import scala.collection.mutable

final class WasiPoller(
    events: mutable.Queue[wasi.io.poll.Pollable]
) {
  val callbacks = mutable.Queue.empty[Unit => Unit]
  var readyEvents: Array[Int] = _

  def poll(timeout: Long): PollResult =
    if (events.isEmpty) {
      PollResult.Complete
    } else {
      if (timeout != -1) {
        // adding a clock works like a timeout
        val alarm = wasi.clocks.monotonic_clock.subscribeDuration(timeout)
        events += alarm
      }

      // Block until an I/O completes
      readyEvents = wasi.io.poll.poll(events.toArray)

      if (events.length > 0)
        PollResult.Incomplete
      else
        PollResult.Complete
    }

  def processReadyEvents(): Boolean =
    if (readyEvents ne null) {
      var did = false
      readyEvents.foreach { idx =>
        val cb = callbacks.remove(idx)
        events.remove(idx)
        did = true
        cb()
      }

      readyEvents = null
      did
    } else {
      false
    }

  def needsPoll: Boolean = events.length > 0
}
