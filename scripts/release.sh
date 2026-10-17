#!/usr/bin/env bash
# Builds the phone, Wear and TV APKs, signs them with the key the installed builds use, writes SHA256SUMS.txt and
# publishes a GitHub release. Hush's in-app updater reads the latest release: the tag is the version, the
# `-phone.apk` asset is what it installs, and SHA256SUMS.txt is verified before installing.
#
#   scripts/release.sh 0.16.0 [notes.md]
#
# The version must match versionName in app/build.gradle.kts. Push the commit first: the tag is made from main.
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:?usage: scripts/release.sh <version> [notes.md]}
notes=${2:-}
grep -q "versionName = \"$version\"" app/build.gradle.kts || { echo "app/build.gradle.kts is not at $version" >&2; exit 1; }

export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}
BT=${BUILD_TOOLS:-$HOME/Android/Sdk/build-tools/36.0.0}
KS=${KEYSTORE:-$HOME/.android/debug.keystore}
KS_PASS=${KEYSTORE_PASS:-android}
KS_ALIAS=${KEY_ALIAS:-androiddebugkey}

./gradlew :app:assembleRelease :wear:assembleRelease :tv:assembleRelease -q

out=dist/release-$version
rm -rf "$out"; mkdir -p "$out"
for m in app:phone wear:wear tv:tv; do
    module=${m%%:*}; name=${m##*:}
    "$BT/zipalign" -f -p 4 "$module/build/outputs/apk/release/$module-release-unsigned.apk" "$out/aligned.apk"
    "$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias "$KS_ALIAS" \
        --out "$out/hush-$version-$name.apk" "$out/aligned.apk" 2>/dev/null
    rm "$out/aligned.apk" "$out/hush-$version-$name.apk.idsig" 2>/dev/null || true
done
(cd "$out" && sha256sum hush-*.apk > SHA256SUMS.txt)
cat "$out/SHA256SUMS.txt"

args=(--title "Hush $version" --target main)
if [[ -n $notes ]]; then args+=(--notes-file "$notes"); else args+=(--generate-notes); fi
gh release create "v$version" "${args[@]}" "$out"/hush-*.apk "$out/SHA256SUMS.txt"
