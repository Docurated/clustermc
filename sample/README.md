Sample workload
-------------
This is a working example of the kind of distributed work loads that are suited for Cluster Emcee. We prefer to read code over documentation, and this is a starting point in learning the pieces that are necessary to implement a working cluster for work distribution. It involves a series of steps, some of which call external services (e.g. blocking I/O). You can invoke the example by running `sbt sample/run`. You will be prompted for API credentials for Mercury and Ambiverse, but if you don't have accounts with those services, you can enter bogus values to see the order of execution as the sample workflow is designed to progress even on step failure.

Workflow steps
-------------
This example fetches links from [LongReads](https://longreads.com/) RSS feed, sanitizes the HTML and annotates named entities that appear in the text. You can see the construction of the workflow in [ArticleWorkflowBuilder](src/main/scala/com/docurated/clustermc/sample/steps/ArticleWorkflowBuilder.scala)
  * Step 1: Fetch, which attempts to find a link to the source article
  * Step 2: Extract, which uses the Mercury API to sanitize the HTML into semi-plain text
  * Step 3: Annotate, which uses Ambiverse API to find named entities in the text
  * Step 4: Print the results
  * Step 5: Mark the workflow as done in the message, so subsequent calls to the workflow builder return no new workflow

Key class implementation
-------------
There are a few classes that are worth reading, as they are necessary extensions of abstract classes for a functional clustermc.

  * [Sample](src/main/scala/com/docurated/clustermc/sample/Sample.scala), the main class and sets up the Akka cluster
  * [LongReadsPollerMaster](src/main/scala/com/docurated/clustermc/sample/pollers/LongReadsPollerMaster.scala), the poller master created in the main class as a cluster singleton. This poller master creates the RSS feed "poller" actor. It is common to have many pollers reporting to the poller master, defined by configuration.
  * [SampleWorkflowMaster](src/main/scala/com/docurated/clustermc/sample/SampleWorkflowMaster.scala), the workflow master created in the main class as a cluster singleton. This handles the creation of workflows in response to messages sent by pollers.
  * [LongReadsRSS](src/main/scala/com/docurated/clustermc/sample/pollers/LongReadsRSS.scala), a sample poller actor that interfaces with an external queue or buffer. In this sample application, it is making a request for an RSS feed and maintaining the "queue" internally.
  * [ArticleWorkflowBuilder](src/main/scala/com/docurated/clustermc/sample/steps/ArticleWorkflowBuilder.scala), an object that builds a workflow with step dependencies based on a message.
  * [Workflow steps](src/main/scala/com/docurated/clustermc/sample/steps), individual actors that implement discreet steps of the sample application, such as calling external APIs.
  * [ArticleMessageOnly](src/main/scala/com/docurated/clustermc/sample/protocol/ArticleMessageOnly.scala), an example of a stackable actor trait that expects to receive a particular message type and returns an error in all other cases.
