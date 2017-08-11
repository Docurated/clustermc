package com.docurated.clustermc.workers

import akka.actor.{Actor, actorRef2Scala}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberRemoved, MemberUp}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.docurated.clustermc.masters.PollersProtocol.MessageToQueue
import com.docurated.clustermc.protocol.MasterWorkerProtocol._
import com.typesafe.scalalogging.LazyLogging

trait Worker extends Actor with LazyLogging {
  private val cluster = Cluster(context.system)
  protected val master = context.actorOf(
    ClusterSingletonProxy.props(
      singletonManagerPath = "/user/workerMaster",
      settings = ClusterSingletonProxySettings(context.system)),
      name = "workerMasterProxy")

  context watch master

  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp], classOf[MemberRemoved])
  override def postStop(): Unit = cluster.unsubscribe(self)

  case class WorkComplete(result: Any)
  case class WorkFailed(reason: Throwable)

  def doWork(work: Any): Unit
  def whileWorking(work: Any): Unit

  def working(work: Any): Receive = {
    case WorkIsReady | NoWorkToBeDone =>

    case CurrentClusterState(_, _, _, _, _) | MemberUp(_) | MemberRemoved(_, _) =>

    case WorkToBeDone(_) =>
      logger.error("Yikes. Master told me to do work, while I'm working.")

    case WorkComplete(result) =>
      logger.debug(s"Work is complete.  Result {} - $self", result)
      context.become(idle)
      master ! WorkIsDone(self, result)

    case WorkFailed(reason) =>
      logger.error("Work failed", reason)
      context.become(idle)
      master ! WorkIsDoneFailed(self, reason)

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
      doWork(work)

    case NoWorkToBeDone =>

    case any =>
      logger.debug(s"Received unknown message $any from $sender() - $self")
  }

  private def register() =
    master ! WorkerCreated(self)

  def receive: Receive = idle
}
