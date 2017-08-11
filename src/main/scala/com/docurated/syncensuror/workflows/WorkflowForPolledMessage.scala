package com.docurated.syncensuror.workflows

import com.docurated.clustermc.workflow.{Workflow, WorkflowStep}
import com.docurated.fakedomain.Space
import com.docurated.syncensuror.domain.EnsurorPolledMessage


object WorkflowForPolledMessage {
  def apply(msg: EnsurorPolledMessage): Option[Workflow] = {
    val wf = new WorkflowForPolledMessage(msg)
    wf.start.flatMap(s => Some(Workflow(msg, s)))
  }
}

class WorkflowForPolledMessage(msg: EnsurorPolledMessage) {
  def start: Option[WorkflowStep] = {
    if (exactSpaceExists) {
      None
    } else if (priorVersion.isEmpty && !msg.visible) {
      None
    } else if (shouldCreateNewVersion) {
      None
    } else {
      None
    }
  }

  private def stepsForModifyingCurrentVersion() = {

  }

  private lazy val priorVersion = Space.findIt(-1)

  // TODO a lot of other checks
  private def exactSpaceExists = {
    Space.findIt(-1)
      .flatMap { e =>
        val truth = e.name == msg.name &&
          e.path == msg.path &&
          e.permissionsHash == msg.permissionsHash

        Some(truth)
      }
      .getOrElse(false)
  }

  // TODO modified at timestamp
  private def shouldCreateNewVersion = {
    (priorVersion.isEmpty || msg.visible) &&
      (msg.contentIdentifier != priorVersion.get.contentIdentifier)
  }
}
