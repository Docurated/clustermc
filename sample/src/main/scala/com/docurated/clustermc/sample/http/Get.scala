package com.docurated.clustermc.sample.http


import java.io.InputStream
import java.net.URL

import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

import scala.collection.JavaConverters._

object Get {
  def toString(url: String, headers: Map[String, String] = Map(), params: List[NameValuePair] = List()): String = {
    val get = new Get(url, headers, params)
    get.readRequest()
  }

  def apply(url: String, headers: Map[String, String], params: List[NameValuePair]): InputStream = {
    val get = new Get(url, headers, params)
    get.makeRequest()
  }
}

class Get(val requestUrl: String, val headers: Map[String, String], params: List[NameValuePair])
  extends Base {

  override protected def url = {
    if (params.nonEmpty)
      new URL(s"$requestUrl?${URLEncodedUtils.format(params.asJava, "UTF-8")}")
    else
      new URL(requestUrl)
  }

  def readRequest(): String = {
    readResponse(conn)
  }

  def makeRequest(): InputStream = {
    response(conn)
  }

  private def conn = {
    val conn = getConnection
    conn.setRequestMethod("GET")
    conn.setDoOutput(false)
    conn
  }
}
