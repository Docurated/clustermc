package com.docurated.clustermc.workflow

import com.docurated.clustermc.protocol.PolledMessage
import org.joda.time.{DateTime, DateTimeZone}

case class Workflow(msg: PolledMessage, start: WorkflowStep) {
  override def toString: String = start.toString
  val createdAt: DateTime = DateTime.now(DateTimeZone.UTC)
}
