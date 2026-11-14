#!/bin/sh
# Installs dist/CleanWatch.apk on a TV over Wi-Fi.
#   tools/install.sh 192.168.1.50
# The TV needs Developer options > USB debugging (and Network debugging, if listed) switched
# on, and you accept the "Allow USB debugging?" prompt on the TV the first time.
set -eu
cd "$(dirname "$0")/.."
[ $# -eq 1 ] || { echo "usage: tools/install.sh <tv ip address>" >&2; exit 2; }
adb="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
[ -f dist/CleanWatch.apk ] || { echo "dist/CleanWatch.apk not found; run tools/build.sh first" >&2; exit 1; }

"$adb" connect "$1:5555"
"$adb" -s "$1:5555" install -r dist/CleanWatch.apk
"$adb" -s "$1:5555" shell am start -n tv.cleanwatch/.BrowseActivity
