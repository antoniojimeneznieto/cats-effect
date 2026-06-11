package cats.effect.unsafe

import scala.scalajs.wasi
import scala.scalajs.wit
import scala.collection.mutable

final class WasiPoller(events: mutable.Queue[wasi.io.poll.Pollable]) {
  val callbacks = mutable.Queue.empty[Unit => Unit]
  var readyEvents: Array[Int] = null

  def poll(timeout: Long): PollResult =
    if (events.isEmpty) {
      // no events means we don't have anything to poll for
      PollResult.Complete
    } else {
      if (timeout == -1) {
        // Wait indefinitely for ready events
        readyEvents = wasi.io.poll.poll(events.toArray)
      } else {
        // adding a clock works like a timeout
        val alarm = wasi.clocks.monotonic_clock.subscribeDuration(timeout)
        events += alarm

        val alarmIdx = events.length - 1

        val processed = wasi.io.poll.poll(events.toArray)

        /* We have to remove the alarm from events because it shouldn't outlive
         * this poll and fire off later. We also drop its index from `readyEvents`
         * if it got polled.
         */
        events.removeFirst(_ == alarm)
        readyEvents = processed.filter(_ == alarmIdx) // this copies
      }

      if (readyEvents == events) PollResult.Complete
      else PollResult.Incomplete
    }

  def processReadyEvents(): Boolean =
    if (readyEvents ne null) {
      var did = false
      readyEvents.foreach { idx =>
        val cb = callbacks.remove(idx)
        val event = events.remove(idx)
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
