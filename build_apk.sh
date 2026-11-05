#!/bin/bash
# 本地一键构建 Debug APK
set -e
cd "$(dirname "$0")"
echo "sdk.dir=${ANDROID_HOME:-$HOME/Android/Sdk}" > local.properties
chmod +x gradlew 2>/dev/null || true
./gradlew assembleDebug --no-daemon
APK=$(find app/build/outputs/apk/debug -name "*.apk" | head -1)
echo "APK: $APK"
cp -f "$APK" ../../LemonIPTV-debug.apk 2>/dev/null || cp -f "$APK" ./LemonIPTV-debug.apk
ls -lh "$APK"
