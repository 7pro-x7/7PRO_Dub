#!/usr/bin/env bash
# Clears the Gradle state that survives between builds and can crash AGP before
# any project code is read ("Failed to instrument class ... PluginCrashReporter").
#
# On Codemagic this already happens automatically — see the "Prepare Android
# project" step in codemagic.yaml. This script is for building on your own
# machine, where nothing clears it for you.
#
# Usage:   bash clean-gradle.sh          (run from the repository root)

set -uo pipefail

echo "Stopping any running Gradle daemons..."
if [ -x "./android-7pro/gradlew" ]; then
  (cd android-7pro && ./gradlew --stop) >/dev/null 2>&1 || true
elif [ -x "./gradlew" ]; then
  ./gradlew --stop >/dev/null 2>&1 || true
fi

echo "Removing project-local build state..."
rm -rf ./android-7pro/.gradle ./android-7pro/build ./android-7pro/app/build
# Explicitly remove the Gradle execution history that can cause
# :app:processReleaseResources to fail with executionHistory.bin errors.
rm -f ./android-7pro/.gradle/8.13/executionHistory/executionHistory.bin 2>/dev/null || true
rm -rf ./.gradle ./build

echo "Removing instrumented plugin + configuration caches..."
rm -rf "$HOME/.gradle/caches/"*/generated-gradle-jars 2>/dev/null || true
rm -rf "$HOME/.gradle/caches/"*/transforms* 2>/dev/null || true
rm -rf "$HOME/.gradle/caches/"*/kotlin-dsl 2>/dev/null || true
rm -rf "$HOME/.gradle/caches/jars-"* 2>/dev/null || true
rm -rf "$HOME/.gradle/configuration-cache" 2>/dev/null || true
rm -rf "$HOME/.gradle/caches/"*/executionHistory 2>/dev/null || true

# Downloaded dependencies under caches/modules-2 are intentionally KEPT: they are
# the slow part to rebuild and are not what causes this crash. If you want a
# completely cold build, uncomment the next line.
# rm -rf "$HOME/.gradle/caches/modules-2"

echo
echo "Done. Now build with:"
echo "    cd android-7pro && ./gradlew clean assembleRelease --no-build-cache --no-configuration-cache"
