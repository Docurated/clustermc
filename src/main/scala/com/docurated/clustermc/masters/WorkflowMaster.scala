package com.docurated.clustermc.masters

import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.docurated.clustermc.masters.PollersProtocol.{MessageComplete, MessageFailed, MessageToQueue}
import com.docurated.clustermc.protocol.MasterWorkerProtocol._
import com.docurated.clustermc.protocol.PolledMessage
import com.docurated.clustermc.util.ActorStack
import com.docurated.clustermc.workflow.Workflow

import scala.collection.mutable
import scala.concurrent.duration._

trait WorkflowMaster extends ActorStack {
  implicit val ec = context.dispatcher
  context.system.scheduler.schedule(5 seconds, 1 seconds, self, HowBusy)

  import com.docurated.clustermc.masters.WorkflowMasterProtocol._

  private val WORK_TO_BUFFER = 100
  private val trackedWorkflows = mutable.Map.empty[PolledMessage, Workflow]
  private var workerStatus = WorkerMasterStatus(0, 0, 0)
  private var pollerStatus = PollerMasterStatus(List(), 0)

  private val workerMaster = context.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/workerMaster",
      settings = ClusterSingletonProxySettings(context.system)),
      name = "workerMasterProxy")

  private val pollerMaster = context.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/pollerMaster",
      settings = ClusterSingletonProxySettings(context.system)),
    name = "pollerMasterProxy")

  def buildWorkflowForMessage(msg: PolledMessage): Unit

  override def wrappedReceive: Receive = {
    case HowBusy =>
      workerMaster ! HowBusy

    case status @ WorkerMasterStatus(_, _, jobs) =>
      if (jobs < WORK_TO_BUFFER)
        pollerMaster ! ReadyForMessage

      workerStatus = status

    case msg: PolledMessage =>
      if (isPolledMessageTracked(msg)) {
        logger.info(s"WorkflowMaster received $msg that is already tracked in a workflow, returning to queue")
        pollerMaster ! MessageFailed(msg)
      } else {
        buildWorkflowForMessage(msg)
      }

    case WorkflowIsDone(workflow) =>
      trackedWorkflows
        .get(workflow.msg)
        .foreach { kv =>
          logger.info(s"WorkflowMaster says workflow is done {}", kv)
          trackedWorkflows.remove(kv.msg)
          buildWorkflowForMessage(workflow.msg)
        }

    case WorkflowIsDoneWithError(workflow, error) =>
      logger.error(error, "WorkflowMaster says workflow failed")
      trackedWorkflows.
        find(_._2 == workflow).
        foreach { kv =>
          trackedWorkflows.remove(kv._1)
          pollerMaster ! MessageFailed(kv._1)
        }

    case WorkflowForMessage(msg, workflowOption) =>
      startWorkflow(msg, workflowOption)

    case p: PollerMasterStatus =>
      pollerStatus = p

    case msg: MessageToQueue =>
      pollerMaster ! msg

    case any =>
      logger.debug("WorkflowMaster received unknown message {}", any)

  }

  private def isPolledMessageTracked(msg: PolledMessage): Boolean =
    trackedWorkflows.keys.exists(p => !msg.canWorkOnMessage(p))

  private def startWorkflow(msg: PolledMessage, workflow: Option[Workflow]) = workflow match {
    case Some(work) if !isPolledMessageTracked(msg) =>
      logger.debug(s"Workflow $work found for $msg, sending to worker master")
      trackedWorkflows += (msg -> work)

      workerMaster ! work

    case Some(_) if isPolledMessageTracked(msg) =>
      logger.info(s"Received $msg that is already tracked in a workflow, returning to queue")
      pollerMaster ! MessageFailed(msg)

    case _ =>
      logger.info("No workflow found, completing work")
      pollerMaster ! MessageComplete(msg)
  }

}

object WorkflowMasterProtocol {
  case object ReadyForMessage
  case class WorkflowForMessage(msg: PolledMessage, workflow: Option[Workflow])
  case class WorkflowIsDone(workflow: Workflow)
  case class WorkflowIsDoneWithError(workflow: Workflow, error: Throwable)
}

