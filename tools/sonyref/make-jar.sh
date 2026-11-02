#!/bin/sh
# Builds tools/sonyref/sound-connect.jar from Sound Connect's APK for the reference tests
# (core/src/test/.../SonyReference.kt). The jar is Sony's code: keep it local, never commit or ship it.
# Usage: tools/sonyref/make-jar.sh path/to/base.apk path/to/d2j-dex2jar.sh   (dex2jar 2.4, github.com/pxb1988/dex2jar)
set -e
here=$(dirname "$0")
sh "$2" -f -o "$here/sound-connect.jar" "$1"
