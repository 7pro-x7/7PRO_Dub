#!/usr/bin/env bash
# ============================================================================
# دفع النماذج المنزّلة مسبقًا إلى التخزين الداخلي للتطبيق على جهاز متصل.
# يتطلب: adb + جهاز متصل بالوضع التنميري.
# ============================================================================
set -e

DIR="${1:-./models-preload}"
PKG="com.arena.arabicdub"
DEST="/sdcard/Android/data/$PKG/files/models"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb غير متوفر — ثبّت Android Platform Tools أولاً"
  exit 1
fi

adb push "$DIR/whisper-base-encoder.onnx"  "$DEST/" 2>/dev/null || true
for f in "$DIR"/whisper-*-encoder.onnx "$DIR"/whisper-*-decoder.onnx; do
  [ -e "$f" ] && adb push "$f" "$DEST/"
done
adb push "$DIR/piper-ar" "$DEST/"
adb push "$DIR/NotoNaskhArabic.ttf" "$DEST/"

echo ""
echo "تم الدفع. افتح التطبيق — سيجد النماذج جاهزة ولن ينزّل شيئًا."
