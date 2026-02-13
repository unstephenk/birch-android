package com.birch.podcast.ui

data class DownloadUi(
  val progress: Float?,
  val soFarBytes: Long?,
  val totalBytes: Long?,
  val etaSec: Long?,
)
