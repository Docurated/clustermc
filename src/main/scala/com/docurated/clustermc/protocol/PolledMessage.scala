package com.docurated.clustermc.protocol

import org.joda.time.{DateTime, DateTimeZone}

trait PolledMessage {
  def id: String
  def receipt: Any
  val createdAt: DateTime = DateTime.now(DateTimeZone.UTC)

  override def toString = id

  override def hashCode(): Int = id.hashCode

  override def equals(obj: scala.Any): Boolean = obj match {
    case x: PolledMessage => this.id == x.id
    case _ => super.equals(obj)
  }

  /**
    * A test that indicates another message currently in a workflow precludes this one from
    * being in a concurrent workflow. For example, a given message may have a workflow that
    * includes writing state to a database outside of a transaction and would result in
    * non-deterministic state if concurrent workflows were running. This allows PolledMessages
    * to have other definitions of comparison besides its id. PolledMessages that cannot be
    * used in a workflow are returned to the poller as a failed message.
    *
    * @param other Another polled message that is currently executing in a workflow
    * @return True or False indicating whether a given message can be executed in a workflow
    */
  def canWorkOnMessage(other: PolledMessage): Boolean = id == other.id
}
