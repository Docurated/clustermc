package com.docurated.clustermc.sample.protocol

import com.docurated.clustermc.protocol.PolledMessage

sealed case class ArticleMessage(id: String,
                                 receipt: String,
                                 url: String,
                                 longLink: Option[String] = None,
                                 content: Option[String] = None,
                                 tags: Set[String] = Set(),
                                 isAnnotated: Boolean = false) extends PolledMessage {

  override def equals(obj: scala.Any): Boolean = obj match {
    case x: ArticleMessage => this.url == x.url
    case _ => super.equals(obj)
  }
}
