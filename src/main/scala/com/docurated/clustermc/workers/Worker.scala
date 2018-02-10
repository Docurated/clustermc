package com.docurated.clustermc.workers

import akka.actor.{Actor, actorRef2Scala}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.docurated.clustermc.protocol.PollersProtocol.MessageToQueue
import com.docurated.clustermc.protocol.MasterWorkerProtocol._
import com.typesafe.scalalogging.LazyLogging

trait Worker extends Actor with LazyLogging {
  private val cluster = Cluster(context.system)
  protected val master = context.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/workerMaster",
      settings = ClusterSingletonProxySettings(context.system)),
      name = "workerMasterProxy")

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp], classOf[MemberRemoved])
  override def postStop(): Unit = cluster.unsubscribe(self)

  private var currentWork: Option[Work] = None

  def doWork(work: Any): Unit
  def whileWorking(work: Any): Unit

  def working(work: Any): Receive = {
    case WorkIsReady | NoWorkToBeDone =>

    case CurrentClusterState(_, _, _, _, _) | MemberUp(_) | MemberRemoved(_, _) =>
      register()

    case WorkToBeDone(_) =>
      logger.error("Yikes. Master told me to do work, while I'm working.")

    case toQ: MessageToQueue =>
      master ! toQ

    case any => whileWorking(any)
  }

  def idle: Receive = {
    case CurrentClusterState(_, _, _, _, _) | MemberUp(_) | MemberRemoved(_, _) =>
      register()

    case WorkIsReady =>
      sender() ! WorkerRequestsWork(self)

    case WorkToBeDone(work) =>
      context.become(working(work))
      currentWork = Some(work)
      doWork(work.job)

    case NoWorkToBeDone =>

    case any =>
      logger.debug(s"Received unknown message $any from $sender() - $self")
  }

  private def register() =
    master ! WorkerExists(self, currentWork)

  def receive: Receive = idle

  protected def workComplete(result: Any): Unit = {
    context.become(idle)
    if (currentWork.nonEmpty) {
      val completedWork = currentWork.get.copy(job = result)
      currentWork = None
      master ! WorkerIsDone(self, completedWork)
    } else {
      logger.warn("Worker completing work but has no current work")
    }
  }

  protected def workFailed(reason: Throwable): Unit = {
    logger.error("Work failed", reason)
    context.become(idle)
    val failedWork = currentWork
    currentWork = None
    master ! WorkerIsDoneFailed(self, reason, failedWork)
  }
}
