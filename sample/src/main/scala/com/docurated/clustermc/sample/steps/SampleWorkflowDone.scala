package com.docurated.clustermc.sample.steps

import akka.actor.Status.Success
import com.docurated.clustermc.sample.protocol.{ArticleMessage, ArticleMessageOnly}


class SampleWorkflowDone extends ArticleMessageOnly {
  override def wrappedReceive: Receive = {
    case article: ArticleMessage =>
      logger.debug(s"sample workflow is done for $article")
      sender() ! Success(article.copy(isAnnotated = true))
  }
}
