package com.docurated.clustermc.protocol

import akka.actor.ActorRef

object MasterWorkerProtocol {
  case class Work(requestor: ActorRef, job: Any)
  case class WorkIsDone(job: Any)
  case class WorkIsDoneFailed(job: Any, reason: Throwable)

  // Messages from Workers
  case class WorkerExists(worker: ActorRef, work: Option[Work])

  case class WorkerRequestsWork(worker: ActorRef)

  case class WorkerIsDone(worker: ActorRef, work: Work)

  case class WorkerIsDoneFailed(worker: ActorRef, reason: Throwable, work: Option[Work])

  // Messages to Workers
  case class WorkToBeDone(work: Work)

  case object WorkIsReady

  case object NoWorkToBeDone

  // Messages to Master
  case object HowBusy
}
