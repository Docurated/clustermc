package com.docurated.clustermc.sample.http

sealed case class HttpNotOk(message: String, code: Int)
  extends Exception(message)
