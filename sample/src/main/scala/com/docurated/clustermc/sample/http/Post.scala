package com.docurated.clustermc.sample.http

import java.io.{DataOutputStream, InputStream}
import javax.net.ssl.HttpsURLConnection

import org.apache.commons.io.IOUtils
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

import scala.collection.JavaConverters._

object Post {
  def apply(url: String, headers: Map[String, String], params: List[NameValuePair]): String = {
    val post = new Post(url, headers, params)
    post.readRequest()
  }
}

class Post(val requestUrl: String, val headers: Map[String, String], params: List[NameValuePair])
  extends Base {

  def readRequest(): String = {
    var wr: DataOutputStream = null
    try {
      wr = new DataOutputStream(postConnection.getOutputStream)
      wr.writeBytes(postData)
      wr.flush()

      readResponse(postConnection)
    }
    finally {
      IOUtils.closeQuietly(wr)
    }
  }

  def makeRequest(): InputStream = {
    var wr: DataOutputStream = null
    try {
      wr = new DataOutputStream(postConnection.getOutputStream)
      wr.writeBytes(postData)
      wr.flush()

      response(postConnection)
    }
    finally {
      IOUtils.closeQuietly(wr)
    }
  }

  private lazy val postConnection = {
    val c = getConnection
    c.setRequestMethod("POST")
    c.setDoOutput(true)
    c
  }

  private lazy val postData = {
    if (headers.contains("Content-Type") && headers("Content-Type") == "application/json") {
      params.head.getValue
    } else {
      URLEncodedUtils.format(params.asJava, "UTF-8")
    }
  }
}