#!/usr/bin/env bash
# Builds NavOS-branded OsmAnd from the official F-Droid OsmAnd~ APK.
#
#   tools/osmand-rebrand/rebrand.sh [ABI] [OUT_DIR]
#
#   ABI      arm64-v8a (default, modern phones), armeabi-v7a, or x86_64 (emulator)
#   OUT_DIR  defaults to tools/osmand-rebrand/build
#
# Signing: with KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS (and optionally
# KEY_PASSWORD) set, signs with that key - use the same key every time, or
# phones refuse the update. Without them, makes a throwaway test key in OUT_DIR.
#
# Needs: Java 17+, Maven, Python 3 with Pillow, numpy, scikit-image, curl.
set -euo pipefail

ABI="${1:-arm64-v8a}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${2:-$HERE/build}"
APP_ID="${NAVOS_APP_ID:-com.excellentmindblow.navos.maps}"

# OsmAnd~ 5.4.10 on F-Droid, one APK per ABI. Hashes from F-Droid's signed index.
case "$ABI" in
  arm64-v8a)   CODE=541003 SHA=ca05d8f4ec364f84bde1946738e898abfb392234905eef1dc0ec597d4580b112 ;;
  x86_64|x86)  CODE=541002 SHA=d33ba6f7f73f82adf3cc08fa7114fb771e714dc8deab2d11b90e87050e17633a ;;
  armeabi-v7a) CODE=541001 SHA=c66480c9288a9cb87b73c618db75df83bb0bf4484ac57a5714e98f828a0d8950 ;;
  *) echo "unknown ABI: $ABI" >&2; exit 2 ;;
esac
APKTOOL_VERSION=3.0.3
APKSIG_VERSION=9.4.1

mkdir -p "$OUT/cache"
SRC="$OUT/cache/net.osmand.plus_$CODE.apk"
if ! echo "$SHA  $SRC" | sha256sum -c --status 2>/dev/null; then
  echo "== Downloading OsmAnd~ ($ABI) from F-Droid"
  curl -fL --retry 3 -o "$SRC" "https://f-droid.org/repo/net.osmand.plus_$CODE.apk"
  echo "$SHA  $SRC" | sha256sum -c
fi

LIB="$OUT/cache/lib-$APKTOOL_VERSION"
if [ ! -f "$LIB/apktool-cli-$APKTOOL_VERSION.jar" ]; then
  echo "== Fetching apktool $APKTOOL_VERSION and apksig $APKSIG_VERSION"
  POM="$OUT/cache/pom.xml"
  cat > "$POM" <<EOF
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
  <groupId>navos</groupId><artifactId>osmand-rebrand-tools</artifactId><version>1</version>
  <repositories>
    <repository><id>google</id><url>https://dl.google.com/android/maven2/</url></repository>
    <repository><id>jitpack</id><url>https://jitpack.io</url></repository>
  </repositories>
  <dependencies>
    <dependency><groupId>org.apktool</groupId><artifactId>apktool-cli</artifactId><version>$APKTOOL_VERSION</version></dependency>
    <dependency><groupId>com.android.tools.build</groupId><artifactId>apksig</artifactId><version>$APKSIG_VERSION</version></dependency>
  </dependencies>
</project>
EOF
  mvn -q -f "$POM" dependency:copy-dependencies -DoutputDirectory="$LIB"
fi
apktool() { java -Xmx6g -cp "$LIB/*" brut.apktool.Main "$@"; }

WORK="$OUT/work-$ABI"
echo "== Decoding"
rm -rf "$WORK"
apktool d -f -o "$WORK" "$SRC"

echo "== Rebranding"
python3 "$HERE/rebrand.py" "$WORK" "$APP_ID"

echo "== Restyling (NavOS Signal design)"
python3 "$HERE/restyle.py" "$WORK"

echo "== Building"
UNSIGNED="$OUT/unsigned-$ABI.apk"
apktool b -o "$UNSIGNED" "$WORK"

if [ -z "${KEYSTORE_PATH:-}" ]; then
  KEYSTORE_PATH="$OUT/test-key.p12" KEY_ALIAS=navos
  export KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-navos-test}"
  if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "== No signing key given: making a throwaway test key ($KEYSTORE_PATH)"
    keytool -genkeypair -keystore "$KEYSTORE_PATH" -storetype PKCS12 -storepass "$KEYSTORE_PASSWORD" \
      -alias "$KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=NavOS test"
  fi
fi

echo "== Signing"
FINAL="$OUT/NavOS-maps-5.4.10-$ABI.apk"
java -cp "$LIB/apksig-$APKSIG_VERSION.jar" "$HERE/Sign.java" "$UNSIGNED" "$FINAL" "$KEYSTORE_PATH" "$KEY_ALIAS"
rm -f "$UNSIGNED"
echo "== Done: $FINAL"
