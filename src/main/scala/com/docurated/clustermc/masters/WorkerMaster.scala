package com.docurated.clustermc.masters

import akka.actor.{ActorRef, Terminated}
import com.docurated.clustermc.WorkflowTerminated
import com.docurated.clustermc.protocol.MasterWorkerProtocol._
import com.docurated.clustermc.protocol.PollersProtocol.MessageToQueue
import com.docurated.clustermc.util.ActorStack

import scala.collection.mutable

sealed case class WorkerMasterStatus(activeWorkers: Int, idleWorkers: Int, numWorkToBeDone: Int)

trait WorkerRegistration extends ActorStack {
  protected val workers: mutable.Map[ActorRef, Option[Work]]

  override def receive: Receive = {
    case WorkerExists(worker, work) =>
      registerWorker(worker, work)

    case x @ WorkerRequestsWork(worker) =>
      registerWorker(worker, None)
      super.receive(x)

    case x @ WorkerIsDone(worker, result) =>
      registerWorker(worker, Some(result))
      super.receive(x)

    case x @ WorkerIsDoneFailed(worker, _, work) =>
      registerWorker(worker, work)
      super.receive(x)

    case any =>
      super.receive(any)
  }

  private def registerWorker(worker: ActorRef, work: Option[Work]) = {
    if (!workers.contains(worker)) {
      context watch worker
      workers += (worker -> work)
    }
  }
}

class WorkerMaster extends ActorStack with WorkerRegistration {
  override protected val workers = mutable.Map.empty[ActorRef, Option[Work]]
  private val workToBeDone = mutable.Queue.empty[Work]

  override def wrappedReceive: Receive = {
    case "status" =>
      sender() ! status

    case Terminated(worker) =>
      logger.debug(s"De-registering work $worker due to termination")
      workers.
        get(worker).
        flatten.
        foreach(work => work.requestor ! WorkIsDoneFailed(work.job, WorkflowTerminated(worker.toString())))
      workers.remove(worker)

    case WorkerRequestsWork(worker) =>
      if (workers(worker).isEmpty && workToBeDone.nonEmpty) {
        val work = workToBeDone.dequeue()
        workers += (worker -> Some(work))
        worker ! WorkToBeDone(work)
      } else {
        worker ! NoWorkToBeDone
      }

    case WorkerIsDone(worker, work) =>
      work.requestor ! WorkIsDone(work.job)
      workers += (worker -> None)
      notifyWorkers()

    case WorkerIsDoneFailed(worker, reason, _) =>
      workers
        .get(worker)
        .flatten
        .foreach(work => work.requestor ! WorkIsDoneFailed(work.job, reason))
      workers += (worker -> None)
      notifyWorkers()

    case msg: MessageToQueue =>
      workers
        .get(sender())
        .flatten
        .foreach(work => work.requestor ! msg)

    case HowBusy =>
      sender() ! status
      notifyWorkers()

    case work: Work =>
      workToBeDone.enqueue(work)
      notifyWorkers()

    case any =>
      logger.warning("received unknown message {}", any)
  }

  private def notifyWorkers(): Unit = {
    if (workToBeDone.nonEmpty) {
      workers
        .filter(w => w._2.isEmpty)
        .foreach(w => w._1 ! WorkIsReady )
    }
  }

  private def status =
    WorkerMasterStatus(workers.count(t => t._2.nonEmpty), workers.count(t => t._2.isEmpty), workToBeDone.size)
}