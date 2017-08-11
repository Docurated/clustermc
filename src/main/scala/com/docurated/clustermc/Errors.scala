package com.docurated.clustermc

case class Panic(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

case class ProcessingException(message: String = "", cause: Throwable = None.orNull)
  extends Exception(message, cause)

case class WorkflowTerminated(message: String = "")
  extends Exception(message)