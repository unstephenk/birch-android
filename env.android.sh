#!/usr/bin/env bash
# env.android.sh — local Android/Gradle environment for this repo.
#
# Usage:
#   source ./env.android.sh
#   ./gradlew :app:assembleDebug
#
# Notes:
# - Uses repo-local JDK (.jdk) and Android SDK (.sdk) so container restarts are painless.
# - Sets GRADLE_USER_HOME to .gradle-user to keep caches inside the repo.

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export ANDROID_SDK_ROOT="$REPO_DIR/.sdk"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export JAVA_HOME="$REPO_DIR/.jdk"

# Ensure java + adb + sdkmanager are available.
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH"

export GRADLE_USER_HOME="$REPO_DIR/.gradle-user"

# Quiet, non-blocking sanity checks.
if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Warning: JAVA_HOME invalid: $JAVA_HOME" >&2
fi
if [ ! -d "$ANDROID_SDK_ROOT" ]; then
  echo "Warning: ANDROID_SDK_ROOT missing: $ANDROID_SDK_ROOT" >&2
fi
if [ ! -x "$ANDROID_SDK_ROOT/platform-tools/adb" ]; then
  echo "Warning: adb not found under: $ANDROID_SDK_ROOT/platform-tools" >&2
fi
if [ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Warning: sdkmanager not found under: $ANDROID_SDK_ROOT/cmdline-tools/latest/bin" >&2
fi

echo "Android env set:" \
  && echo "  JAVA_HOME=$JAVA_HOME" \
  && echo "  ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" \
  && echo "  GRADLE_USER_HOME=$GRADLE_USER_HOME"
