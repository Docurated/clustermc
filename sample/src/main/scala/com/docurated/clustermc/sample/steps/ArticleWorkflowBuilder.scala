package com.docurated.clustermc.sample.steps

import akka.actor.Props
import akka.event.LoggingAdapter
import com.docurated.clustermc.sample.protocol.ArticleMessage
import com.docurated.clustermc.workflow.{Workflow, WorkflowStep}

object ArticleWorkflowBuilder {
  def apply(article: ArticleMessage, logger: LoggingAdapter): Option[Workflow] = {
    // Base case indicating workflow has been completed
    if (article.isAnnotated)
      return None

    val steps = WorkflowStep(s"fetch-$article", Props[FetchLongRead])
    steps onComplete
      WorkflowStep(s"extract-$article", Props[ExtractLongRead]) onComplete
      WorkflowStep(s"annotate-$article", Props[Annotate]) onComplete
      WorkflowStep(s"print-$article", Props[PrintArticle]) onComplete
      WorkflowStep(s"done-$article", Props[SampleWorkflowDone])

    Some(Workflow(article, steps))
  }
}
