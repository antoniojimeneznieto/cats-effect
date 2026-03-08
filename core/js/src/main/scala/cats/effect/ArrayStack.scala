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
import scalajs.runtime

private final class ArrayStack[A <: AnyRef](val buffer: mutable.Stack[A]) extends AnyVal {

  @inline def init(bound: Int): Unit = {
    val _ = bound
    ()
  }

  @inline def push(a: A): Unit = {
    buffer.append(a)
    ()
  }

  @inline def pop(): A = {
    buffer.pop()
  }

  @inline def peek(): A = buffer(buffer.length - 1)

  @inline def isEmpty(): Boolean = buffer.length == 0

  // to allow for external iteration
  @inline def unsafeBuffer(): mutable.Stack[A] = buffer

  @inline def unsafeIndex(): Int = buffer.length

  @inline def invalidate(): Unit = {
    //buffer.size = 0 // javascript is crazy!
  }

}

private object ArrayStack {

  @inline def apply[A <: AnyRef](size: Int): ArrayStack[A] = {
    val _ = size
    apply()
  }

  @inline def apply[A <: AnyRef](): ArrayStack[A] = {
    new ArrayStack(new mutable.Stack[A])
  }

}
