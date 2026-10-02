#!/bin/bash
set -eux

echo "================================"
echo "CrashLab Android Build Script"
echo "================================"

# Step 1: Fix gradlew permissions
echo ""
echo "[1/6] Fixing gradlew permissions..."
ls -la ./gradlew
chmod 755 ./gradlew
chmod +x ./gradlew
ls -la ./gradlew
echo "✓ Gradlew permissions fixed"

# Step 2: Create local.properties
echo ""
echo "[2/6] Setting up local.properties..."
if [ -z "$ANDROID_SDK_ROOT" ]; then
  echo "ERROR: ANDROID_SDK_ROOT not set"
  exit 1
fi
echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
cat local.properties
echo "✓ local.properties created"

# Step 3: Run unit tests
echo ""
echo "[3/6] Running unit tests..."
chmod 755 ./gradlew
./gradlew test --stacktrace
echo "✓ Unit tests passed"

# Step 4: Build debug APK
echo ""
echo "[4/6] Building debug APK..."
chmod 755 ./gradlew
./gradlew assembleDebug --stacktrace
echo "✓ Debug APK built"

# Step 5: Build release APK
echo ""
echo "[5/6] Building release APK..."
chmod 755 ./gradlew
./gradlew assembleRelease --stacktrace
echo "✓ Release APK built"

# Step 6: Build App Bundle
echo ""
echo "[6/6] Building App Bundle..."
chmod 755 ./gradlew
./gradlew bundleRelease --stacktrace
echo "✓ App Bundle built"

echo ""
echo "================================"
echo "Build completed successfully!"
echo "================================"
echo ""
echo "Artifacts:"
find app/build/outputs -type f \( -name "*.apk" -o -name "*.aab" \) -exec ls -lh {} \;
