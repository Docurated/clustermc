package com.docurated.clustermc.workflow

import com.docurated.clustermc.protocol.PolledMessage
import com.docurated.clustermc.util.ActorStack


class WorkflowBuilder extends ActorStack {
  override def wrappedReceive: Receive = {
    case msg: PolledMessage =>
      logger.info("Calculating workflow")
//      val workflowOption = SpaceMessageWorkflow(msg, logger)
//      sender() ! WorkflowForMessage(msg, workflowOption)
  }
}
