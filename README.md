![Build status](https://api.travis-ci.org/Docurated/clustermc.svg?branch=master)

Cluster Emcee
-------------
This is the project page for Cluster Emcee (clustermc). Clustermc distributes processing workloads across clusters of servers/pods with high resiliency, supervision and throughput. Any workload that can be modeled as a directed acyclic graph is a good candidate to run on clustermc. A workload is defined as a [Workflow](#workflow) which is a series of [WorkflowStep](#workflowstep) with optional dependencies on successful completion. Clustermc is built on top of [Akka](http://akka.io/) (for supervision) and [Akka Cluster](http://doc.akka.io/docs/akka/current/scala/common/cluster.html) (for distribution). It is also inspired by the ["work pulling pattern"](http://www.michaelpollmeier.com/akka-work-pulling-pattern) for fair distribution and supervision of work.

Cluster Emcee is developed and maintained by [Docurated](http://www.docurated.com/)

Table of Contents
-------------
  * [Definitions](#definitions)
  * [Sample](sample/README.md)
  * [Masters](#masters)
  * [Structure and Supervision](#structure-and-supervision)
    * [Message passing](#message-passing)
    * [Supervision structure](#supervision-structure)
  * [Actor Stack](#actor-stack)
  * [Workflow](#workflow)
  * [Workflow Step](#workflow-step)
  * [Workflow Worker](#workflow-worker)
  * [Current deployments](#current-deployments)
  * [Planned refinements](#planned-refinements)

Definitions
-------------
**Master**

An actor that keeps track of work and workers. It registers and supervises workers, accepts work and distributes work among the workers.

**Worker**

An actor that actually executes a given piece of work

**Workflow**

A sequence of ordered, semi-dependent steps of worker execution.

**Workflow Step**

A single, isolated unit of processing execution. At its core, a step is a Prop definition of an Akka Actor. In keeping with the actor pattern, steps should not store or rely on internal state.

**Poller**

An actor retrieving work from an external source such as a database (e.g. Redis list), buffer (e.g. Kafka) or queue, (e.g. Rabbit).

**PolledMessage**

A unit of work that is retrieved from an external source and used to build a Workflow.

Masters
-----------
There are three distinct masters in clustermc that control message flow and supervision. Clustermc utilizes [Akka Cluster Singletons](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton) for resiliency and accessibility (singletons can be referenced through a proxy by name instead of a full actor path).

**Workflow Master**

The "workflow master" is the main actor in clustermc. It creates and supervises the other two masters, worker and poller. The workflow master will tell the poller when to poll based on capacity. It will build a workflow object from a polled message, and send the workflow object to the worker master for execution. The workflow master keeps track of all workflows currently executing. Execution is based on a schedule, at each "tick" of one second it asks the worker master how much work it has buffered. If the worker master has little or no buffered work, it will tell the poller to poll for new work.

It is a best practice to have the workflow master asynchronously build workflows from polled messages. This is due to the fact that there is a single workflow master, and often calculating the necessary steps for a workflow requires I/O, such as checking persistent storage for state. In clustermc the workflow master trait has an ActorRef for building workflows.

**Poller Master**

The "poller master" is responsible for calling to external buffers. It is common to have several different poller actors and types of pollers. Polling is not a precise term and represents the retrieval of a message of work to be done from any external system. In practice this has been queues, buffers or databases like SQS, Kafka and Redis.

The poller master maintains a map of messages received from a given poller, so that it can direct completion, failure or additional work back to the source buffer for a given polled message.

**Worker Master**

The "worker master" is responsible for distributing work among workers. Workers register with the "worker master" on creation and then get work distributed to them when idle. The master is aware of all workers and work to be done. It supervises the workers and is able to redistribute or mark work as failed when a worker is terminated.

Structure and Supervision
-----------
The structure of the actor system is very deliberate to minimize concerns around message handling and tracking and limit the amount of supervision by a single master. 

**Message passing**

![Message passing](/images/message_passing.png)

**Supervision structure**

![Supervision](/images/supervision.png)

ActorStack
-----------
Clustermc uses a pattern of stackable traits called an "actor stack". This allows for the mix-in of behaviors for actors given particular messages. For example, when receiving certain message types, you might set specific logger MDC values, forward the message up the stack, then unset them when execution on the message completes. Or you might have many actors that can only process a particular message, and receiving any other message is an error. A stackable trait like this can keep the actors' code specific to its particular concern.

For more information about stackable actor traits, see [this presentation starting at slide 13](https://www.slideshare.net/EvanChan2/akka-inproductionpnw-scala2013)

Workflow
-----------
A workflow is implemented as a polled message and a series of steps to execute against that message. For simplicity the workflow only stores a single starting step. It expects there to be no gaps or cycles in the chain of steps (see [planned refinements](#planned-refinements) below). Storing the starting step does not mean that a workflow cannot branch or re-join, instead it is common to have a no-op "start" step for purposes of creating a workflow, and then branching steps from the start.

Workflow Step
-----------
A workflow step comprises a Props specification for an actor, several lists (previous/next steps), and a record of completion. It has a list of "prerequisite steps" or steps that require completion before executing self, a list of "next if success" steps which can be executed if self completes without error, and "next if any" steps which can be executed regardless of the success of self completion.

Each step can find the next work in the chain by recursing through those three lists. It should be noted that the current implementation is too naive, there is nothing guarding against cycles other than a cap on how far searches for the next work can recurse (or follow into other steps).

Workflow Worker
-----------
The workflow workers receive a workflow and executes its steps in order. It launches an actor for each step Props specification, sends it the polled message and supervises the execution. If the worker is terminated or returns Failure, it marks that step as a failure. If there is available work, but no "next steps" it sends that workflow back to the worker master as failed work. Otherwise it sends the workflow as successful. Workflow workers send a polled message back in the success message, which could be an altered copy of the original polled message. It should be noted that, given the potential for parallel execution of sibling steps, ordering is not guaranteed and altering the polled message is only safe when there is a single step in the graph.

Workflow workers extent the Worker trait that is general to the work-pulling pattern. Each worker self-registers with the Worker Master on creation by utilizing the ClusterSyngletonProxy for the master.

Current deployments
-----------
Clustermc is currently used in production at Docurated. They run workflows for document analysis and index production on clusters with hundreds of pods and process tens of millions of documents through the system.

Authors
-----------
Ryan Cooke ([GitHub](https://github.com/debussyman), [LinkedIn](https://www.linkedin.com/in/ryancooke/))

Mike Patterson ([GitHub](https://github.com/rpatters), [LinkedIn](https://www.linkedin.com/in/mike-patterson-1592b723/))

Erik Nygren ([GitHub](https://github.com/underscorenygren), [LinkedIn](https://www.linkedin.com/in/eriknygren/))

Acknowledgements
-----------
Thanks to [William Huba](https://github.com/hexedpackets) for guidance and [Robbie Kanarek](https://www.linkedin.com/in/robert-kanarek-59150537/) for his tireless patience while scaling out clustermc.

Planned refinements
-----------
* Better status/throughput measurement
* Backoff or back pressure when workflows start failing
* Add a notion priority of workflows for better scheduling
* Assert each calculated workflow is a DAG and order execution of steps based on topological sort



