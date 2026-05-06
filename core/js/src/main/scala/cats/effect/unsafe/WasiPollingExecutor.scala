package cats.effect.unsafe

import scala.collection.mutable
import java.util.{ArrayDeque as JArrayDeque, PriorityQueue as JPriorityQueue}
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.Duration
import scala.scalajs.wasi

private[effect] final class WasiPollingExecutor extends ExecutionContextExecutor with Scheduler {
  override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()

  private[this] val executeQueue = new JArrayDeque[Runnable]
  private[this] val sleepQueue = new JPriorityQueue[SleepTask]

  private val events = new mutable.ArrayDeque[wasi.io.poll.Pollable](256)

  private var needsReschedule = true

  def poll(timeout: Long): Boolean = 
    if (events.isEmpty) {
      false
    } else {
      if (timeout != -1) {
        // adding a clock works like a timeout
        val alarm = wasi.clocks.monotonic_clock.subscribeDuration(timeout)
        events += alarm
      }

      // Block until an I/O completes
      val handles = wasi.io.poll.poll(events.toArray)

      // Remove completed events
      handles.foreach(events.remove)
      
      events.length > 0
    }


  private final class SleepTask(val at: Long, val runnable: Runnable) extends Runnable with Comparable[SleepTask] {
    def run(): Unit = {
      sleepQueue.remove(this)
    }

    def compareTo(that: SleepTask): Int = java.lang.Long.compare(this.at, that.at)
  }

  def loop() = {
    needsReschedule = false
    var continue = true

    while (continue) {
      val now = monotonicNanos()

      // 1. timers
      while (!sleepQueue.isEmpty() && sleepQueue.peek().at <= now) {
        val task = sleepQueue.poll()
        task.runnable.run()
      }

      // 2. tasks
      while (!executeQueue.isEmpty()) {
        val task = executeQueue.poll()
        task.run()
      }

      // 3. poll
      val timeout =
        if (!executeQueue.isEmpty())
          0
        else if (!sleepQueue.isEmpty())
          Math.max(sleepQueue.peek().at - monotonicNanos(), 0)
        else
          -1

      continue = !executeQueue.isEmpty() || !sleepQueue.isEmpty() || poll(timeout)
    }

    needsReschedule = true
  }

  private def scheduleIfNeeded() = if (needsReschedule) {
    loop()
    needsReschedule = false
  }

  override def execute(command: Runnable): Unit = {
    scheduleIfNeeded()
    executeQueue.addLast(command)
  }

  def sleep(delay: FiniteDuration, command: Runnable): Runnable = {
    if (delay <= Duration.Zero) {
      execute(command)
      val noop: Runnable = () => ()
      noop
    } else {
      scheduleIfNeeded()
      val now = monotonicNanos()
      val sleepTask = new SleepTask(now + delay.toNanos, command)
      sleepQueue.offer(sleepTask)

      sleepTask
    }
  }

  def monotonicNanos(): Long = wasi.clocks.monotonic_clock.now()
  def nowMillis(): Long = (wasi.clocks.wall_clock.now().nanoseconds / 1000000).toLong
}
