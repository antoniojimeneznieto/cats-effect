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

package cats

import scalajs.LinkingInfo.{linkTimeIf, moduleKind, ModuleKind}

object WasmMigration {
  def guard[A](from: String)(body: => A): A = 
    linkTimeIf(moduleKind == ModuleKind.WasmComponent) {
      throw new NotImplementedError(s"not implemented: ${from}")
    } {
      body
    }

  @inline def forComponent[A](forWasi: => A)(forJS: => A) = 
    linkTimeIf(moduleKind == ModuleKind.WasmComponent) {
      forWasi
    } {
      forJS
    }

  @inline val isComponent = linkTimeIf(moduleKind == ModuleKind.WasmComponent)(true)(false)
}
