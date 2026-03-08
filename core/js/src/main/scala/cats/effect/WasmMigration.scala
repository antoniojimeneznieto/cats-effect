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
