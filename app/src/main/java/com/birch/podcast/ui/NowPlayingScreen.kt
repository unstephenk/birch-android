package com.birch.podcast.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
  title: String?,
  podcastTitle: String?,
  episodeDate: String?,
  isPlaying: Boolean,
  positionMs: Long,
  durationMs: Long,
  playbackSpeed: Float,
  playbackPitch: Float,
  sleepTimerLabel: String?,
  skipSilenceEnabled: Boolean,
  boostVolumeEnabled: Boolean,
  trimIntroSec: Int,
  trimOutroSec: Int,
  chapters: List<ChapterUi>,
  onBack: () -> Unit,
  onOpenQueue: () -> Unit,
  onSetSleepTimerOff: () -> Unit,
  onSetSleepTimerMinutes: (Int) -> Unit,
  onSetSleepTimerEndOfEpisode: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onPlayPause: () -> Unit,
  onRewind15: () -> Unit,
  onForward30: () -> Unit,
  onSetSpeed: (Float) -> Unit,
  onSetPitch: (Float) -> Unit,
  onToggleSkipSilence: (Boolean) -> Unit,
  onToggleBoostVolume: (Boolean) -> Unit,
  onSetTrimIntroSec: (Int) -> Unit,
  onSetTrimOutroSec: (Int) -> Unit,
  onSeekToChapter: (Long) -> Unit,
) {
  var speedMenuOpen by remember { mutableStateOf(false) }
  var pitchMenuOpen by remember { mutableStateOf(false) }
  var timerMenuOpen by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.surface,
    topBar = {
      TopAppBar(
        title = { Text("Now Playing") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = onOpenQueue) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
          }

          IconButton(onClick = { timerMenuOpen = true }) {
            Icon(Icons.Filled.Timer, contentDescription = "Sleep timer")
          }
          DropdownMenu(expanded = timerMenuOpen, onDismissRequest = { timerMenuOpen = false }) {
            if (!sleepTimerLabel.isNullOrBlank()) {
              DropdownMenuItem(
                text = { Text("Timer: $sleepTimerLabel") },
                onClick = { /* no-op */ },
                enabled = false,
              )
            }
            DropdownMenuItem(
              text = { Text("Off") },
              onClick = {
                timerMenuOpen = false
                onSetSleepTimerOff()
              }
            )
            listOf(15, 30, 60).forEach { m ->
              DropdownMenuItem(
                text = { Text("$m min") },
                onClick = {
                  timerMenuOpen = false
                  onSetSleepTimerMinutes(m)
                }
              )
            }
            DropdownMenuItem(
              text = { Text("End of episode") },
              onClick = {
                timerMenuOpen = false
                onSetSleepTimerEndOfEpisode()
              }
            )
          }

          IconButton(onClick = { speedMenuOpen = true }) {
            Text("${playbackSpeed}x", style = MaterialTheme.typography.labelLarge)
          }
          DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
            listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f).forEach { s ->
              DropdownMenuItem(
                text = { Text("${s}x") },
                onClick = {
                  speedMenuOpen = false
                  onSetSpeed(s)
                }
              )
            }
          }

          IconButton(onClick = { pitchMenuOpen = true }) {
            Text("${playbackPitch}p", style = MaterialTheme.typography.labelLarge)
          }
          DropdownMenu(expanded = pitchMenuOpen, onDismissRequest = { pitchMenuOpen = false }) {
            listOf(0.9f, 1.0f, 1.1f).forEach { p ->
              DropdownMenuItem(
                text = { Text("${p}x pitch") },
                onClick = {
                  pitchMenuOpen = false
                  onSetPitch(p)
                }
              )
            }
          }
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = title ?: "—",
        style = MaterialTheme.typography.titleLarge,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      val metaBits = buildList {
        podcastTitle?.takeIf { it.isNotBlank() }?.let { add(normalizePodcastTitle(it)) }
        episodeDate?.takeIf { it.isNotBlank() }?.let { add(it) }
      }
      if (metaBits.isNotEmpty()) {
        Text(
          metaBits.joinToString(" • "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Timeline
      if (durationMs > 0) {
        val value = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        Slider(
          value = value,
          onValueChange = { v -> onSeekTo((v * durationMs.toFloat()).toLong()) },
          modifier = Modifier.fillMaxWidth()
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(fmtMs(positionMs), style = MaterialTheme.typography.labelSmall)
          Text("-${fmtMs((durationMs - positionMs).coerceAtLeast(0))}", style = MaterialTheme.typography.labelSmall)
          Text(fmtMs(durationMs), style = MaterialTheme.typography.labelSmall)
        }
      }

      Spacer(Modifier.padding(4.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onRewind15) {
          Icon(Icons.Filled.FastRewind, contentDescription = "Rewind 15")
        }
        IconButton(onClick = onPlayPause) {
          Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = "Play/Pause"
          )
        }
        IconButton(onClick = onForward30) {
          Icon(Icons.Filled.FastForward, contentDescription = "Forward 30")
        }
      }

      Spacer(Modifier.padding(4.dp))

      // Toggles
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Skip silence", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = skipSilenceEnabled, onCheckedChange = onToggleSkipSilence)
      }
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Boost volume", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = boostVolumeEnabled, onCheckedChange = onToggleBoostVolume)
      }

      // Trim controls (per-podcast)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Trim intro: ${trimIntroSec}s", style = MaterialTheme.typography.bodyMedium)
      }
      Slider(
        value = trimIntroSec.toFloat(),
        onValueChange = { onSetTrimIntroSec(it.toInt()) },
        valueRange = 0f..120f,
        steps = 11,
      )
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Trim outro: ${trimOutroSec}s", style = MaterialTheme.typography.bodyMedium)
      }
      Slider(
        value = trimOutroSec.toFloat(),
        onValueChange = { onSetTrimOutroSec(it.toInt()) },
        valueRange = 0f..120f,
        steps = 11,
      )

      if (chapters.isNotEmpty()) {
        Spacer(Modifier.padding(4.dp))
        Text("Chapters", style = MaterialTheme.typography.titleSmall)
        chapters.take(20).forEach { ch ->
          Row(
            modifier = Modifier.fillMaxWidth().clickable { onSeekToChapter(ch.startMs) }.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(ch.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtMs(ch.startMs), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
          }
        }
      }

      Text(
        text = "Tip: tap an episode again to restart it; queue coming next.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun fmtMs(ms: Long): String {
  val totalSec = (ms.coerceAtLeast(0L) / 1000L).toInt()
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
