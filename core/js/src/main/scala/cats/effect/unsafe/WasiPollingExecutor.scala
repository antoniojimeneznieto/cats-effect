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

package cats.effect.unsafe

import cats.effect.IOFiber

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.scalajs.wasi

import java.util.{PriorityQueue => JPriorityQueue}

final class WasiPollingExecutor(pollEvery: Int, system: PollingSystem.WithPoller[WasiPoller])
    extends ExecutionContextExecutor
    with Scheduler {

  private val poller = system.makePoller()

  private[this] val executeQueue = new mutable.Queue[Runnable]
  private[this] val sleepQueue = new JPriorityQueue[SleepTask]

  private var needsReschedule = true

  override def reportFailure(cause: Throwable): Unit = cause.printStackTrace()

  private final class SleepTask(val at: Long, val runnable: Runnable)
      extends Runnable
      with Comparable[SleepTask] {
    def run(): Unit = {
      sleepQueue.remove(this)
      ()
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
      var i = 0
      while (i < pollEvery && !executeQueue.isEmpty) {
        val task = executeQueue.dequeue()
        try task.run()
        catch {
          case t: Throwable =>
            IOFiber.onFatalFailure(t)
        }
        i += 1
      }

      // 3. poll
      val timeout =
        if (!executeQueue.isEmpty)
          0
        else if (!sleepQueue.isEmpty())
          Math.max(sleepQueue.peek().at - monotonicNanos(), 0)
        else
          -1

      if (system.needsPoll(poller) && timeout < -1) {
        system.poll(poller, timeout)
        system.processReadyEvents(poller)
      }

      continue = !executeQueue.isEmpty || !sleepQueue.isEmpty() || system.needsPoll(poller)
    }

    needsReschedule = true
  }

  private def scheduleIfNeeded() = if (needsReschedule) {
    ExecutionContext.global.execute(() => loop())
    needsReschedule = false
  }

  override def execute(command: Runnable): Unit = {
    executeQueue.enqueue(command)
    scheduleIfNeeded()
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

object WasiPollingExecutor {
  val global = new WasiPollingExecutor(64, WasiPollSystem)
}
