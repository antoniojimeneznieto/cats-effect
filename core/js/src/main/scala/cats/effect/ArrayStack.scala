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
import scalajs.LinkingInfo.{linkTimeIf, moduleKind, ModuleKind}

private sealed trait ArrayStack[A <: AnyRef] extends Any {

  @inline def init(bound: Int): Unit = {
    val _ = bound
    ()
  }

  @inline def push(a: A): Unit

  @inline def pop(): A 

  @inline def peek(): A

  @inline def isEmpty(): Boolean

  @inline def unsafeIndex(): Int

  @inline def invalidate(): Unit

}

private final class WasmArrayStack[A <: AnyRef](val buffer: mutable.Stack[A]) extends AnyVal with ArrayStack[A] {

  @inline override def push(a: A): Unit = {
    buffer.push(a)
    ()
  }

  @inline override def pop(): A = {
    buffer.pop()
  }

  @inline override def peek(): A = buffer(buffer.length - 1)

  @inline override def isEmpty(): Boolean = buffer.length == 0

  // to allow for external iteration
  @inline def unsafeBuffer(): mutable.Stack[A] = buffer

  @inline override def unsafeIndex(): Int = buffer.length

  @inline override def invalidate(): Unit = ()

}

private final class JSArrayStack[A <: AnyRef](val buffer: js.Array[A]) extends AnyVal with ArrayStack[A] {

  @inline def push(a: A): Unit = {
    buffer.push(a)
    ()
  }

  @inline def pop(): A = {
    buffer.pop()
  }

  @inline def peek(): A = buffer(buffer.length - 1)

  @inline def isEmpty(): Boolean = buffer.length == 0

  // to allow for external iteration
  @inline def unsafeBuffer(): js.Array[A] = buffer
  @inline def unsafeIndex(): Int = buffer.length

  @inline def invalidate(): Unit = {
    buffer.length = 0 // javascript is crazy!
  }
}


private object ArrayStack {

  @inline def apply[A <: AnyRef](size: Int): ArrayStack[A] = {
    val _ = size
    apply()
  }

  @inline def apply[A <: AnyRef](): ArrayStack[A] = 
    linkTimeIf(moduleKind == ModuleKind.WasmComponent) {
      new WasmArrayStack(new mutable.Stack[A]).asInstanceOf[ArrayStack[A]]
    } {
      new JSArrayStack(new js.Array[A])
    }
}
