package com.docurated.clustermc.sample.pollers

import java.io.InputStream
import java.net.URL

import akka.actor.{Actor, ActorRef}
import akka.event.{DiagnosticLoggingAdapter, Logging}
import com.docurated.clustermc.protocol.PolledMessage
import com.docurated.clustermc.sample.http.Get
import com.docurated.clustermc.sample.protocol.ArticleMessage
import com.rometools.rome.io.{SyndFeedInput, XmlReader}
import org.apache.commons.io.IOUtils

import scala.collection.mutable
import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/**
  * This is a sample poller that illustrates how to accomplish work pulling from an external buffer
  * with Cluster Emcee. It is not a true "poller" in the sense that it is pulling from a queue or subscribing to
  * a pub/sub. Instead it maintains an internal stack of links read from an RSS feed.
  *
  * It is best practice to make the actual call to poll/pop asynchronously, so that the poller is not blocking
  * on i/o. This can dramatically increase throughput when messages are being pulled and completed at a high rate,
  * as the poller can continue to complete messages even while pulling the next work.
  *
  * Typically a poller will handle MessageComplete/Failed in a way appropriate for the external buffer,
  * such as deleting the message, resetting it's visibility timeout, or pushing a dead letter.
  */
class LongReadsRSS(master: ActorRef) extends Actor {
  import com.docurated.clustermc.protocol.PollersProtocol._
  implicit val ec = context.dispatcher
  val logger: DiagnosticLoggingAdapter = Logging(this)
  private val links = new mutable.Stack[String]()
  private val rssUrl = "https://longreads.com/feed/"
  private val rssInput = new SyndFeedInput()

  master ! PollerCreated(self)

  maybeFetchMoreLinks()

  def polling: Receive = {
    case Poll(_) => // do nothing, busy

    case MessageFailed(msg: PolledMessage) =>
      logger.warning(s"LongReadsRSS message {} failed", msg)

    case MessageComplete(msg: PolledMessage) =>
      logger.info(s"LongReadsRSS message {} complete!", msg)

    case MessageToQueue(_, _) =>
      // N.A. for this contrived example

    case any =>
      logger.info(s"LongReadsRSS received unknown message $any")
  }

  def idle: Receive = {
    case p: Poll =>
      context.become(polling)
      val sendTo = sender()
      poll onComplete {
        case scala.util.Success(message) =>
          message
            .flatMap(toPolledMessage)
            .foreach { msg =>
              sendTo ! MessagePopped(msg, p)
            }

          context.become(idle)

        case scala.util.Failure(t) =>
          logger.warning("failed to poll LongReadsRSS - {}", t)
          context.become(idle)

        case _ =>
          context.become(idle)
      }

    case MessageFailed(msg: PolledMessage) =>
      logger.warning(s"message {} failed", msg)

    case MessageComplete(msg: PolledMessage) =>
      logger.info(s"message {} complete!", msg)

    case MessageToQueue(_, _) =>
      // N.A. for this contrived example

    case any =>
      logger.info(s"received unknown message $any")
  }

  def receive: Receive = idle

  private def poll: Future[Option[String]] = Future {
    if (links.nonEmpty)
      Some(links.pop())
    else
      None
  }

  private def toPolledMessage(url: String) = {
    val parsedUrl = new URL(url)
    val prettyId = parsedUrl.getPath.split("/").last.replaceAll("\\W", "-")
    Some(ArticleMessage(prettyId, url, url, Some(url)))
  }

  private def maybeFetchMoreLinks() = if (links.isEmpty) {
    var is: InputStream = null
    try {
      is = Get(rssUrl, Map.empty, List.empty)
      rssInput
        .build(new XmlReader(is))
        .getEntries
        .asScala
        .map(e => e.getLink)
        .foreach(l => links.push(l))
    } catch {
      case NonFatal(e) =>
        logger.error(e, "Failed to read RSS feed")
    } finally {
      IOUtils.closeQuietly(is)
    }
  }
}
