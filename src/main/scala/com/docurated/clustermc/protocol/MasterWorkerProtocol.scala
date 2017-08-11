package com.docurated.clustermc.protocol

import akka.actor.ActorRef

object MasterWorkerProtocol {

  // Messages from Workers
  case class WorkerCreated(worker: ActorRef)

  case class WorkerRequestsWork(worker: ActorRef)

  case class WorkIsDone(worker: ActorRef, work: Any)

  case class WorkIsDoneFailed(worker: ActorRef, reason: Throwable)

  case class WorkToQueue(work: Any, delay: Integer)

  // Messages to Workers
  case class WorkToBeDone(work: Any)

  case object WorkIsReady

  case object NoWorkToBeDone

  // Messages to Master
  case object HowBusy
}
