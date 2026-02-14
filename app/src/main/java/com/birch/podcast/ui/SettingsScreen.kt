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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
// (no keyboard options; keep dependencies minimal)
import androidx.compose.ui.unit.dp
import com.birch.podcast.downloads.DownloadPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  onBack: () -> Unit,
) {
  val context = LocalContext.current

  var wifiOnly by remember { mutableStateOf(DownloadPrefs.wifiOnly(context, default = false)) }
  var chargingOnly by remember { mutableStateOf(DownloadPrefs.chargingOnly(context, default = false)) }
  var showNotif by remember { mutableStateOf(DownloadPrefs.showSystemNotification(context, default = true)) }
  var autoDeleteOnPlayed by remember { mutableStateOf(DownloadPrefs.autoDeleteOnPlayed(context, default = false)) }
  var autoDeleteDaysText by remember { mutableStateOf(DownloadPrefs.autoDeleteDays(context, default = 0).toString()) }
  var showNetworkWarnings by remember { mutableStateOf(DownloadPrefs.showNetworkWarnings(context, default = true)) }

  fun setAutoDeleteDaysSafe(txt: String) {
    autoDeleteDaysText = txt
    val n = txt.toIntOrNull() ?: return
    DownloadPrefs.setAutoDeleteDays(context, n)
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
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
    }
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
