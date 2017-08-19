package com.docurated.clustermc.actors

import akka.actor.Props
import com.docurated.clustermc.masters.{PollerMasterStatus, WorkerMasterStatus}
import com.docurated.clustermc.protocol.MasterWorkerProtocol.HowBusy
import com.docurated.clustermc.protocol.PolledMessage
import com.docurated.clustermc.util.ActorStack
import com.docurated.clustermc.workflow.Workflow

trait WorkflowMasterRiemannReporter extends ActorStack {
  private val reporter = context.actorOf(Props[RiemannReporter].withMailbox("bounded-mailbox"))
  def workerStatus: WorkerMasterStatus
  def trackedWorkflows: Map[PolledMessage, Workflow]
  def pollerStatus: PollerMasterStatus

  override def wrappedReceive = {
    case HowBusy =>
      super.receive(HowBusy)
      reportToRiemann()

    case any => super.receive(any)
  }

  private def reportToRiemann() = {
    reporter ! RiemannMetric("fileworker.workers.active", workerStatus.activeWorkers)
    reporter ! RiemannMetric("fileworker.workers.idle", workerStatus.idleWorkers)
    reporter ! RiemannMetric("fileworker.workflows", trackedWorkflows.size)
    reporter ! RiemannMetric("fileworker.pollers.messagesInFlight", pollerStatus.messagesInFlight)
  }
}
