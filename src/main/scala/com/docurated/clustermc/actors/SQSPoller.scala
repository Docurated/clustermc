package com.docurated.clustermc.actors

import akka.actor.{Actor, ActorRef}
import akka.event.{DiagnosticLoggingAdapter, Logging}
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.{DeleteMessageRequest, Message, ReceiveMessageRequest}
import com.docurated.clustermc.protocol.PolledMessage
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization.write

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.control.NonFatal

case class SQSConfig(credentials: AWSCredentialsProviderChain, region: String, queueUrl: String)
case class SQSJson(message: String, tags: Option[List[String]])
case class SQSPolledMessage(id: String, message: String, tags: Option[List[String]], receipt: Message) extends PolledMessage

class SQSPoller(config: SQSConfig, master: ActorRef, canPull: Boolean = true) extends Actor {
  import com.docurated.clustermc.masters.PollersProtocol._
  implicit val ec = context.dispatcher
  val logger: DiagnosticLoggingAdapter = Logging(this)

  private val client = AmazonSQSClientBuilder
    .standard()
    .withCredentials(config.credentials)
    .withRegion(config.region)
    .build()

  master ! PollerCreated(self)

  def polling: Receive = {
    case Poll(_) =>
      // do nothing, busy

    case MessageFailed(_) =>
      // do nothing to allow the message to become visible again

    case MessageComplete(msg: PolledMessage) =>
      complete(msg)

    case MessageToQueue(msg, queueUrl) =>
      push(msg, queueUrl)

    case any =>
      logger.info(s"SQSPoller for ${config.queueUrl} received unknown message $any")
  }

  def idle: Receive = {
    case p: Poll if canPull =>
      context.become(polling)
      val sendTo = sender()
      poll onComplete {
        case scala.util.Success(messages) =>
          messages
            .flatMap(toPolledMessage)
            .foreach { msg =>
              logger.info("SQSPoller received {} from {}", msg, config.queueUrl)
              sendTo ! MessagePopped(msg, p)
            }

          context.become(idle)

        case scala.util.Failure(t) =>
          logger.warning("Failed to poll SQS {} - {}", config.queueUrl, t)
          context.become(idle)

        case _ =>
          context.become(idle)
      }

    case MessageFailed(_) =>
      // do nothing to allow the message to become visible again

    case MessageComplete(msg: PolledMessage) =>
      complete(msg)

    case MessageToQueue(msg, queueUrl) =>
      push(msg, queueUrl)

    case Poll(_) => // do nothing, can't pull

    case any =>
      logger.info(s"SQSPoller for ${config.queueUrl} received unknown message $any")
  }

  def receive: Receive = idle

  private def poll: Future[List[Message]] = Future {
    client
      .receiveMessage(receiveRequest)
      .getMessages
      .asScala
      .toList
  }

  private def complete(msg: PolledMessage) =
    sqsMessageFromPolledMessage(msg) foreach { sqs =>
      safeDelete(sqs)
    }

  private def push(msg: PolledMessage, queueUrl: String) = msg match {
    case m: SQSPolledMessage if queueUrl == config.queueUrl =>
      safeSend(m, queueUrl)
    case _ => // not deliverable
  }

  private def receiveRequest = new ReceiveMessageRequest()
    .withQueueUrl(config.queueUrl)
    .withMaxNumberOfMessages(10)
    .withVisibilityTimeout(360)
    .withWaitTimeSeconds(5)

  private def deleteRequest(sqsMessage: Message) = new DeleteMessageRequest()
    .withQueueUrl(config.queueUrl)
    .withReceiptHandle(sqsMessage.getReceiptHandle)

  private def safeDelete(sqsMessage: Message) = {
    try {
      client.deleteMessage(deleteRequest(sqsMessage))
    } catch {
      case NonFatal(e) =>
        logger.error(e, "SQSPoller failed to remove sqs message from queue")
    }
  }

  private def safeSend(polledMessage: SQSPolledMessage, queueUrl: String) = {
    implicit val formats = DefaultFormats
    val sqsJson = SQSJson(polledMessage.message, polledMessage.tags)
    try {
      client.sendMessage(queueUrl, write(sqsJson))
    } catch {
      case NonFatal(e) =>
        logger.error(e, s"Failed to send sqs message $sqsJson")
    }
  }

  private def sqsMessageFromPolledMessage(msg: PolledMessage): Option[Message] = msg match {
    case r: SQSPolledMessage => Some(r.receipt)
    case _ =>
      logger.warning("Not matching on sqs space message! {} {}", msg.getClass, msg.receipt)
      None
  }

  private def toPolledMessage(sqsMessage: Message): Option[SQSPolledMessage] = {
    parseSqsMessage(sqsMessage)
      .flatMap(p => Some(SQSPolledMessage(s"sqs-${sqsMessage.getMessageId}", p.message, p.tags, sqsMessage)))
  }

  private def parseSqsMessage(sqsMessage: Message): Option[SQSJson] = {
    implicit val formats = DefaultFormats
    try {
      Some(parse(sqsMessage.getBody).extract[SQSJson])
    } catch {
      case NonFatal(e) =>
        logger.error(e, s"Failed to parse SQS message ${sqsMessage.getBody}")
        None
    }
  }
}
