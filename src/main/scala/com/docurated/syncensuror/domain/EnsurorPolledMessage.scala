package com.docurated.syncensuror.domain

import com.docurated.clustermc.protocol.PolledMessage

sealed case class EnsurorPolledMessage(organizationId: Int,
                                       version: Int,
                                       name: String,
                                       path: String,
                                       permissionsHash: String,
                                       uploadSource: String,
                                       contentIdentifier: String,
                                       visible: Boolean,
                                       underlyingId: String) extends PolledMessage {

  def id = s"$organizationId|$underlyingId"
}
