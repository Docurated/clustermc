package com.docurated.clustermc.workflow

import akka.actor.Props
import akka.actor.Status.{Failure, Success}
import com.docurated.clustermc.Panic
import com.docurated.clustermc.protocol.PolledMessage
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.mutable

case class Workflow(msg: PolledMessage, start: WorkflowStep) {
  override def toString: String = start.toString
  val createdAt: DateTime = DateTime.now(DateTimeZone.UTC)
}

/**
  * This class is not thread safe. It is intended for use with something tracking
  * steps that are currently being executed.
  * It also does not enforce acyclical connections between steps. Instead it tracks
  * the number of recursive iterations that searching for the next work and raises
  * and exception if too many recursions have occurred.
  *
  * @param id A string uniquely identifying this workflow step among many in a single workflow
  * @param props The Props for the actor that can complete this step
  */
case class WorkflowStep(id: String, props: Props) extends Comparable[WorkflowStep] {
  private val prerequisites = mutable.MutableList.empty[WorkflowStep]
  private val nextIfSuccess = mutable.MutableList.empty[WorkflowStep]
  private val nextIfAny = mutable.MutableList.empty[WorkflowStep]
  private var complete: Option[Either[Success, Failure]] = None

  def addNextOnSuccess(step: WorkflowStep): Unit = {
    step.addPrerequisite(this)
    nextIfSuccess += step
  }

  def addNextOnAny(step: WorkflowStep): Unit = {
    step.addPrerequisite(this)
    nextIfAny += step
  }

  def addPrerequisite(step: WorkflowStep): Unit = prerequisites += step

  def onSuccess(step: WorkflowStep): WorkflowStep = {
    addNextOnSuccess(step)
    step
  }

  def onComplete(step: WorkflowStep): WorkflowStep = {
    addNextOnAny(step)
    step
  }

  def nextWork(): Option[List[WorkflowStep]] = {
    findNextWork()
  }

  private def findNextWork(iterations: Int = 0): Option[List[WorkflowStep]] = {
    if (iterations > 1000)
      throw Panic("Too many work search iterations")

    if (prerequisites.exists(_.incomplete)) {
      return None
    } else if (incomplete) {
      return Some(List(this))
    }

    val next = complete match {
      case Some(Left(_)) => nextIfSuccess ++ nextIfAny
      case Some(Right(_)) => nextIfAny
    }

    val nextList = next
      .flatMap(_.findNextWork(iterations + 1))
      .flatten
      .toList

    if (nextList.nonEmpty)
      Some(nextList)
    else
      None
  }

  def completeStep(result: Either[Success, Failure]): Unit = {
    complete = Some(result)
  }

  def incomplete: Boolean = complete.isEmpty

  def failed: Boolean = complete.collect{case Right(_) => true}.nonEmpty

  def isNextIncomplete: Boolean =
    (failed && nextIfSuccess.nonEmpty) ||
      nextIfAny.exists(_.isNextIncomplete)

  override def compareTo(o: WorkflowStep): Int = id.compareTo(o.id)

  override def toString: String = {
    val next = nextIfSuccess ++ nextIfAny
    if (next.isEmpty)
      id
    else
      next.map(n => s"$id -> ${n.toString}").mkString(",")
  }
}

class WorkflowSteps(wf: Workflow) {
  private var availableWork = mutable.Queue.empty[WorkflowStep]
  private var completedWork = mutable.MutableList.empty[WorkflowStep]
  availableWork.enqueue(wf.start)

  def hasNext: Boolean = availableWork.nonEmpty
  def next: WorkflowStep = availableWork.dequeue()

  def complete(step: WorkflowStep, status: Either[Success, Failure]): Unit = {
    step.completeStep(status)
    step.nextWork().foreach(availableWork ++= _)
    completedWork += step
  }

  def isFailed: Boolean = availableWork.isEmpty && wf.start.isNextIncomplete
}