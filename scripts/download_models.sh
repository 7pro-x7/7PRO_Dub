#!/usr/bin/env bash
# ============================================================================
# تنزيل النماذج مسبقًا (اختياري) — التطبيق ينزّلها تلقائيًا عند أول تشغيل.
# بعد التنزيل، انقلها إلى الجهاز باستخدام push_models_to_device.sh
# ============================================================================
set -e

DIR="${1:-./models-preload}"
mkdir -p "$DIR"

WHISPER_KEY="${WHISPER_KEY:-base}"   # tiny | base | small | medium

echo ">> تنزيل Whisper $WHISPER_KEY (int8)..."
curl -L --progress-bar -o "$DIR/whisper-$WHISPER_KEY-encoder.onnx" \
  "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$WHISPER_KEY/resolve/main/$WHISPER_KEY-encoder.int8.onnx"

curl -L --progress-bar -o "$DIR/whisper-$WHISPER_KEY-decoder.onnx" \
  "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-$WHISPER_KEY/resolve/main/$WHISPER_KEY-decoder.int8.onnx"

echo ">> تنزيل حزمة الصوت العربي (Piper ar_JO-kareem)..."
curl -L --progress-bar -o "$DIR/piper-ar.tar.bz2" \
  "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ar_JO-kareem-medium.tar.bz2"

mkdir -p "$DIR/piper-ar"
tar xjf "$DIR/piper-ar.tar.bz2" -C "$DIR/piper-ar" --strip-components=1

echo ">> تنزيل الخط العربي (Noto Naskh Arabic)..."
curl -L --progress-bar -o "$DIR/NotoNaskhArabic.ttf" \
  "https://github.com/google/fonts/raw/main/ofl/notonaskharabic/NotoNaskhArabic%5Bwght%5D.ttf"

echo ""
echo "تم التنزيل إلى: $DIR"
echo "الآن شغّل: ./scripts/push_models_to_device.sh $DIR"
