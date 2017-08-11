package com.docurated.clustermc.masters

import akka.actor.{Actor, ActorRef, Terminated}
import com.docurated.clustermc.WorkflowTerminated
import com.docurated.clustermc.workflow.Workflow
import com.docurated.clustermc.masters.PollersProtocol.MessageToQueue
import com.docurated.clustermc.masters.WorkflowMasterProtocol._
import com.docurated.clustermc.protocol.MasterWorkerProtocol._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.duration._

sealed case class WorkerMasterStatus(activeWorkers: Int, idleWorkers: Int, numWorkToBeDone: Int)

class WorkerMaster extends Actor with LazyLogging {
  implicit val ec = context.dispatcher

  private val workers = mutable.Map.empty[ActorRef, Option[(ActorRef, Workflow)]]
  private val workToBeDone = mutable.Queue.empty[(ActorRef, Workflow)]

  context.system.scheduler.schedule(5 seconds, 1 seconds, self, "CheckWork!")

  override def receive: Receive = {
    case "status" =>
      sender() ! status

    case Terminated(worker) =>
      logger.debug(s"De-registering work $worker due to termination")
      workers.
        get(worker).
        flatten.
        foreach(work => work._1 ! WorkflowIsDoneWithError(work._2, WorkflowTerminated(worker.toString())))
      workers.remove(worker)

    case WorkerCreated(worker) if !workers.contains(worker) =>
      logger.debug(s"Registering worker $worker")
      context watch worker
      workers += (worker -> None)

    case WorkerRequestsWork(worker) =>
      if (workers.contains(worker) && workers(worker).isEmpty && workToBeDone.nonEmpty) {
        val work = workToBeDone.dequeue()
        workers += (worker -> Some(work))
        worker ! WorkToBeDone(work._2)
      } else {
        worker ! NoWorkToBeDone
      }

    case WorkIsDone(worker, result: Workflow) =>
      workers.
        get(worker).
        flatten.
        foreach(work => work._1 ! WorkflowIsDone(result))
      workers += (worker -> None)

    case WorkIsDoneFailed(worker, reason) =>
      workers
        .get(worker)
        .flatten
        .foreach(work => work._1 ! WorkflowIsDoneWithError(work._2, reason))
      workers += (worker -> None)

    case msg: MessageToQueue =>
      workers
        .get(sender())
        .flatten
        .foreach(work => work._1 ! msg)

    case HowBusy =>
      sender() ! status

    case work: Workflow =>
      workToBeDone.enqueue((sender(), work))
      self ! "Work!"

    // Anything other than our own protocol is "work to be done"
    // so check for work and maybe notify workers
    case any =>
      notifyWorkers()
  }

  private def notifyWorkers(): Unit = {
    workers.foreach {
      case (worker, m) if m.isEmpty => worker ! WorkIsReady
      case _ =>
    }
  }

  override def toString: String = self.path.toString

  private def status =
    WorkerMasterStatus(workers.count(t => t._2.nonEmpty), workers.count(t => t._2.isEmpty), workToBeDone.size)
}