package com.docurated.clustermc.actors

import com.docurated.clustermc.masters.WorkflowMasterProtocol.{WorkflowIsDone, WorkflowIsDoneWithError}
import com.docurated.clustermc.protocol.PolledMessage
import com.docurated.clustermc.util.ActorStack
import com.docurated.clustermc.workflow.Workflow
import org.joda.time.{DateTime, DateTimeZone, Seconds}

sealed case class WorkflowSummary(numCompleted: Int, startedAt: DateTime, totalSeconds: Long, avgWorkflowTime: Long)

trait WorkflowMasterSummary extends ActorStack {
  def trackedWorkflows: Map[PolledMessage, Workflow]
  var summary = WorkflowSummary(0, DateTime.now(DateTimeZone.UTC), 0, 0)

  override def wrappedReceive = {
    case x @ WorkflowIsDone(workflow) =>
      trackedWorkflows
        .get(workflow.msg)
        .foreach { kv =>
          summary = summary.copy(numCompleted = summary.numCompleted + 1, totalSeconds = summary.totalSeconds + Seconds.secondsBetween(summary.startedAt, kv.createdAt).getSeconds)
        }

      super.receive(x)

    case x @ WorkflowIsDoneWithError(workflow, _) =>
      trackedWorkflows.
        get(workflow.msg).
        foreach { kv =>
          summary = summary.copy(numCompleted = summary.numCompleted + 1, totalSeconds = summary.totalSeconds + Seconds.secondsBetween(summary.startedAt, kv.createdAt).getSeconds)
        }

      super.receive(x)

    case any => super.receive(any)
  }

}
