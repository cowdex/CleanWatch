#!/bin/sh
# Builds the APK and copies it to dist/CleanWatch.apk.
# Uses Homebrew's JDK 25 unless JAVA_HOME is already set.
set -eu
cd "$(dirname "$0")/.."
: "${JAVA_HOME:=/opt/homebrew/opt/openjdk@25}"
export JAVA_HOME

./gradlew --console=plain testDebugUnitTest assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/CleanWatch.apk
ls -lh dist/CleanWatch.apk
