package com.docurated.clustermc.workflow

import akka.actor.Status.{Failure, Success}

import scala.collection.mutable

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
