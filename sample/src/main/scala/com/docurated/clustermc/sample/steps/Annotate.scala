package com.docurated.clustermc.sample.steps

import akka.actor.Status.Success
import com.docurated.clustermc.sample.http.Post
import com.docurated.clustermc.sample.protocol.{ArticleMessage, ArticleMessageOnly}
import org.apache.http.message.BasicNameValuePair
import org.json4s.DefaultFormats

import scala.util.control.NonFatal
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write


sealed case class AmbiverseRequest(text: String)
sealed case class AmbiverseEntity(id: Option[String], url: Option[String], confidence: Option[Long])
sealed case class AmbiverseMatch(charLength: Int, charOffset: Int, text: String, entity: AmbiverseEntity)
sealed case class AmbiverseResponse(docId: String, language: String, matches: List[AmbiverseMatch])
sealed case class AmbiverseOauth(tokenType: String, accessToken: String, expiresIn: Int)

class Annotate extends ArticleMessageOnly {
  implicit val formats = DefaultFormats
  private val ambiverseUrl = "https://api.ambiverse.com/v1/entitylinking/analyze"
  private val ambiverseTokenUrl = "https://api.ambiverse.com/oauth/token"

  override def receive: Receive = {
    case article: ArticleMessage if article.content.nonEmpty =>
      val oauth = authenticate()
      val annotations = annotateWithAmbiverse(article, oauth)
      sender() ! Success(article.copy(tags = annotations))

    case article =>
      logger.warning("Cannot annotate without content")
      sender() ! Success(article)
  }

  private def annotateWithAmbiverse(article: ArticleMessage, oauth: AmbiverseOauth) = {
    val response = Post(ambiverseUrl, headers(oauth), parameters(article.content.get))
    parseResponse(response)
      .flatMap(r => Some(r.matches.map(m => m.text)))
      .toSet
      .flatten
  }

  private def parseResponse(response: String) =
    try {
      Some(parse(response).camelizeKeys.extract[AmbiverseResponse])
    } catch {
      case NonFatal(e) =>
        logger.error(e, "Failed to parse response - {}", response)
        None
    }

  private def parameters(content: String) =
    List(
      new BasicNameValuePair("data", write(AmbiverseRequest(content)))
    )

  private def headers(oauth: AmbiverseOauth) =
    Map(
      "Accept" -> "application/json",
      "Content-Type" -> "application/json",
      "Authorization" -> s"Bearer ${oauth.accessToken}"
    )

  private def authenticate() = {
    val authHeaders = Map(
      "Accept" -> "application/json",
      "Content-Type" -> "application/x-www-form-urlencoded"
    )

    val authParams = List(
      new BasicNameValuePair("client_id", System.getProperty("ambiverseId")),
      new BasicNameValuePair("client_secret", System.getProperty("ambiverseSecret")),
      new BasicNameValuePair("grant_type", "client_credentials")
    )

    try {
      val response = Post(ambiverseTokenUrl, authHeaders, authParams)
      parse(response).camelizeKeys.extract[AmbiverseOauth]
    } catch {
      case NonFatal(e) =>
        logger.error(e, "Failed to authenticate with Ambiverse")
        AmbiverseOauth("", "", 0)
    }
  }
}
