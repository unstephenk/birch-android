package com.birch.podcast.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
// (no keyboard options; keep dependencies minimal)
import androidx.compose.ui.unit.dp
import com.birch.podcast.downloads.DownloadPrefs
import com.birch.podcast.playback.PlaybackPrefs
import com.birch.podcast.theme.ThemeMode
import com.birch.podcast.theme.ThemePrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
) {
  val context = LocalContext.current

  // Theme
  val themePrefs = remember { ThemePrefs(context) }
  val themeMode = themePrefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM).value
  val scope = rememberCoroutineScope()

  var wifiOnly by remember { mutableStateOf(DownloadPrefs.wifiOnly(context, default = false)) }
  var chargingOnly by remember { mutableStateOf(DownloadPrefs.chargingOnly(context, default = false)) }
  var showNotif by remember { mutableStateOf(DownloadPrefs.showSystemNotification(context, default = true)) }
  var autoDeleteOnPlayed by remember { mutableStateOf(DownloadPrefs.autoDeleteOnPlayed(context, default = false)) }
  var autoDeleteDaysText by remember { mutableStateOf(DownloadPrefs.autoDeleteDays(context, default = 0).toString()) }
  var showNetworkWarnings by remember { mutableStateOf(DownloadPrefs.showNetworkWarnings(context, default = true)) }

  var skipBackSecText by remember { mutableStateOf(PlaybackPrefs.getSkipBackSec(context).toString()) }
  var skipForwardSecText by remember { mutableStateOf(PlaybackPrefs.getSkipForwardSec(context).toString()) }

  fun setSkipBackSafe(txt: String) {
    skipBackSecText = txt
    val n = txt.toIntOrNull() ?: return
    PlaybackPrefs.setSkipBackSec(context, n)
  }

  fun setSkipForwardSafe(txt: String) {
    skipForwardSecText = txt
    val n = txt.toIntOrNull() ?: return
    PlaybackPrefs.setSkipForwardSec(context, n)
  }

  fun setAutoDeleteDaysSafe(txt: String) {
    autoDeleteDaysText = txt
    val n = txt.toIntOrNull() ?: return
    DownloadPrefs.setAutoDeleteDays(context, n)
    com.birch.podcast.downloads.DownloadsCleanupScheduler.sync(context)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.padding(4.dp))
        }
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
        // Add a bit of extra space so the last items are reachable even if something overlays the bottom.
        .padding(bottom = 96.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text("Appearance", style = MaterialTheme.typography.titleMedium)

      ThemeModeRow(
        title = "Follow system",
        selected = themeMode == ThemeMode.SYSTEM,
        onClick = { scope.launch { themePrefs.setThemeMode(ThemeMode.SYSTEM) } },
      )
      ThemeModeRow(
        title = "Light",
        selected = themeMode == ThemeMode.LIGHT,
        onClick = { scope.launch { themePrefs.setThemeMode(ThemeMode.LIGHT) } },
      )
      ThemeModeRow(
        title = "Dark",
        selected = themeMode == ThemeMode.DARK,
        onClick = { scope.launch { themePrefs.setThemeMode(ThemeMode.DARK) } },
      )

      Text("Playback", style = MaterialTheme.typography.titleMedium)

      OutlinedTextField(
        value = skipBackSecText,
        onValueChange = { setSkipBackSafe(it.filter { c -> c.isDigit() }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Skip back (seconds)") },
        supportingText = { Text("Common: 10 / 15 / 30") },
        singleLine = true,
      )

      OutlinedTextField(
        value = skipForwardSecText,
        onValueChange = { setSkipForwardSafe(it.filter { c -> c.isDigit() }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Skip forward (seconds)") },
        supportingText = { Text("Common: 30 / 45 / 60") },
        singleLine = true,
      )

      Text("Downloads", style = MaterialTheme.typography.titleMedium)

      SettingToggleRow(
        title = "Wi‑Fi only",
        subtitle = "Only download episodes on Wi‑Fi",
        checked = wifiOnly,
        onCheckedChange = {
          wifiOnly = it
          DownloadPrefs.setWifiOnly(context, it)
        }
      )

      SettingToggleRow(
        title = "Only while charging",
        subtitle = "Require device charging to download",
        checked = chargingOnly,
        onCheckedChange = {
          chargingOnly = it
          DownloadPrefs.setChargingOnly(context, it)
        }
      )

      SettingToggleRow(
        title = "Show download notification",
        subtitle = "Use Android’s download notification",
        checked = showNotif,
        onCheckedChange = {
          showNotif = it
          DownloadPrefs.setShowSystemNotification(context, it)
        }
      )

      SettingToggleRow(
        title = "Show network warnings",
        subtitle = "Warn when Wi‑Fi-only / charging-only would block downloads",
        checked = showNetworkWarnings,
        onCheckedChange = {
          showNetworkWarnings = it
          DownloadPrefs.setShowNetworkWarnings(context, it)
        }
      )

      SettingToggleRow(
        title = "Auto-delete on played",
        subtitle = "Delete local file when you mark an episode played",
        checked = autoDeleteOnPlayed,
        onCheckedChange = {
          autoDeleteOnPlayed = it
          DownloadPrefs.setAutoDeleteOnPlayed(context, it)
        }
      )

      OutlinedTextField(
        value = autoDeleteDaysText,
        onValueChange = { setAutoDeleteDaysSafe(it.filter { c -> c.isDigit() }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Auto-delete played downloads after (days)") },
        supportingText = { Text("0 = disabled") },
        singleLine = true,
        // keyboardOptions omitted
      )

      Text("About", style = MaterialTheme.typography.titleMedium)

      val pkg = if (android.os.Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getPackageInfo(
          context.packageName,
          android.content.pm.PackageManager.PackageInfoFlags.of(0),
        )
      } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0)
      }
      val versionName = pkg.versionName ?: ""
      val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()

      Text(
        text = "Birch v${versionName} (${versionCode})",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ThemeModeRow(
  title: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable { onClick() },
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    RadioButton(selected = selected, onClick = onClick)
  }
}

@Composable
private fun SettingToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
