/*
 * Copyright 2020-2022 Typelevel
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
package unsafe

import scala.concurrent.ExecutionContext

/**
 * Builder object for creating custom `IORuntime`s. Useful for creating `IORuntime` based on the
 * default one but with some wrappers around execution contexts or custom shutdown hooks.
 */
final class IORuntimeBuilder protected (
    protected var customCompute: Option[(ExecutionContext, () => Unit)] = None,
    protected var computeTransform: ExecutionContext => ExecutionContext = identity,
    protected var customBlocking: Option[(ExecutionContext, () => Unit)] = None,
    protected var blockingTransform: ExecutionContext => ExecutionContext = identity,
    protected var customConfig: Option[IORuntimeConfig] = None,
    protected var customScheduler: Option[(Scheduler, () => Unit)] = None,
    protected var extraShutdownHooks: List[() => Unit] = Nil,
    protected var builderExecuted: Boolean = false,
    protected var failureReporter: Throwable => Unit = _.printStackTrace()
) extends IORuntimeBuilderPlatform {

  /**
   * Set a custom compute pool
   *
   * @param compute
   *   `ExecutionContext` for compute
   * @param shutdown
   *   shutdown hook upon [[IORuntime]] shutdown
   */
  def setCompute(compute: ExecutionContext, shutdown: () => Unit) = {
    if (customCompute.isDefined) {
      throw new IllegalStateException("Compute can be set only once")
    }
    customCompute = Some((compute, shutdown))
    this
  }

  /**
   * Modifies the execution underlying execution context. Useful in case you want to use the
   * default compute but add extra logic to `execute`, e.g. for adding instrumentation.
   *
   * @param transform
   */
  def transformCompute(transform: ExecutionContext => ExecutionContext) = {
    computeTransform = transform.andThen(computeTransform)
    this
  }

  /**
   * Override the default blocking execution context
   *
   * @param compute
   *   execution context for blocking
   * @param shutdown
   *   method called upon blocking context shutdown
   */
  def setBlocking(blocking: ExecutionContext, shutdown: () => Unit) = {
    if (customBlocking.isDefined) {
      throw new RuntimeException("Blocking can only be set once")
    }
    customBlocking = Some((blocking, shutdown))
    this
  }

  /**
   * Modifies the execution underlying blocking execution context. Useful in case you want to
   * use the default blocking context but add extra logic to `execute`, e.g. for adding
   * instrumentation.
   *
   * @param transform
   */
  def transformBlocking(transform: ExecutionContext => ExecutionContext) = {
    blockingTransform = transform.andThen(blockingTransform)
    this
  }

  /**
   * Provide custom IORuntimConfig for created IORuntime
   *
   * @param config
   * @return
   */
  def setConfig(config: IORuntimeConfig) = {
    customConfig = Some(config)
    this
  }

  /**
   * Override the defaul scheduler
   *
   * @param compute
   *   execution context for compute
   * @param shutdown
   *   method called upon compute context shutdown
   */
  def setScheduler(scheduler: Scheduler, shutdown: () => Unit) = {
    if (customScheduler.isDefined) {
      throw new RuntimeException("Scheduler can only be set once")
    }
    customScheduler = Some((scheduler, shutdown))
    this
  }

  def setFailureReporter(f: Throwable => Unit) = {
    failureReporter = f
    this
  }

  /**
   * Introduce additional shutdown hook to be executed after compute, blocking and scheduler
   * shutdown logic is invoked
   *
   * @param shutdown
   * @return
   */
  def addShutdownHook(shutdown: () => Unit) = {
    extraShutdownHooks = shutdown :: extraShutdownHooks
    this
  }

  def build(): IORuntime =
    if (builderExecuted) throw new RuntimeException("Build can only be performe once")
    else {
      builderExecuted = true
      platformSpecificBuild
    }

}

object IORuntimeBuilder {
  def apply(): IORuntimeBuilder =
    new IORuntimeBuilder()
}
