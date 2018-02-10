package com.docurated.clustermc.masters

import akka.actor.{ActorRef, Props}
import akka.routing.RoundRobinPool
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.docurated.clustermc.actors.{SQSConfig, SQSPoller}
import com.docurated.clustermc.masters.WorkflowMasterProtocol.ReadyForMessage
import com.docurated.clustermc.protocol.PolledMessage
import com.docurated.clustermc.util.ActorStack
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.collection.mutable

sealed case class PollerMasterStatus(pollers: Seq[String], messagesInFlight: Int)

abstract class PollerMaster extends ActorStack {
  import com.docurated.clustermc.protocol.PollersProtocol._

  private val messagesInFlight = mutable.Map.empty[PolledMessage, ActorRef]
  private val pollers = mutable.Set.empty[ActorRef]
  protected def createPollers(): Unit

  createPollers()

  override def wrappedReceive: Receive = {
    case MessageComplete(msg) =>
      completeAndTellPoller(msg, MessageComplete(msg))

    case MessageFailed(msg) =>
      completeAndTellPoller(msg, MessageFailed(msg))

    case ReadyForMessage =>
      val p = Poll(sender())
      pollers.foreach(poller => poller ! p)
      sender() ! PollerMasterStatus(pollers.map(_.toString()).toList, messagesInFlight.size)

    case msg: MessagePopped =>
      messagesInFlight.put(msg.msg, sender())
      msg.poll.requestor ! msg.msg

    case PollerCreated(poller) =>
      logger.debug(s"adding poller $poller")
      pollers += poller

    case msg: MessageToQueue =>
      pollers.foreach(poller => poller ! msg)

    case any =>
      logger.info(s"received unknown message {}", any)
  }

  private def completeAndTellPoller(msg: PolledMessage, toTell: Any) = {
    messagesInFlight.get(msg).foreach(poller => poller ! toTell)
    messagesInFlight.remove(msg)
  }
}
