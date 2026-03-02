# Birch (Android)

Birch is a lightweight podcast app built with **Kotlin**, **Jetpack Compose**, **Room**, and **Media3**.

This repo is intentionally small and iterative: the goal is a fast, usable player with a clean UX, not a giant framework.

## What it can do (current)

- **Library**
  - Add a podcast by RSS URL
  - Refresh feeds
  - Unsubscribe (with confirmation)
  - Theme toggle (dark/light)
  - Shows episode counts + unplayed counts

- **Episodes**
  - Search by title/summary
  - Filters: All / Unplayed / Downloaded / In progress
  - Sorts downloaded (“saved”) episodes to the top
  - Tap to play, long-press to add to queue
  - Mark played/unplayed (row check + played badge)
  - Bulk actions: mark all played/unplayed, clear played

- **Queue**
  - View “Up next” queue
  - Tap row to play
  - Move up/down
  - Swipe to remove
  - Clear queue (with confirmation)
  - Clear completed + remove duplicates (menu actions)

- **Playback**
  - Now Playing screen (timeline + seek)
  - Rewind 15s / forward 30s
  - Playback speed control
  - Playback pitch control
  - Sleep timer (15/30/60, or end of episode)
  - Keeps screen awake while playing
  - Supports BT **SEEK** back/forward and shows seek buttons in the Android media notification (device-dependent)

- **Downloads**
  - Download episodes via `DownloadManager`
  - In-row download state + progress ring (+ % when available)
  - Completion/failure toast
  - Cancel/remove download (best-effort deletes job + local file)

## Tech overview

- **UI**: Jetpack Compose (Material 3)
- **DB**: Room (`PodcastEntity`, `EpisodeEntity`, queue tables)
- **Networking**: OkHttp
- **Playback**: Media3 `MediaSessionService` + `MediaController`
- **Downloads**: Android `DownloadManager`

## Build & run

### Prereqs
- Android Studio (recommended)
- Android SDK installed

### Build debug APK
```bash
./gradlew assembleDebug
```
APK output:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install to a device/emulator (adb)
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap (next up)

Next 15 features/improvements (rough priority order):

1. **Proper “Play next” semantics**: insert right after the currently playing item (not just “top of queue”).
2. **Queue: now-playing highlighting** (visual “Now playing” row + disable pointless actions on it).
3. **Queue: remove duplicates** (one-tap cleanup).
4. **Queue: clear played/finished** (keep queue tidy).
5. **Mini-player polish**: show podcast + episode, progress, and a clear “tap to open” affordance.
6. **Persist/restore last-playing** on cold start (preload last episode without autoplay).
7. **Downloads screen upgrades**: bulk delete, show sizes, and better failure visibility.
8. **Episode list UX**: “In progress” filter, better resume hints, and consistent long-press actions.
9. **Restart-from-beginning action** in episode menus (explicit restart, not accidental).
10. **Per-podcast settings**: speed/pitch, trim intro/outro, auto-queue newest, skip silence, volume boost.
11. **OPML import/export** for subscriptions.
12. **Better refresh UX**: per-show refresh state, last refreshed timestamp, and error surfacing.
13. **Playback robustness**: audio focus edge cases, BT disconnect/reconnect, and controller rebind behavior.
14. **Search upgrades**: filter by show, downloaded, in-progress; sort by date/length.
15. **Release pipeline**: stable “nightly/latest” APK download link (in addition to Actions artifacts).

## Notes / known behaviors

- Some emulators/devices don’t report total size to `DownloadManager` early, so the app may show an indeterminate spinner until size is known.
- Notification seek buttons can vary by Android version/OEM and whether playback is active.

## License

TBD (private project)
