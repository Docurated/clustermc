package com.docurated.clustermc.sample.steps

import akka.actor.Status.Success
import com.docurated.clustermc.sample.http.Get
import com.docurated.clustermc.sample.protocol.{ArticleMessage, ArticleMessageOnly}
import org.apache.http.message.BasicNameValuePair
import org.joda.time.DateTime
import org.json4s.native.JsonMethods.parse

import scala.util.control.NonFatal

sealed case class MercuryResponse(
  title: String,
  content: String,
  datePublished: String,
  leadImageUrl: String,
  dek: String,
  url: String,
  domain: String,
  excerpt: String,
  wordCount: Int,
  direction: String,
  totalPages: Int,
  renderedPages: Int,
  nextPageUrl: Option[String])

class ExtractLongRead extends ArticleMessageOnly {
  private val mercuryUrl = "https://mercury.postlight.com/parser"

  override def receive: Receive = {
    case article: ArticleMessage if article.longLink.nonEmpty =>
      val extraction = mercuryExtraction(article)
      sender() ! Success(article.copy(content = Some(extraction)))

    case article =>
      logger.warning(s"can't extract a long read without link for $article")
      sender() ! Success(article)
  }

  private def mercuryExtraction(article: ArticleMessage) = {
    val mercuryResponse = Get.toString(mercuryUrl, Map("x-api-key" -> System.getProperty("mercuryKey")), List(new BasicNameValuePair("url", article.longLink.get)))
    parseResponse(mercuryResponse) match {
      case Some(r) => r.content
      case _ => rawLongRead(article)
    }
  }

  private def parseResponse(response: String) = {
    implicit val formats = org.json4s.DefaultFormats
    try {
      Some(parse(response).camelizeKeys.extract[MercuryResponse])
    } catch {
      case NonFatal(e) =>
        logger.error(e, "Failed to parse Mercury response - {}", response)
        None
    }
  }

  private def rawLongRead(article: ArticleMessage) = {
    Get.toString(article.longLink.get)
  }
}
