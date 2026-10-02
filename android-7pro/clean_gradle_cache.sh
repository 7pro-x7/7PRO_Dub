#!/usr/bin/env bash
# clean_gradle_cache.sh
# Fixes: "Could not read entry ... from cache executionHistory.bin"
# Run this from inside the project's android-7pro/ directory (where gradlew lives).

set -e

if [ ! -f "./gradlew" ]; then
  echo "ERROR: gradlew not found in this directory."
  echo "cd into the android-7pro folder (the one containing gradlew) and run this script again."
  exit 1
fi

echo "== Stopping any running Gradle daemons =="
./gradlew --stop || true

echo "== Removing local project .gradle cache =="
rm -rf .gradle

echo "== Removing build output directories =="
rm -rf app/build
rm -rf build

echo "== Removing global Gradle caches (safe, will be re-downloaded) =="
rm -rf ~/.gradle/caches

echo "== Done. Rebuilding release APK now... =="
./gradlew :app:assembleRelease --refresh-dependencies

echo "== Build finished. Check the output above for errors. =="
