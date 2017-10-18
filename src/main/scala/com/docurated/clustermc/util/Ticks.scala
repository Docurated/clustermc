package com.docurated.clustermc.util

import akka.actor.Actor

import scala.concurrent.duration._

case object Tick

trait Ticks extends Actor {
  implicit val ec = context.dispatcher
  context.system.scheduler.schedule(5 seconds, 1 second, self, Tick)
}
