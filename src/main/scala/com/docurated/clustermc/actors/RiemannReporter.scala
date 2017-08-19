package com.docurated.clustermc.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import com.docurated.clustermc.util.ActorStack
import io.riemann.riemann.client.RiemannClient
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal

trait RiemannStat {
  def service: String
  def metric: Int
  def tags: List[String]
  def time: Long
}
case class RiemannMetric(service: String, metric: Int) extends RiemannStat {
  def tags = List()
  def time = DateTime.now(DateTimeZone.UTC).getMillis
}
case class RiemannTaggedMetric(service: String, metric: Int, tags: List[String]) extends RiemannStat {
  def time = DateTime.now(DateTimeZone.UTC).getMillis
}

trait RiemannReporter extends ActorStack {
  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    tryToClose()
    super.preRestart(reason, message)
  }

  override def postStop(): Unit = {
    tryToClose()
    super.postStop()
  }

  def host: String
  def port: Int
  def namespace: Option[String]
  def environment: String
  def machineName: String

  private val client: RiemannClient =
    RiemannClient.tcp(host, port)
  try {
    client.connect()
  } catch {
    case NonFatal(t) =>
      logger.warning("Failed to connect to Riemann at {}:{} {}", host, port, t)
  }

  override def wrappedReceive: Receive = {
    case s: RiemannStat =>
      safeSend(s)

    case _ => // do nothing
  }

  private def tryToClose() = {
    try {
      client.close()
    } catch {
      case NonFatal(t) =>
        logger.warning("Failed to close Riemann client: {}", t)
    }
  }

  private def safeSend(s: RiemannStat) = {
    try {
      val service = if (namespace.nonEmpty)
        s"${namespace.get}.${s.service}"
      else
        s.service

      val tags = s.tags :+ s"environment=${environment}"

      client
        .event()
        .service(service)
        .host(machineName)
        .metric(s.metric)
        .tags(tags.asJava)
        .time(s.time)
        .send()
        .deref(1, TimeUnit.SECONDS)
    } catch {
      case NonFatal(t) =>
//        logger.info(s"Failed to send Riemann metric: $t")
    }
  }
}