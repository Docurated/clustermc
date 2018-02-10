package com.docurated.clustermc.sample.steps

import akka.actor.Status.Success
import com.docurated.clustermc.sample.http.Get
import com.docurated.clustermc.sample.protocol.{ArticleMessage, ArticleMessageOnly}
import org.jsoup.Jsoup

import scala.collection.JavaConverters._

class FetchLongRead extends ArticleMessageOnly {
  override def receive: Receive = {
    case article: ArticleMessage =>
      logger.info(s"maybe get article link from Long Read for $article")
      val link = linkFromLongRead(article.url)
      if (link.nonEmpty) {
        logger.debug(s"found $link for ${article.url}")
        sender() ! Success(article.copy(longLink = link))
      } else {
        logger.debug(s"found no link for ${article.url}")
        sender() ! Success(article.copy(longLink = Some(article.url)))
      }
  }

  private def linkFromLongRead(url: String) = {
    val html = Get.toString(url)
    Jsoup
      .parse(html)
      .select("a[text()='Read the story']")
      .asScala
      .map(_.attr("href"))
      .headOption
  }

}
