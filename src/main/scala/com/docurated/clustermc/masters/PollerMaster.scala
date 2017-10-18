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
  import com.docurated.clustermc.masters.PollersProtocol._

  private val messagesInFlight = mutable.Map.empty[PolledMessage, ActorRef]
  private val pollers = mutable.Set.empty[ActorRef]

  createPollers()

  protected def createPollers(): Unit = {
      logger.info("Creating pollers")
      ConfigFactory.load()
        .getConfigList("pollers")
        .asScala
        .flatMap { pollConfig =>
          val canPull = pollConfig.getBoolean("pull")
          val q = pollConfig.getString("queueUrl")
          val num = if (pollConfig.hasPath("numPollers"))
            pollConfig.getInt("numPollers")
          else
              1

          pollConfig.getString("type") match {
            case "sqs" =>
              val c = SQSConfig(new AWSCredentialsProviderChain(), "us-east-1", q)
              val p = context.actorOf(
                RoundRobinPool(num).props(Props(classOf[SQSPoller], c, self, canPull)),
                s"sqs-$q-poller")
              Some(p)
            case _ => None
          }
        }
  }

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
      logger.info(s"PollerMaster received unknown message {}", any)
  }

  private def completeAndTellPoller(msg: PolledMessage, toTell: Any) = {
    messagesInFlight.get(msg).foreach(poller => poller ! toTell)
    messagesInFlight.remove(msg)
  }
}

object PollersProtocol {
  case class MessageComplete(msg: PolledMessage)
  case class MessageFailed(msg: PolledMessage)
  case class PollerCreated(poller: ActorRef)
  case class MessagePopped(msg: PolledMessage, poll: Poll)
  case class Poll(requestor: ActorRef)
  case class MessageToQueue(msg: PolledMessage, queueUrl: String)
}
