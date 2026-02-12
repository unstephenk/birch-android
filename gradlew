#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="${JAVA_HOME:-$DIR/.jdk}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$DIR/.sdk}"
export PATH="$JAVA_HOME/bin:$DIR/.sdk/platform-tools:$PATH"
exec "$DIR/.gradle/gradle-8.7/bin/gradle" "$@"
