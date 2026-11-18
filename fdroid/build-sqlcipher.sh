#!/usr/bin/env bash
# Builds the SQLCipher for Android AAR from source (libtomcrypt as crypto provider, no OpenSSL)
# and installs it as app/libs/sqlcipher-android.aar, which app/build.gradle.kts then uses
# instead of the prebuilt Maven Central artifact. Used by the F-Droid build.
#
# Needs: git, tclsh, make, a C compiler (SQLCipher amalgamation), JDK 17, and the Android NDK
# named below installed in $ANDROID_HOME/ndk.
set -euo pipefail

SQLCIPHER_ANDROID_TAG=v4.20.0
# Commit the tag must point to: a moved tag fails the build instead of changing the binary.
SQLCIPHER_ANDROID_COMMIT=94bdf2589942b11a53b7d9a005d5c1e66920d4e8

root=$(cd "$(dirname "$0")/.." && pwd)
# Fixed path: the compiler embeds source paths (__FILE__) in libsqlcipher.so, so a random
# directory would make every build differ and the F-Droid result could not be reproduced.
work=/tmp/sqlcipher-android-build
rm -rf "$work"
mkdir -p "$work"
trap 'rm -rf "$work"' EXIT

git clone --quiet --branch "$SQLCIPHER_ANDROID_TAG" --depth 1 --recurse-submodules --shallow-submodules \
    https://github.com/sqlcipher/sqlcipher-android.git "$work/sqlcipher-android"
cd "$work/sqlcipher-android"

if [ "$(git rev-parse HEAD)" != "$SQLCIPHER_ANDROID_COMMIT" ]; then
    echo "sqlcipher-android $SQLCIPHER_ANDROID_TAG is not $SQLCIPHER_ANDROID_COMMIT" >&2
    exit 1
fi

./gradlew --no-daemon assembleRelease ${GRADLE_ARGS:-}
mkdir -p "$root/app/libs"
cp sqlcipher/build/outputs/aar/sqlcipher-android-*-release.aar "$root/app/libs/sqlcipher-android.aar"
