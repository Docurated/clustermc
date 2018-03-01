package com.docurated.clustermc.sample

import akka.actor.{ActorSystem, CoordinatedShutdown, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.docurated.clustermc.masters.WorkerMaster
import com.docurated.clustermc.sample.pollers.LongReadsPollerMaster
import com.docurated.clustermc.workers.WorkflowWorker
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

object Sample extends App with LazyLogging {
  val akkaPort = 2551

  println("Enter Mercury API credentials")
  val mercuryKey = scala.io.StdIn.readLine()
  System.setProperty("mercuryKey", mercuryKey)

  println("Enter Ambiverse API client id")
  val ambiverseId = scala.io.StdIn.readLine()
  System.setProperty("ambiverseId", ambiverseId)

  println("Enter Ambiverse API client secret")
  val ambiverseSecret = scala.io.StdIn.readLine()
  System.setProperty("ambiverseSecret", ambiverseSecret)

  logger.info("Creating cluster system")

  private val config = ConfigFactory.parseString(s"akka.remote.netty.tcp.port=$akkaPort").
    withFallback(ConfigFactory.parseString("""akka.cluster.seed-nodes=["akka.tcp://ClusterSystem@127.0.0.1:2551"]""")).
    withFallback(ConfigFactory.load())

  implicit val system = ActorSystem("ClusterSystem", config)
  private val cluster = Cluster(system)

  logger.info("Creating poller master")
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props[LongReadsPollerMaster],
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)),
    name = "pollerMaster")

  logger.info("Creating worker master")
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props[WorkerMaster],
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)),
    name = "workerMaster")

  logger.info("Creating workflow master")
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props[SampleWorkflowMaster],
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)),
    name = "workflowMaster")

  startWorkflowWorkers()

  sys.addShutdownHook(logger.info("shutdown hook called, system terminating."))
  sys.addShutdownHook(CoordinatedShutdown(system).run())

  private def startWorkflowWorkers() = {
    val numWorkers = if (config.hasPath("clustermc.workflow-workers-per-node"))
      config.getInt("clustermc.workflow-workers-per-node")
    else
      2

    logger.info(s"Starting $numWorkers workflow workers")
    for(i <- 1 to numWorkers)
      system.actorOf(Props[WorkflowWorker])
  }
}
