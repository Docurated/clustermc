package com.docurated.clustermc.sample.http

import java.io.InputStream
import java.net.{HttpURLConnection, URL}
import javax.net.ssl.HttpsURLConnection

import org.apache.commons.io.IOUtils

abstract class Base() {
  val requestUrl: String
  val headers: Map[String, String]
  def readRequest(): String
  def makeRequest(): InputStream

  protected def url = new URL(requestUrl)

  protected def getConnection: HttpsURLConnection = {
    val c = url.openConnection().asInstanceOf[HttpsURLConnection]
    headers
      .foreach(h => c.setRequestProperty(h._1, h._2))

    c
  }

  protected def readResponse(conn: HttpsURLConnection): String = {
    var is: InputStream = null
    try {
      is = response(conn)
      inputStreamToString(is)
    }
    finally {
      IOUtils.closeQuietly(is)
    }
  }

  protected def response(conn: HttpsURLConnection): InputStream = {
    var is: InputStream = null
    if (conn.getResponseCode != HttpURLConnection.HTTP_OK) {
      val msg = StringBuilder.newBuilder
      msg.append(s"Server returned HTTP response code: ${conn.getResponseCode} for URL ${conn.getURL}")
      is = conn.getErrorStream
      if (is != null) {
        msg.append(s", Error details : ${inputStreamToString(is)}")
      }
      throw HttpNotOk(msg.toString(), conn.getResponseCode)
    }

    conn.getInputStream
  }

  private def inputStreamToString(is: InputStream): String = {
    val s: java.util.Scanner = new java.util.Scanner(is).useDelimiter("\\A")
    if (s.hasNext())
      s.next()
    else
      ""
  }
}
