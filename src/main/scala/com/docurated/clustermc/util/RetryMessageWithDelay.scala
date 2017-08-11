package com.docurated.clustermc.util

import akka.actor.Actor
import scala.concurrent.duration._

trait RetryMessageWithDelay extends Actor {
  implicit val ec = context.dispatcher

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    message.foreach(msg => context.system.scheduler.scheduleOnce(777 millis, self, msg)(ec, sender()))
  }
}
