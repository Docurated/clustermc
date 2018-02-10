package com.docurated.clustermc.sample.protocol

import com.docurated.clustermc.util.ActorStack

trait ArticleMessageOnly extends ActorStack {
  override def wrappedReceive: Receive = {
    case x: ArticleMessage =>
      val mdc = Map("url" -> x.url)
      logger.mdc(mdc)
      super.receive(x)
      logger.clearMDC()

    case any =>
      logger.warning("Received a message that is not an ArticleMessage {}", any)
  }
}
