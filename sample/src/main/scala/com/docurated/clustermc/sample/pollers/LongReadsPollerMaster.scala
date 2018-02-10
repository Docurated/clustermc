package com.docurated.clustermc.sample.pollers

import akka.actor.Props
import com.docurated.clustermc.masters.PollerMaster

class LongReadsPollerMaster extends PollerMaster {
  override protected def createPollers(): Unit = {
    context.actorOf(Props(classOf[LongReadsRSS], self), "long-reads-poller")
  }
}
