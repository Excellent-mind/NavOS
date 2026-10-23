#!/bin/bash
# Installs the Android SDK for Claude Code cloud sessions, so the debug APK,
# unit tests and Android lint can run inside the session instead of only
# on GitHub Actions. Idempotent: anything already installed is skipped, and
# the container is cached after the first run.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"

# Must match compileSdk in app/build.gradle.kts, and the build tools AGP
# 8.7 defaults to.
SDK_PACKAGES=("platform-tools" "platforms;android-34" "build-tools;34.0.0")

if [ ! -x "$SDKMANAGER" ]; then
  echo "Installing Android command-line tools into $SDK_DIR"
  mkdir -p "$SDK_DIR/cmdline-tools"
  tmp="$(mktemp -d)"
  curl -sSfL -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$tmp"
fi

yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true
"$SDKMANAGER" --sdk_root="$SDK_DIR" "${SDK_PACKAGES[@]}" >/dev/null

# Maven Central rate-limits (HTTP 429) the shared egress these sessions use.
# Session-only Gradle init script: try Google's mirror of Maven Central
# first. The repo's own build files are untouched, so CI is unaffected.
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/maven-central-mirror.gradle" <<'GRADLE'
def centralMirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
beforeSettings { settings ->
    settings.pluginManagement.repositories { maven { url centralMirror } }
    settings.dependencyResolutionManagement.repositories { maven { url centralMirror } }
}
GRADLE

# Gradle finds the SDK through local.properties (git-ignored).
echo "sdk.dir=$SDK_DIR" > "$PROJECT_DIR/local.properties"

if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export ANDROID_HOME=\"$SDK_DIR\""
    echo "export ANDROID_SDK_ROOT=\"$SDK_DIR\""
    echo "export PATH=\"\$PATH:$SDK_DIR/platform-tools:$SDK_DIR/cmdline-tools/latest/bin\""
  } >> "$CLAUDE_ENV_FILE"
fi

# Download Gradle and every build/test dependency now, so the first build
# in the session is fast and works even if a mirror is slow later.
cd "$PROJECT_DIR"
chmod +x ./gradlew
# Maven Central sometimes answers 429 (rate limit) under load: retry with
# a back-off instead of failing the session start.
for attempt in 1 2 3 4; do
  if ./gradlew --no-daemon --quiet :app:compileDebugUnitTestKotlin; then
    exit 0
  fi
  echo "Gradle warm-up failed (attempt $attempt), retrying in $((attempt * 15))s"
  sleep $((attempt * 15))
done
exit 1
