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

package cats.effect

import scala.scalajs.js
import scala.collection.mutable
import scala.scalajs.LinkingInfo.{linkTimeIf, ModuleKind, moduleKind}

import CallbackStack.Handle

import scala.annotation.tailrec

private trait CallbackStackOps[A] extends Any {
  @inline def push(next: A => Unit): Handle[A]

  @inline def unsafeSetCallback(cb: A => Unit): Unit

  /**
   * Invokes *all* non-null callbacks in the queue, starting with the current one. Returns true
   * iff *any* callbacks were invoked.
   */
  @inline def apply(oc: A): Boolean

  /**
   * Removes the callback referenced by a handle. Returns `true` if the data structure was
   * cleaned up immediately, `false` if a subsequent call to [[pack]] is required.
   */
  @inline def clearHandle(handle: Handle[A]): Boolean

  @inline def clear(): Unit

  @inline def pack(bound: Int): Int
}

private final class JSCallbackStackOps[A](private val callbacks: js.Array[A => Unit])
    extends AnyVal
    with CallbackStackOps[A] {

  @inline def push(next: A => Unit): Handle[A] = {
    callbacks.push(next)
    callbacks.length - 1
  }

  @inline def unsafeSetCallback(cb: A => Unit): Unit = {
    callbacks(callbacks.length - 1) = cb
  }

  /**
   * Invokes *all* non-null callbacks in the queue, starting with the current one. Returns true
   * iff *any* callbacks were invoked.
   */
  @inline def apply(oc: A): Boolean =
    callbacks
      .asInstanceOf[js.Dynamic]
      .reduceRight( // skips deleted indices, but there can still be nulls
        (acc: Boolean, cb: A => Unit) =>
          if (cb ne null) { cb(oc); true }
          else acc,
        false)
      .asInstanceOf[Boolean]

  /**
   * Removes the callback referenced by a handle. Returns `true` if the data structure was
   * cleaned up immediately, `false` if a subsequent call to [[pack]] is required.
   */
  @inline def clearHandle(handle: Handle[A]): Boolean = {
    // deleting an index from a js.Array makes it sparse (aka "holey"), so no memory leak
    js.special.delete(callbacks, handle)
    true
  }

  @inline def clear(): Unit =
    callbacks.length = 0 // javascript is crazy!

  @inline def pack(bound: Int): Int =
    bound - bound // aka 0, but so bound is not unused ...
}

private final class WasiCallbackStack[A](private var callbacks: mutable.ArrayBuffer[A => Unit])
    extends CallbackStackOps[A] {

  private val order = mutable.ArrayDeque.from(0.until(callbacks.length))

  @inline def push(next: A => Unit): Handle[A] = {
    @tailrec
    def loop(idx: Int): Int = {
      if (idx >= callbacks.length) {
        callbacks.addOne(next)
        order.prepend(idx)
        idx
      } else if (callbacks(idx) == null) {
        callbacks(idx) = next
        order.prepend(idx)
        idx
      } else {
        loop(idx + 1)
      }
    }

    if (callbacks equals null) {
      callbacks = mutable.ArrayBuffer(next)
      order.prepend(0)
      0
    } else {
      loop(0)
    }
  }

  @inline def unsafeSetCallback(cb: A => Unit): Unit =
    if (order.isEmpty) {
      callbacks.prepend(cb)
      order.prepend(0)
    } else {
      val last = order.head
      callbacks(last) = cb
    }

  /**
   * Invokes *all* non-null callbacks in the queue, starting with the current one. Returns true
   * iff *any* callbacks were invoked.
   */
  @inline def apply(oc: A): Boolean =
    order.foldLeft(false) { (acc, idx) =>
      if (callbacks(idx) ne null) { callbacks(idx)(oc); true }
      else acc
    }

  /**
   * Removes the callback referenced by a handle. Returns `true` if the data structure was
   * cleaned up immediately, `false` if a subsequent call to [[pack]] is required.
   */
  @inline def clearHandle(handle: Handle[A]): Boolean = {
    callbacks(handle) = null
    val idx = order.indexOf(handle)
    order.remove(idx)
    true
  }

  @inline def clear(): Unit = {
    callbacks.clear()
    order.clear()
  }

  @inline def pack(bound: Int): Int =
    bound - bound // aka 0, but so bound is not unused ...
}

private object CallbackStackFactory {
  import CallbackStack.CallbackStackFactory

  // Explicitly typed to aid linkTimeIf type resolution
  def Wasm: CallbackStackFactory = new CallbackStackFactory {
    type StackType[A] = WasiCallbackStack[A]

    @inline def of[A](cb: A => Unit): StackType[A] =
      new WasiCallbackStack(mutable.ArrayBuffer[A => Unit](cb))

    @inline def ops[A](stack: StackType[A]): CallbackStackOps[A] =
      stack
  }

  def JS: CallbackStackFactory = new CallbackStackFactory {
    type StackType[A] = js.Array[A => Unit]

    @inline def of[A](cb: A => Unit): StackType[A] =
      new js.Array[A => Unit]()

    @inline def ops[A](stack: StackType[A]): CallbackStackOps[A] =
      new JSCallbackStackOps(stack)
  }
}

private[effect] object CallbackStack {
  type Handle[A] = Int

  // This trait has to be defined here to support the implicit `ops` conversion
  sealed trait CallbackStackFactory {
    type StackType[A]
    def of[A](cb: A => Unit): StackType[A]
    implicit def ops[A](stack: StackType[A]): CallbackStackOps[A]
  }

  val callbackStackFactory: CallbackStackFactory =
    linkTimeIf(moduleKind == ModuleKind.WasmComponent) {
      CallbackStackFactory.Wasm
    } {
      CallbackStackFactory.JS
    }

  @inline def of[A](cb: A => Unit): CallbackStack[A] =
    callbackStackFactory.of(cb)

  @inline implicit def ops[A](stack: CallbackStack[A]): CallbackStackOps[A] =
    callbackStackFactory.ops(stack)
}

private[cats] trait CallbackStackPlatform {
  type CallbackStack[A] = CallbackStack.callbackStackFactory.StackType[A]
}
