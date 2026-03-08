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

/*
 * scalajs-weakreferences (https://github.com/scala-js/scala-js-weakreferences)
 *
 * Copyright EPFL.
 *
 * Licensed under Apache License 2.0
 * (https://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package cats.effect.unsafe.ref

import scala.scalajs.js
import scala.scalajs.LinkingInfo.{linkTimeIf, isWebAssembly}
import scala.scalajs.LinkingInfo
import cats.WasmMigration

private[unsafe] class ReferenceQueue[T] {

  /**
   * The "enqueued" References.
   *
   * Despite the name, this is used more as a stack (LIFO) than as a queue (FIFO). The JavaDoc
   * of `ReferenceQueue` does not actually prescribe FIFO ordering, and experimentation shows
   * that the JVM implementation does not guarantee that ordering.
   */
  private[this] val enqueuedRefs = 
    LinkingInfo.linkTimeIf(LinkingInfo.moduleKind == LinkingInfo.ModuleKind.WasmComponent) {
      throw new NotImplementedError("finalizationRegistry")
    } {
      js.Array[Reference[? <: T]]()
    }

  private[this] val finalizationRegistry =
    linkTimeIf(isWebAssembly) {
      throw new NotImplementedError("finalizationRegistry")
    } {
      new js.FinalizationRegistry[T, Reference[? <: T], Reference[? <: T]]({
        (ref: Reference[? <: T]) => enqueue(ref)
      })
    }

  private[ref] def register(ref: Reference[? <: T], referent: T): Unit =
    linkTimeIf(isWebAssembly) {
      throw new NotImplementedError("finalizationRegistry.register")
    } {
      finalizationRegistry.register(referent, ref, ref)
    }

  private[ref] def unregister(ref: Reference[? <: T]): Unit =
    linkTimeIf(isWebAssembly) {
      throw new NotImplementedError("finalizationRegistry.unregister")
    } {
      val _ = finalizationRegistry.unregister(ref)
    }

  private[ref] def enqueue(ref: Reference[? <: T]): Boolean =
    linkTimeIf(isWebAssembly) {
      throw new NotImplementedError("finalizationRegistry.enqueue")
    } {
      if (ref.enqueued) {
        false
      } else {
        ref.enqueued = true
        enqueuedRefs.push(ref)
        true
      }
    }

  def poll(): Reference[? <: T] =
    linkTimeIf(isWebAssembly) {
      throw new NotImplementedError("finalizationRegistry.enqueue")
    } {
      if (enqueuedRefs.length == 0)
        null
      else
        enqueuedRefs.pop()
    }

  // Not implemented because they have a blocking contract:
  // def remove(timeout: Long): Reference[_ <: T] = ???
  // def remove(): Reference[_ <: T] = ???
}
