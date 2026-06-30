#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$ROOT/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
VERSION_NAME="${VERSION_NAME:-0.3.0}"
VERSION_CODE="${VERSION_CODE:-3}"
OUT="${OUT:-$REPO_ROOT/ChaiChaiQuestNative-$VERSION_NAME.apk}"

if [ -z "$SDK" ]; then
  echo "ANDROID_HOME or ANDROID_SDK_ROOT is required" >&2
  exit 1
fi

BUILD_TOOLS="${BUILD_TOOLS:-$SDK/build-tools/35.0.0}"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-35/android.jar}"

rm -rf "$ROOT/build"
mkdir -p "$ROOT/build/classes" "$ROOT/build/dex" "$ROOT/build/gen" "$ROOT/build/compiled"

"$BUILD_TOOLS/aapt2" compile --dir "$ROOT/res" -o "$ROOT/build/compiled/resources.zip"
"$BUILD_TOOLS/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  --java "$ROOT/build/gen" \
  --min-sdk-version 23 \
  --target-sdk-version 35 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  -o "$ROOT/build/base.apk" \
  "$ROOT/build/compiled/resources.zip"

javac --release 8 \
  -classpath "$ANDROID_JAR" \
  -d "$ROOT/build/classes" \
  $(find "$ROOT/src" "$ROOT/build/gen" -name '*.java')

"$BUILD_TOOLS/d8" \
  --lib "$ANDROID_JAR" \
  --min-api 23 \
  --output "$ROOT/build/dex" \
  $(find "$ROOT/build/classes" -name '*.class')

cp "$ROOT/build/base.apk" "$ROOT/build/unsigned.apk"
(cd "$ROOT/build/dex" && zip -q "$ROOT/build/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$ROOT/build/unsigned.apk" "$ROOT/build/aligned.apk"

if [ ! -f "$ROOT/debug.keystore" ]; then
  keytool -genkeypair \
    -keystore "$ROOT/debug.keystore" \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$ROOT/debug.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --ks-key-alias androiddebugkey \
  --out "$OUT" \
  "$ROOT/build/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose "$OUT"
echo "$OUT"
