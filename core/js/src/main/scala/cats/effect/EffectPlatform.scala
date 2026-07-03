package cats.effect

trait EffectPlatform {
  private[effect] type CallbackStack[A] = CallbackStack.StackType[A]
}
