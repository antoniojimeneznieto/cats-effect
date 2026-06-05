package cats.effect.unsafe

import cats.effect.unsafe.metrics.PollerMetrics
import scala.collection.mutable

object WasiPollSystem extends PollingSystem {

  val initialPollerArraySize = 128

  type Poller = WasiPoller

  override def close(): Unit = ()

  override def makeApi(ctx: PollingContext[Poller]): Api = ???

  override def makePoller(): Poller = {
    new WasiPoller(new mutable.Queue(initialPollerArraySize))
  }

  override def closePoller(poller: Poller): Unit = ()

  override def poll(poller: Poller, nanos: Long): PollResult =
    poller.poll(nanos)

  override def processReadyEvents(poller: Poller): Boolean =
    poller.processReadyEvents()

  override def needsPoll(poller: Poller): Boolean = poller.needsPoll

  override def interrupt(targetThread: Thread, targetPoller: Poller): Unit = ()

  override def metrics(poller: Poller): PollerMetrics = ???

}
