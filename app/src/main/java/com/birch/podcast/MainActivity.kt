package com.birch.podcast

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.birch.podcast.data.db.AppDatabase
import com.birch.podcast.data.repo.PodcastRepository
import com.birch.podcast.downloads.DownloadPrefs
import com.birch.podcast.playback.PlaybackPrefs
import com.birch.podcast.playback.PlaybackService
import com.birch.podcast.theme.BirchTheme
import com.birch.podcast.theme.ThemePrefs
import com.birch.podcast.ui.AddFeedScreen
import com.birch.podcast.ui.AddFeedViewModel
import com.birch.podcast.ui.EpisodesScreen
import com.birch.podcast.ui.EpisodesViewModel
import com.birch.podcast.ui.LibraryScreen
import com.birch.podcast.ui.LibraryViewModel
import com.birch.podcast.ui.SettingsScreen
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.session.SessionCommand
import com.birch.podcast.ui.ChapterUi
import com.birch.podcast.ui.DownloadsScreen
import com.birch.podcast.ui.HistoryScreen
import com.birch.podcast.ui.NowPlayingScreen
import com.birch.podcast.ui.QueueScreen
import com.birch.podcast.ui.QueueViewModel
import com.birch.podcast.playback.PlaybackCommands
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { BirchApp() }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirchApp() {
  val context = LocalContext.current
  val view = LocalView.current
  val scope = rememberCoroutineScope()

  // Theme
  val themePrefs = remember { ThemePrefs(context) }
  val storedDark = themePrefs.darkTheme.collectAsState(initial = true).value
  val dark = storedDark ?: true


  // DB + repo
  val db = remember { AppDatabase.get(context) }
  val repo = remember { PodcastRepository(db) }

  // Download cleanup (best-effort, runs on app start)
  LaunchedEffect(Unit) {
    val days = DownloadPrefs.autoDeleteDays(context, default = 0)
    if (days > 0) {
      val olderThan = System.currentTimeMillis() - (days.toLong() * 24L * 60L * 60L * 1000L)
      runCatching {
        val candidates = repo.listPlayedDownloadsOlderThan(olderThan)
        candidates.forEach { ep ->
          val local = ep.localFileUri
          if (!local.isNullOrBlank()) {
            runCatching {
              val uri = Uri.parse(local)
              context.contentResolver.delete(uri, null, null)
            }
          }
          repo.clearEpisodeDownload(ep.guid)
          repo.setEpisodeDownloadStatus(ep.guid, null, null)
        }
      }
    }
  }

  // In-memory download UI state, keyed by episode guid.
  val dlUi = remember { androidx.compose.runtime.mutableStateMapOf<String, com.birch.podcast.ui.DownloadUi>() }
  val downloadingEpisodes by repo.observeDownloadingEpisodes().collectAsState(initial = emptyList())
  val downloadMgr = remember { context.getSystemService(DownloadManager::class.java) }
  val dlSamples = remember { mutableMapOf<String, Pair<Long, Long>>() } // guid -> (bytes, timeMs)
  val dlBps = remember { mutableMapOf<String, Double>() } // guid -> smoothed bytes/sec

  LaunchedEffect(Unit) {
    while (true) {
      val snapshot = downloadingEpisodes
      snapshot.forEach { ep ->
        val id = ep.downloadId
        if (id == 0L) return@forEach

        val ui = try {
          val q = DownloadManager.Query().setFilterById(id)
          downloadMgr?.query(q)?.use { cur ->
            if (!cur.moveToFirst()) return@use null

            val status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

            val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 }
            val soFar = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)).takeIf { it >= 0 }

            // Update speed estimate.
            if (soFar != null) {
              val now = System.currentTimeMillis()
              val prev = dlSamples[ep.guid]
              if (prev != null) {
                val dBytes = (soFar - prev.first).coerceAtLeast(0)
                val dMs = (now - prev.second).coerceAtLeast(1)
                val instBps = dBytes.toDouble() / (dMs.toDouble() / 1000.0)
                val old = dlBps[ep.guid]
                val next = if (old == null) instBps else (old * 0.7 + instBps * 0.3)
                dlBps[ep.guid] = next
              }
              dlSamples[ep.guid] = soFar to now
            }

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
              val local = cur.getString(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
              if (!local.isNullOrBlank()) {
                // Sometimes the broadcast receiver is delayed or missed on emulators.
                // Ensure we persist the local URI so the UI switches to "downloaded".
                scope.launch { repo.setEpisodeLocalFileUri(ep.guid, local) }
              }
            }

            val progress = if (status == DownloadManager.STATUS_SUCCESSFUL) 1f
              else if (total != null && soFar != null && total > 0) (soFar.toFloat() / total.toFloat()).coerceIn(0f, 1f)
              else null

            val bps = dlBps[ep.guid]
            val etaSec = if (total != null && soFar != null && bps != null && bps > 50) {
              ((total - soFar).coerceAtLeast(0).toDouble() / bps).toLong().coerceAtLeast(0)
            } else null

            com.birch.podcast.ui.DownloadUi(
              progress = progress,
              soFarBytes = soFar,
              totalBytes = total,
              etaSec = etaSec,
            )
          }
        } catch (_: Throwable) {
          null
        }

        if (ui != null) dlUi[ep.guid] = ui
      }

      // Drop stale keys.
      val activeGuids = snapshot.map { it.guid }.toSet()
      dlUi.keys.toList().forEach { g ->
        if (!activeGuids.contains(g)) {
          dlUi.remove(g)
          dlSamples.remove(g)
          dlBps.remove(g)
        }
      }

      delay(1_000)
    }
  }

  fun downloadEpisode(title: String, guid: String, audioUrl: String) {
    val dmSvc = context.getSystemService(DownloadManager::class.java) ?: return

    // Some GUIDs contain characters that are illegal in filenames (or are extremely long).
    // Sanitize to avoid DownloadManager/FS exceptions.
    val safeBase = guid
      .replace(Regex("[^A-Za-z0-9._-]"), "_")
      .take(80)
      .ifBlank { guid.hashCode().toString() }

    val showNotif = DownloadPrefs.showSystemNotification(context, default = true)
    val wifiOnly = DownloadPrefs.wifiOnly(context, default = false)
    val chargingOnly = DownloadPrefs.chargingOnly(context, default = false)

    val baseReq = DownloadManager.Request(Uri.parse(audioUrl))
      .setTitle(title)
      .setAllowedOverRoaming(true)
      .setAllowedOverMetered(!wifiOnly)
      .setAllowedNetworkTypes(
        if (wifiOnly) DownloadManager.Request.NETWORK_WIFI
        else (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
      )
      // Some Android versions/devices reject VISIBILITY_HIDDEN for 3P apps.
      .setNotificationVisibility(
        if (showNotif) DownloadManager.Request.VISIBILITY_VISIBLE
        else DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
      )
      .setDestinationInExternalFilesDir(context, "downloads", "$safeBase.mp3")
      .apply {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
          setRequiresCharging(chargingOnly)
        }
      }

    val id = try {
      dmSvc.enqueue(baseReq)
    } catch (_: SecurityException) {
      // Fallback: try again without forcing visibility.
      val fallbackReq = DownloadManager.Request(Uri.parse(audioUrl))
        .setTitle(title)
        .setAllowedOverRoaming(true)
        .setAllowedOverMetered(true)
        .setDestinationInExternalFilesDir(context, "downloads", "$safeBase.mp3")
        .apply {
          if (android.os.Build.VERSION.SDK_INT >= 24) {
            setRequiresCharging(chargingOnly)
          }
        }
      dmSvc.enqueue(fallbackReq)
    }

    scope.launch {
      repo.setEpisodeDownloadId(guid, id)
      repo.setEpisodeDownloadStatus(guid, "DOWNLOADING", null)
    }
  }

  // Listen for DownloadManager completion and attach local file uri to the episode.
  DisposableEffect(Unit) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0) return

        val dmSvc = context.getSystemService(DownloadManager::class.java) ?: return
        val q = DownloadManager.Query().setFilterById(id)
        val cur: Cursor = dmSvc.query(q) ?: return
        cur.use {
          if (!it.moveToFirst()) return
          val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

          // Find matching episode by downloadId.
          scope.launch {
            val ep = repo.getEpisodeByDownloadId(id) ?: return@launch

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
              val local = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)) ?: return@launch
              repo.setEpisodeLocalFileUri(ep.guid, local)
              repo.setEpisodeDownloadStatus(ep.guid, "SAVED", null)
              Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show()
            } else {
              val reason = runCatching {
                it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)).toString()
              }.getOrNull()
              // Failed / canceled
              repo.setEpisodeDownloadStatus(ep.guid, "FAILED", reason)
              repo.clearEpisodeDownload(ep.guid)
              Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
            }
          }
        }
      }
    }

    ContextCompat.registerReceiver(
      context,
      receiver,
      IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
      RECEIVER_NOT_EXPORTED,
    )
    onDispose {
      try {
        context.unregisterReceiver(receiver)
      } catch (_: IllegalArgumentException) {
        // If registration failed or was already unregistered, ignore.
      }
    }
  }

  // Playback controller + UI state
  var controller by remember { mutableStateOf<MediaController?>(null) }
  var nowTitle by remember { mutableStateOf<String?>(null) }
  var isPlaying by remember { mutableStateOf(false) }
  var positionMs by remember { mutableStateOf(0L) }
  var durationMs by remember { mutableStateOf(0L) }
  var playbackSpeed by remember { mutableStateOf(PlaybackPrefs.getSpeed(context, 1.0f)) }
  var playbackPitch by remember { mutableStateOf(PlaybackPrefs.getPitch(context, 1.0f)) }
  var skipSilenceEnabled by remember { mutableStateOf(PlaybackPrefs.getSkipSilence(context, false)) }
  var boostVolumeEnabled by remember { mutableStateOf(PlaybackPrefs.getBoostVolume(context, false)) }
  var nowPodcastId by remember { mutableStateOf<Long?>(null) }
  var nowPodcastTitle by remember { mutableStateOf<String?>(null) }
  var nowEpisodeDate by remember { mutableStateOf<String?>(null) }
  var nowArtworkUrl by remember { mutableStateOf<String?>(null) }
  var nowTrimIntroMs by remember { mutableStateOf(0L) }
  var nowTrimOutroMs by remember { mutableStateOf(0L) }
  var chapters by remember { mutableStateOf<List<ChapterUi>>(emptyList()) }

  // Sleep timer
  var sleepTimerLabel by remember { mutableStateOf<String?>(null) }
  var sleepTimerEndOfEpisode by remember { mutableStateOf(false) }
  var sleepTimerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

  fun clearSleepTimer() {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    sleepTimerEndOfEpisode = false
    sleepTimerLabel = null
  }

  fun setSleepTimerMinutes(minutes: Int) {
    clearSleepTimer()
    sleepTimerLabel = "${minutes}m"
    sleepTimerJob = scope.launch {
      delay(minutes * 60_000L)
      controller?.pause()
      clearSleepTimer()
    }
  }

  fun setSleepTimerEndOfEpisode() {
    clearSleepTimer()
    sleepTimerEndOfEpisode = true
    sleepTimerLabel = "End"
  }

  LaunchedEffect(Unit) {
    // Start the playback service so the Media3 SessionToken can bind.
    // Important: don't use startForegroundService() here unless we immediately call startForeground(),
    // otherwise some Android builds will ANR the app.
    val intent = android.content.Intent(context, PlaybackService::class.java)
    try {
      context.startService(intent)
    } catch (_: Throwable) {
      // best-effort
    }

    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    future.addListener(
      { controller = future.get() },
      ContextCompat.getMainExecutor(context)
    )
  }

  // Keep screen on while playing.
  DisposableEffect(isPlaying) {
    val prev = view.keepScreenOn
    view.keepScreenOn = isPlaying
    onDispose { view.keepScreenOn = prev }
  }

  // Keep UI in sync with controller.
  val listener = remember {
    object : Player.Listener {
      override fun onIsPlayingChanged(isPlayingNow: Boolean) {
        isPlaying = isPlayingNow
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        nowTitle = mediaItem?.mediaMetadata?.title?.toString()
        chapters = emptyList()

        val guid = mediaItem?.mediaId ?: return
        scope.launch {
          val ep = repo.getEpisodeByGuid(guid) ?: return@launch
          nowPodcastId = ep.podcastId
          nowTrimIntroMs = PlaybackPrefs.getTrimIntroMs(context, ep.podcastId)
          nowTrimOutroMs = PlaybackPrefs.getTrimOutroMs(context, ep.podcastId)
        }
      }

      override fun onMetadata(metadata: Metadata) {
        // Best-effort ID3 chapter support.
        val out = buildList {
          for (i in 0 until metadata.length()) {
            val e = metadata[i]
            if (e is ChapterFrame) {
              val t = e.chapterId?.takeIf { it.isNotBlank() } ?: "Chapter"
              add(ChapterUi(title = t, startMs = e.startTimeMs.toLong()))
            }
          }
        }.sortedBy { it.startMs }

        if (out.isNotEmpty()) chapters = out
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        val c = controller
        isPlaying = c?.isPlaying ?: false
        playbackSpeed = c?.playbackParameters?.speed ?: playbackSpeed
      }
    }
  }

  DisposableEffect(controller) {
    val c = controller
    c?.addListener(listener)
    onDispose { c?.removeListener(listener) }
  }

  // Position/duration ticker + persist playback
  LaunchedEffect(controller) {
    // Apply saved playback speed/pitch whenever we get a controller.
    controller?.setPlaybackParameters(PlaybackParameters(playbackSpeed, playbackPitch))

    var lastPersistAt = 0L
    while (true) {
      val c = controller
      positionMs = c?.currentPosition ?: 0L
      durationMs = c?.duration ?: 0L

      // Trim outro: stop early by jumping to end when within the last N ms.
      val outroMs = nowTrimOutroMs
      if (outroMs > 0 && durationMs > 0 && durationMs - positionMs in 1..outroMs) {
        c?.seekTo(durationMs)
      }

      val guid = c?.currentMediaItem?.mediaId
      val now = System.currentTimeMillis()
      if (guid != null && durationMs > 0 && now - lastPersistAt > 3_000) {
        lastPersistAt = now
        // best-effort persistence
        scope.launch {
          repo.updateEpisodePlayback(
            guid = guid,
            positionMs = positionMs,
            durationMs = durationMs,
            completed = false,
          )
        }
      }

      delay(500)
    }
  }

  fun playEpisode(title: String, guid: String, audioUrl: String, podcastId: Long? = null) {
    val c = controller ?: return

    nowTitle = title

    scope.launch {
      val saved = repo.getEpisodeByGuid(guid)
      val effectivePodcastId = podcastId ?: saved?.podcastId
      val uri = saved?.localFileUri ?: audioUrl

      // Per-podcast playback prefs (fallback to global)
      val speed = if (effectivePodcastId != null) PlaybackPrefs.getSpeedForPodcast(context, effectivePodcastId) else PlaybackPrefs.getSpeed(context)
      val pitch = if (effectivePodcastId != null) PlaybackPrefs.getPitchForPodcast(context, effectivePodcastId) else PlaybackPrefs.getPitch(context)
      val subtitle = "${speed}x • ${pitch}p"

      val podcast = effectivePodcastId?.let { id -> runCatching { db.podcasts().getById(id) }.getOrNull() }
      val artworkUri = podcast?.imageUrl?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }

      val item = MediaItem.Builder()
        .setMediaId(guid)
        .setUri(uri)
        .setMediaMetadata(
          MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artworkUri)
            .build()
        )
        .build()

      c.setMediaItem(item)

      // Apply playback params before play
      c.setPlaybackParameters(PlaybackParameters(speed, pitch))
      playbackSpeed = speed
      playbackPitch = pitch

      // Persist and cache episode context for UI
      nowPodcastId = effectivePodcastId
      nowPodcastTitle = podcast?.title
      nowArtworkUrl = podcast?.imageUrl
      nowEpisodeDate = saved?.publishedAtMs?.let { ms -> com.birch.podcast.ui.formatEpochMsShort(ms) }
      nowTrimIntroMs = if (effectivePodcastId != null) PlaybackPrefs.getTrimIntroMs(context, effectivePodcastId) else 0L
      nowTrimOutroMs = if (effectivePodcastId != null) PlaybackPrefs.getTrimOutroMs(context, effectivePodcastId) else 0L

      // Best-effort: toggle features
      val skip = PlaybackPrefs.getSkipSilence(context)
      val boost = PlaybackPrefs.getBoostVolume(context)
      skipSilenceEnabled = skip
      boostVolumeEnabled = boost
      c.sendCustomCommand(SessionCommand(PlaybackCommands.ACTION_SET_SKIP_SILENCE, Bundle.EMPTY), Bundle().apply {
        putBoolean(PlaybackCommands.EXTRA_ENABLED, skip)
      })
      c.sendCustomCommand(SessionCommand(PlaybackCommands.ACTION_SET_BOOST, Bundle.EMPTY), Bundle().apply {
        putBoolean(PlaybackCommands.EXTRA_ENABLED, boost)
      })

      // History
      if (effectivePodcastId != null) {
        repo.addToHistory(effectivePodcastId, guid, title)
      }

      // Resume (best-effort) w/ intro trim
      val resumeMs = (saved?.lastPositionMs ?: 0L)
      val wasCompleted = (saved?.completed ?: 0) == 1
      val introMs = nowTrimIntroMs
      val initialSeekMs = if (!wasCompleted) maxOf(resumeMs, introMs) else 0L
      if (!wasCompleted && initialSeekMs > 5_000) {
        c.seekTo(initialSeekMs)
      } else if (!wasCompleted && introMs > 0L) {
        c.seekTo(introMs)
      }

      c.prepare()
      c.play()
    }
  }

  // Auto-play next in queue when the current item ends.
  LaunchedEffect(controller) {
    val c = controller ?: return@LaunchedEffect

    val l = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
          // Mark completed
          val guid = c.currentMediaItem?.mediaId
          val dur = c.duration
          if (guid != null && dur > 0) {
            scope.launch {
              repo.updateEpisodePlayback(
                guid = guid,
                positionMs = dur,
                durationMs = dur,
                completed = true,
              )
            }
          }

          if (sleepTimerEndOfEpisode) {
            // Stop after this episode.
            clearSleepTimer()
            c.pause()
            return
          }

          // Pick the next queued item.
          scope.launch {
            val next = repo.dequeueNext() ?: return@launch
            playEpisode(next.title, next.episodeGuid, next.audioUrl)
          }
        }
      }
    }

    c.addListener(l)
    try {
      // Keep installed until controller changes
      while (true) delay(60_000)
    } finally {
      c.removeListener(l)
    }
  }

  BirchTheme(darkTheme = dark) {
    val nav = rememberNavController()

    Scaffold(
      bottomBar = {
        if (!nowTitle.isNullOrBlank()) {
          MiniPlayerBar(
            title = nowTitle,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            onOpen = { nav.navigate("nowplaying") },
            onSeekTo = { ms -> controller?.seekTo(ms) },
            onPlayPause = {
              val c = controller ?: return@MiniPlayerBar
              if (c.isPlaying) c.pause() else c.play()
            },
            onRewind15 = {
              val c = controller ?: return@MiniPlayerBar
              c.seekTo((c.currentPosition - 15_000).coerceAtLeast(0))
            },
            onForward30 = {
              val c = controller ?: return@MiniPlayerBar
              c.seekTo(c.currentPosition + 30_000)
            }
          )
        }
      }
    ) { padding ->
      NavHost(
        navController = nav,
        startDestination = "library",
        modifier = Modifier.padding(padding)
      ) {
        composable("library") {
          val vm = remember { LibraryViewModel(repo) }
          LibraryScreen(
            vm = vm,
            darkTheme = dark,
            onToggleTheme = {
              scope.launch { themePrefs.setDarkTheme(!dark) }
            },
            onAdd = { nav.navigate("add") },
            onOpenDownloads = { nav.navigate("downloads") },
            onOpenHistory = { nav.navigate("history") },
            onOpenSettings = { nav.navigate("settings") },
            onOpenPodcast = { id -> nav.navigate("podcast/$id") },
          )
        }

        composable("add") {
          val vm = remember { AddFeedViewModel(repo) }
          AddFeedScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onAdded = { id ->
              nav.popBackStack() // back to library
              nav.navigate("podcast/$id")
            }
          )
        }

        composable(
          route = "podcast/{podcastId}",
          arguments = listOf(navArgument("podcastId") { type = NavType.LongType })
        ) { entry ->
          val podcastId = entry.arguments?.getLong("podcastId") ?: return@composable

          // Title lookup (best-effort)
          var title by remember { mutableStateOf("Podcast") }
          LaunchedEffect(podcastId) {
            title = db.podcasts().getById(podcastId)?.title ?: "Podcast"
          }

          val vm = remember { EpisodesViewModel(repo, podcastId) }
          EpisodesScreen(
            title = title,
            vm = vm,
            podcastId = podcastId,
            onBack = { nav.popBackStack() },
            onPlay = { ep ->
              playEpisode(ep.title, ep.guid, ep.audioUrl, ep.podcastId)
            },
            onAddToQueue = { ep ->
              scope.launch { repo.enqueue(ep.title, ep.guid, ep.audioUrl) }
            },
            onPlayNext = { ep ->
              scope.launch { repo.enqueueNext(ep.title, ep.guid, ep.audioUrl) }
            },
            onPlayLast = { ep ->
              scope.launch { repo.enqueueLast(ep.title, ep.guid, ep.audioUrl) }
            },
            onDownloadAllUnplayed = { eps ->
              eps.forEach { ep -> downloadEpisode(ep.title, ep.guid, ep.audioUrl) }
            },
            onDownload = { ep ->
              downloadEpisode(ep.title, ep.guid, ep.audioUrl)
            },
            onRemoveDownload = { ep ->
              scope.launch {
                // Best-effort: remove the underlying download/file when possible.
                val dmSvc = context.getSystemService(DownloadManager::class.java)
                if (ep.downloadId != 0L) {
                  try {
                    dmSvc?.remove(ep.downloadId)
                  } catch (_: Throwable) {
                    // ignore
                  }
                }

                val local = ep.localFileUri
                if (!local.isNullOrBlank()) {
                  try {
                    val uri = Uri.parse(local)
                    if (uri.scheme == "file") {
                      val f = File(uri.path ?: "")
                      if (f.exists()) f.delete()
                    } else {
                      context.contentResolver.delete(uri, null, null)
                    }
                  } catch (_: Throwable) {
                    // ignore
                  }
                }

                repo.clearEpisodeDownload(ep.guid)
              }
            },
            onTogglePlayed = { ep ->
              scope.launch {
                val nowCompleted = ep.completed == 0
                repo.setEpisodeCompleted(ep.guid, nowCompleted)

                if (nowCompleted && DownloadPrefs.autoDeleteOnPlayed(context, default = false)) {
                  // Auto-delete the local file/download when marked played.
                  val dmSvc = context.getSystemService(DownloadManager::class.java)
                  if (ep.downloadId != 0L) runCatching { dmSvc?.remove(ep.downloadId) }

                  val local = ep.localFileUri
                  if (!local.isNullOrBlank()) {
                    runCatching {
                      val uri = Uri.parse(local)
                      context.contentResolver.delete(uri, null, null)
                    }
                  }

                  repo.clearEpisodeDownload(ep.guid)
                  repo.setEpisodeDownloadStatus(ep.guid, null, null)
                }
              }
            },
            downloadProgress = { ep -> dlUi[ep.guid] },
          )
        }

        composable("nowplaying") {
          fun refreshNotificationSubtitle() {
            val c = controller ?: return
            val cur = c.currentMediaItem ?: return
            val title = cur.mediaMetadata.title?.toString() ?: nowTitle ?: ""
            val subtitle = "${playbackSpeed}x • ${playbackPitch}p"
            val updated = cur.buildUpon()
              .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).build())
              .build()
            runCatching { c.replaceMediaItem(c.currentMediaItemIndex, updated) }
          }

          NowPlayingScreen(
            title = nowTitle,
            podcastTitle = nowPodcastTitle,
            episodeDate = nowEpisodeDate,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            sleepTimerLabel = sleepTimerLabel,
            skipSilenceEnabled = skipSilenceEnabled,
            boostVolumeEnabled = boostVolumeEnabled,
            artworkUrl = nowArtworkUrl,
            trimIntroSec = (nowTrimIntroMs / 1000L).toInt(),
            trimOutroSec = (nowTrimOutroMs / 1000L).toInt(),
            chapters = chapters,
            onBack = { nav.popBackStack() },
            onOpenQueue = { nav.navigate("queue") },
            onSetSleepTimerOff = { clearSleepTimer() },
            onSetSleepTimerMinutes = { m -> setSleepTimerMinutes(m) },
            onSetSleepTimerEndOfEpisode = { setSleepTimerEndOfEpisode() },
            onSeekTo = { ms -> controller?.seekTo(ms) },
            onPlayPause = {
              val c = controller ?: return@NowPlayingScreen
              if (c.isPlaying) c.pause() else c.play()
            },
            onRewind15 = {
              val c = controller ?: return@NowPlayingScreen
              c.seekTo((c.currentPosition - 15_000).coerceAtLeast(0))
            },
            onForward30 = {
              val c = controller ?: return@NowPlayingScreen
              c.seekTo(c.currentPosition + 30_000)
            },
            onSetSpeed = { speed ->
              val c = controller ?: return@NowPlayingScreen
              c.setPlaybackParameters(PlaybackParameters(speed, playbackPitch))
              playbackSpeed = speed
              val pid = nowPodcastId
              if (pid != null) PlaybackPrefs.setSpeedForPodcast(context, pid, speed) else PlaybackPrefs.setSpeed(context, speed)
              refreshNotificationSubtitle()
            },
            onSetPitch = { pitch ->
              val c = controller ?: return@NowPlayingScreen
              c.setPlaybackParameters(PlaybackParameters(playbackSpeed, pitch))
              playbackPitch = pitch
              val pid = nowPodcastId
              if (pid != null) PlaybackPrefs.setPitchForPodcast(context, pid, pitch) else PlaybackPrefs.setPitch(context, pitch)
              refreshNotificationSubtitle()
            },
            onToggleSkipSilence = { enabled ->
              val c = controller ?: return@NowPlayingScreen
              skipSilenceEnabled = enabled
              PlaybackPrefs.setSkipSilence(context, enabled)
              c.sendCustomCommand(SessionCommand(PlaybackCommands.ACTION_SET_SKIP_SILENCE, Bundle.EMPTY), Bundle().apply {
                putBoolean(PlaybackCommands.EXTRA_ENABLED, enabled)
              })
            },
            onToggleBoostVolume = { enabled ->
              val c = controller ?: return@NowPlayingScreen
              boostVolumeEnabled = enabled
              PlaybackPrefs.setBoostVolume(context, enabled)
              c.sendCustomCommand(SessionCommand(PlaybackCommands.ACTION_SET_BOOST, Bundle.EMPTY), Bundle().apply {
                putBoolean(PlaybackCommands.EXTRA_ENABLED, enabled)
              })
            },
            onSetTrimIntroSec = { sec ->
              val pid = nowPodcastId ?: return@NowPlayingScreen
              nowTrimIntroMs = sec.coerceAtLeast(0) * 1000L
              PlaybackPrefs.setTrimIntroMs(context, pid, nowTrimIntroMs)
            },
            onSetTrimOutroSec = { sec ->
              val pid = nowPodcastId ?: return@NowPlayingScreen
              nowTrimOutroMs = sec.coerceAtLeast(0) * 1000L
              PlaybackPrefs.setTrimOutroMs(context, pid, nowTrimOutroMs)
            },
            onSeekToChapter = { ms ->
              controller?.seekTo(ms)
            },
          )
        }

        composable("queue") {
          val vm = remember { QueueViewModel(repo) }
          QueueScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onPlayNow = { item ->
              playEpisode(item.title, item.episodeGuid, item.audioUrl)
            }
          )
        }

        composable("downloads") {
          DownloadsScreen(
            repo = repo,
            onBack = { nav.popBackStack() },
            onPlay = { ep -> playEpisode(ep.title, ep.guid, ep.audioUrl, ep.podcastId) },
            onRetry = { ep ->
              // Clear failure state then re-enqueue.
              scope.launch { repo.setEpisodeDownloadStatus(ep.guid, null, null) }
              downloadEpisode(ep.title, ep.guid, ep.audioUrl)
            },
          )
        }

        composable("settings") {
          SettingsScreen(onBack = { nav.popBackStack() })
        }

        composable("history") {
          HistoryScreen(
            repo = repo,
            onBack = { nav.popBackStack() },
            onPlay = { guid ->
              scope.launch {
                val ep = repo.getEpisodeByGuid(guid) ?: return@launch
                playEpisode(ep.title, ep.guid, ep.audioUrl, ep.podcastId)
                nav.navigate("nowplaying")
              }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun MiniPlayerBar(
  title: String?,
  isPlaying: Boolean,
  positionMs: Long,
  durationMs: Long,
  onOpen: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onPlayPause: () -> Unit,
  onRewind15: () -> Unit,
  onForward30: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable { onOpen() },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text(
        text = title ?: "Now playing",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium
      )

      if (durationMs > 0) {
        val value = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        Slider(
          value = value,
          onValueChange = { v -> onSeekTo((v * durationMs.toFloat()).toLong()) },
          modifier = Modifier.fillMaxWidth()
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onRewind15) {
          Icon(Icons.Filled.FastRewind, contentDescription = "Rewind 15")
        }
        IconButton(onClick = onPlayPause) {
          Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
        }
        IconButton(onClick = onForward30) {
          Icon(Icons.Filled.FastForward, contentDescription = "Forward 30")
        }
        Spacer(Modifier.weight(1f))
      }
    }
  }
}
