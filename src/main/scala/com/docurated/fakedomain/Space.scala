package com.docurated.fakedomain

object Space {
  def findIt(spaceId: Int): Option[Space] = {
    Some(new Space(-1, "", -1, true, "", "", "", ""))
  }
}

sealed case class Space(organizationId: Int, versionSeries: String, versionLabel: Int, visible: Boolean, name: String, path: String, permissionsHash: String, contentIdentifier: String)
