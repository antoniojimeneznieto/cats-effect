package cats.effect.unsafe

import scala.scalajs.wasi
import scala.scalajs.wit
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

final class WasiPoller(events: mutable.Queue[wasi.io.poll.Pollable]) {
  val callbacks = mutable.ArrayDeque.empty[() => Unit]
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
        // add an alarm so that we can timeout
        val alarm = wasi.clocks.monotonic_clock.subscribeDuration(timeout)
        events += alarm
        val alarmIdx = events.length - 1

        val processed = wasi.io.poll.poll(events.toArray)

        // We have to sort the array so that we don't change indexes
        // when processing the array later.
        processed.sortInPlace().reverse

        /* We have to remove the alarm from events because it shouldn't outlive
         * this poll and fire off later. We also drop its index from `readyEvents`
         * if it got polled.
         */
        events.removeFirst(_ == alarm)
        processed.headOption match {
          case Some(`alarmIdx`) =>
            readyEvents = processed.tail
          case _ =>
            readyEvents = processed
        }
      }

      if (readyEvents == events) PollResult.Complete
      else PollResult.Incomplete
    }

  def processReadyEvents(): Boolean =
    if (readyEvents ne null) {
      var did = false

      // readyEvents is sorted descending
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

  def registerPollable(pollable: wasi.io.poll.Pollable, cb: () => Unit): Unit = {
    events.append(pollable)
    callbacks.append(cb)
  }

  def deregisterPollable(pollable: wasi.io.poll.Pollable): Unit = {
    val idx = events.indexOf(pollable)
    events.remove(idx)
    callbacks.remove(idx)
    readyEvents = readyEvents.filterNot(_ == idx)

    ()
  }
}
