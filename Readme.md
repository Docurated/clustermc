

Cluster Emcee
-------------
This is the project page for Cluster Emcee (clustermc). Clustermc distributes processing workloads across clusters of servers/pods with high resiliency, supervision and throughput. Any workload that can be modeled as a directed acyclic graph is a good candidate to run on clustermc. A workload is defined as a [Workflow](#workflow) which is a series of [WorkflowStep](#workflowstep) with optional dependencies on successful completion. Clustermc is built on top of [Akka](http://akka.io/) (for supervision) and [Akka Cluster](http://doc.akka.io/docs/akka/current/scala/common/cluster.html) (for distribution). It is also inspired by the ["work pulling pattern"](http://www.michaelpollmeier.com/akka-work-pulling-pattern) for fair distribution and supervision of work.

Cluster Emcee is developed and maintained by [Docurated](http://www.docurated.com/)

Table of Contents

[TOC]

Definitions
-------------
Master
:   An actor that keeps track of work and workers. It registers and supervises workers, accepts work and distributes work among the workers.

Worker
:   An actor that actually executes a given piece of work

Workflow
:   A sequence of ordered, semi-dependent steps of worker execution.

WorkflowStep
:   A single, isolated unit of processing execution. At its core, a step is a Prop definition of an Akka Actor. In keeping with the actor pattern, steps should not store or rely on internal state.

Poller
:   An actor retrieving work from an external source such as a database (e.g. Redis list), buffer (e.g. Kafka) or queue, (e.g. Rabbit).

Masters
-----------
There are three distinct masters in clustermc that control message flow and supervision. Clustermc utilizes [Akka Cluster Singletons](http://doc.akka.io/docs/akka/current/scala/guide/modules.html#cluster-singleton) for resiliency and accessibility (singletons can be referenced through a proxy by name instead of a full actor path).

The "workflow master" is the main actor in clustermc. It creates and supervises the other two masters, worker and poller. The workflow master will tell the poller when to poll based on capacity. It will build a workflow object from a polled message, and send the workflow object to the worker master for execution.

The "poller master" is responsible for calling to external buffers. It is common to have several different poller actors and types of pollers. Polling is not a precise term and represents the retrieval of a message of work to be done from any external system. In practice this has been queues, buffers or databases like SQS, Kafka and Redis.

The "worker master" is responsible for distributing work among workers. Workers register with the "worker master" and then get work distributed to them when idle. The master is aware of all workers and work to be done. It supervises the workers and is able to redistribute or mark work as failed when a worker is terminated.

Structure and Supervision
-----------
The structure of the actor system is very deliberate to minimize concerns around message handling and tracking and limit the amount of supervision by a single master. 
###Message passing
```sequence
WorkflowMaster->PollerMaster: Poll
Note right of PollerMaster: Pop/advanced external work buffer
PollerMaster-->WorkflowMaster: Popped message
Note left of WorkflowMaster: Compute a Workflow from message
WorkflowMaster->WorkerMaster: Workfow to be done
WorkerMaster->Worker: Work available
Worker-->WorkerMaster: Request work
WorkerMaster->Worker: Work
Note right of Worker: Worker might create actors to complete work
Worker-->WorkerMaster: Work complete/failed
WorkerMaster-->WorkflowMaster: Workflow complete/failed
WorkflowMaster->PollerMaster: Message complete/failed
```
###Supervision Structure
```sequence
WorkflowMaster->PollerMaster: supervises
PollerMaster->Pollers: supervises
WorkflowMaster->WorkerMaster: supervises
WorkerMaster->Workers: supervises
```

Workflow
-----------
TODO

WorkflowStep
-----------
TODO

WorkflowStep Worker
-----------
TODO

Current deployments
-----------
Clustermc is currently use in production at Docurated. They run workflows for document analysis and index production on clusters with hundreds of pods and process tens of millions of documents through the system.

Planned refinements
-----------
Better status/throughput measurement
Backoff or back pressure when workflows start failing
Add a notion priority of workflows for better scheduling



