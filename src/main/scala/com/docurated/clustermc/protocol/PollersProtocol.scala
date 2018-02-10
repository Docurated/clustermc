package com.docurated.clustermc.protocol

import akka.actor.ActorRef

object PollersProtocol {
  case class MessageComplete(msg: PolledMessage)
  case class MessageFailed(msg: PolledMessage)
  case class PollerCreated(poller: ActorRef)
  case class MessagePopped(msg: PolledMessage, poll: Poll)
  case class Poll(requestor: ActorRef)
  case class MessageToQueue(msg: PolledMessage, queueUrl: String)
}
