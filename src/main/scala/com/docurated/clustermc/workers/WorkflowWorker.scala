package com.docurated.clustermc.workers

import akka.actor.Status.{Failure, Success}
import akka.actor.SupervisorStrategy.{Restart, Stop}
import akka.actor.{ActorRef, OneForOneStrategy, Terminated}
import com.docurated.clustermc.protocol.MasterWorkerProtocol.WorkIsDoneFailed
import com.docurated.clustermc.protocol.{PolledMessage, RetryableStep}
import com.docurated.clustermc.workflow.{Workflow, WorkflowStep, WorkflowSteps}
import com.docurated.clustermc.{ProcessingException, WorkflowTerminated}

import scala.collection.mutable
import scala.reflect.runtime.{universe => ru}

case object DoMoreWork

class WorkflowWorker extends Worker {
  import scala.concurrent.duration._

  override def postRestart(reason: Throwable): Unit = {
    super.postRestart(reason)
    reset()
    workFailed(ProcessingException("WorkflowWorker restarted"))
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 5 minutes) {
      case _ if stepIsRetryable(sender()) => Restart
      case _ => Stop
    }

  private var currentWorkflow: Workflow = null
  private var steps: WorkflowSteps = null
  private var currentWork = mutable.Map.empty[ActorRef, WorkflowStep]
  private val maxConcurrency = 10

  override def doWork(work: Any): Unit = work match {
    case wf: Workflow =>
      reset()
      currentWorkflow = wf
      steps = new WorkflowSteps(wf)

      self ! DoMoreWork

    case any =>
      logger.debug(s"WorkflowWorker received unexpected message $any")
  }

  override def whileWorking(work: Any): Unit = work match {
    case DoMoreWork =>
      doMoreWork()

    case Success(res: PolledMessage) =>
      currentWorkflow = currentWorkflow.copy(msg = res)
      completeWork(sender(), Left(Success()))

    case f: Failure =>
      logger.warn(s"Workflow step failed", f.cause)
      completeWork(sender(), Right(f))

    case Terminated(worker) =>
      logger.warn(s"Workflow step $worker was terminated")
      completeWork(worker, Right(Failure(WorkflowTerminated(s"$worker was terminated"))))

    case any =>
      logger.debug(s"WorkflowWorker received unexpected message $any")
  }

  private def doMoreWork() = {
    if (steps.hasNext && currentWork.size < maxConcurrency) {
      startWorkForStep(steps.next)
      self ! DoMoreWork
    } else if (!steps.hasNext && currentWork.isEmpty && currentWorkflow != null) {
      completeWorkflow(steps.isFailed)
    }
  }

  private def completeWorkflow(failed: Boolean) = {
    val cw = currentWorkflow
    reset()

    if (failed)
      workFailed(ProcessingException("Some workflow step failed"))
    else
      workComplete(cw)
  }

  private def startWorkForStep(step: WorkflowStep) = {
    val actor = context.actorOf(step.props, nameForStep(step))
    context watch actor
    currentWork.put(actor, step)

    actor ! currentWorkflow.msg
  }

  private def completeWork(sender: ActorRef, status: Either[Success, Failure]) = {
    context unwatch sender
    context stop sender
    currentWork.remove(sender).foreach { step =>
      steps.complete(step, status)
      self ! DoMoreWork
    }
  }

  private def reset() = {
    currentWork = mutable.Map.empty[ActorRef, WorkflowStep]
    currentWorkflow = null
    steps = null
  }

  private def nameForStep(step: WorkflowStep) =
    s"Workflow.${step.id}.${step.props.clazz.toString.split("\\.").last}"

  private def stepIsRetryable(worker: ActorRef): Boolean = {
    currentWork.get(worker).flatMap { step =>
      val clazz = step.props.clazz
      val check = ru.runtimeMirror(clazz.getClassLoader)
        .classSymbol(clazz)
        .toType
        .typeSymbol
        .asClass
        .annotations
        .exists(a => a.tree.tpe == ru.typeOf[RetryableStep])

      Some(check)
    }.getOrElse(false)
  }
}
