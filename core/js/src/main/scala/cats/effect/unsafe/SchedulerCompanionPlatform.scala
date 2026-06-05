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

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.timers
import scala.util.Try
import cats.WasmMigration
import scala.scalajs.LinkingInfo
import scala.scalajs.LinkingInfo.{linkTimeIf, moduleKind, ModuleKind}

private[unsafe] abstract class SchedulerCompanionPlatform { this: Scheduler.type =>
  private[this] val maxTimeout = Int.MaxValue.millis

  def createDefaultScheduler(): (Scheduler, () => Unit) =
    linkTimeIf(moduleKind == ModuleKind.WasmComponent) {
      (WasiPollingExecutor.global.asInstanceOf[Scheduler], () => ())
    } {
      (
        new Scheduler {

          def sleep(delay: FiniteDuration, task: Runnable): Runnable =
            if (delay <= maxTimeout) {
              val handle = timers.setTimeout(delay)(task.run())
              mkCancelRunnable(handle)
            } else {
              var cancel: Runnable = () => ()
              cancel = sleep(maxTimeout, () => cancel = sleep(delay - maxTimeout, task))
              () => cancel.run()
            }

          def nowMillis() = System.currentTimeMillis()
          def monotonicNanos() = System.nanoTime()
          override def nowMicros(): Long = nowMicrosImpl()
        },
        () => ()
      )
    }

  private[this] val mkCancelRunnable: timers.SetTimeoutHandle => Runnable =
    LinkingInfo.linkTimeIf(LinkingInfo.moduleKind == LinkingInfo.ModuleKind.WasmComponent) {
      val noop: Runnable = () => ()
      (_: timers.SetTimeoutHandle) => noop
    } {
      if (js.typeOf(js.Dynamic.global.clearTimeout) == "function")
        handle => () => timers.clearTimeout(handle)
      else { // raw V8 doesn't support `clearTimeout`, so don't crash
        val noop: Runnable = () => ()
        _ => noop
      }
    }

  private[this] val nowMicrosImpl: () => Long = {
    LinkingInfo.linkTimeIf(LinkingInfo.moduleKind == LinkingInfo.ModuleKind.WasmComponent) {
      () => scalajs.wasi.clocks.monotonic_clock.now() / 1_000
    } {
      def test(performance: Performance) = {
        // take it for a spin
        !(performance.timeOrigin + performance.now()).isNaN
      }

      def browsers = WasmMigration.forComponent(Option.empty[Performance]) {
        Try(js.Dynamic.global.performance.asInstanceOf[js.UndefOr[Performance]].filter(test))
          .toOption
          .flatMap(_.toOption)
      }
      def nodeJS = WasmMigration.forComponent(Option.empty[Performance]) {
        Try {
          js.Dynamic
            .global
            .require("perf_hooks")
            .performance
            .asInstanceOf[js.UndefOr[Performance]]
            .filter(test)
        }.toOption.flatMap(_.toOption)
      }

      browsers.orElse(nodeJS).map { performance => () =>
        ((performance.timeOrigin + performance.now()) * 1000).toLong
      } getOrElse { () => System.currentTimeMillis() * 1000 }
    }
  }
}

@js.native
private trait Performance extends js.Any {
  def timeOrigin: Double = js.native
  def now(): Double = js.native
}
