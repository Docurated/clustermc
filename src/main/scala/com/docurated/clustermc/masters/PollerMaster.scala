package com.docurated.clustermc.masters

import akka.actor.ActorRef
import com.docurated.clustermc.masters.WorkflowMasterProtocol.ReadyForMessage
import com.docurated.clustermc.protocol.PolledMessage
import com.docurated.clustermc.util.ActorStack

import scala.collection.mutable

sealed case class PollerMasterStatus(pollers: Seq[String], messagesInFlight: Int)

class PollerMaster extends ActorStack {
  import com.docurated.clustermc.masters.PollersProtocol._

  private val messagesInFlight = mutable.Map.empty[PolledMessage, ActorRef]
  private val pollers = mutable.Set.empty[ActorRef]

//  logger.info("Creating pollers")
//  ConfigFactory.load()
//    .getConfigList("pollers")
//    .asScala
//    .flatMap { pollConfig =>
//      val canPull = pollConfig.getBoolean("pull")
//      val q = pollConfig.getString("queueUrl")
//      val num = if (pollConfig.hasPath("numPollers"))
//        pollConfig.getInt("numPollers")
//      else
//          1
//
//      pollConfig.getString("type") match {
//        case "sqs" =>
//          val c = SQSConfig(new DocuratedAWSCredentialsProviderChain, q)
//          val p = context.actorOf(
//            RoundRobinPool(num).props(Props(classOf[SQSPoller], c, self, canPull)),
//            s"sqs-$q-poller")
//          Some(p)
//        case "pq" =>
//          val c = PQConfig(q, pollConfig.getString("host"))
//          val p = context.actorOf(Props(classOf[PQPoller], c, self, canPull), s"pq-$q-poller")
//          Some(p)
//        case "salesforce" =>
//          val p = context.actorOf(Props(classOf[SalesforceObjectPoller], self, canPull), "sfobject-poller")
//          Some(p)
//        case "rebuild-solr" =>
//          val s = Option(pollConfig.getInt("startAt")).getOrElse(0)
//          val e = Option(pollConfig.getInt("endAt")).getOrElse(Int.MaxValue)
//          val p = context.actorOf(Props(classOf[RebuildSolrPoller], self, s, e, canPull), "rebuild-solr-poller")
//          Some(p)
//        case _ => None
//      }
//    }

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
