package com.docurated.clustermc.sample

import com.docurated.clustermc.masters.WorkflowMaster
import com.docurated.clustermc.masters.WorkflowMasterProtocol.WorkflowForMessage
import com.docurated.clustermc.protocol.PolledMessage
import com.docurated.clustermc.sample.protocol.ArticleMessage
import com.docurated.clustermc.sample.steps.ArticleWorkflowBuilder

/**
  * Sample extension of the abstract WorkflowMaster. Here we are setting the internal buffer of work to
  * 1. Building the workflow containing the workflow steps is done serially because there is no I/O involved, the
  * steps are static. In a real-world application, it is common that determining workflow steps requires persisted
  * data lookup, in which case the implementation of the master would contain a pool of actors to build workflows
  * asynchronously. Since the WorkflowMaster is a cluster singleton, it is important that no I/O is actually
  * performed directly by the master as it will become a bottleneck in a large cluster.
  */
class SampleWorkflowMaster extends WorkflowMaster {
  override protected val workToBuffer = 1

  override def buildWorkflowForMessage(msg: PolledMessage): Unit = msg match {
    case article: ArticleMessage =>
      val workflow = ArticleWorkflowBuilder(article, logger)
      logger.debug(s"Got $workflow for message $article")
      self ! WorkflowForMessage(article, workflow)

    case _ =>
      logger.warning("SampleWorkflowMaster received unexpected polled message")
  }
}
