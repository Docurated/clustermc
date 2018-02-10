package com.docurated.clustermc.sample.steps

import akka.actor.Status.Success
import com.docurated.clustermc.sample.protocol.{ArticleMessage, ArticleMessageOnly}

class PrintArticle extends ArticleMessageOnly {
  override def wrappedReceive: Receive = {
    case article: ArticleMessage =>
      logger.info(s"\n\nurl: ${article.url}\ncontent:\n${article.content.toString}\ntags: ${article.tags.toString()}\n")
      sender() ! Success(article)
  }
}
