package com.docurated.clustermc.util

import akka.actor.Actor
import akka.event.{DiagnosticLoggingAdapter, Logging}

trait ActorStack extends Actor {
  val logger: DiagnosticLoggingAdapter = Logging(this)

  def wrappedReceive: Receive

  def receive: Receive = {
    case x => if (wrappedReceive.isDefinedAt(x)) wrappedReceive(x) else unhandled(x)
  }

  override def unhandled(msg: Any): Unit = {
    logger.error(s"ActorStack received unhandled message $msg")
  }
}
