"""
The speech pipeline, built only from open-source projects:

    speech ──► faster-whisper (STT, MIT)
           ──► Argos Translate (MT, MIT; OPUS-MT-derived models)
           ──► Piper (TTS, MIT)
           ──► OpenVoice V2 ToneColorConverter (voice cloning, MIT)
           ──► translated speech in the original speaker's voice

Every heavy call is synchronous and meant to run in a worker thread (see rooms.py). Model objects
are loaded once and shared; a lock serialises GPU use so two utterances never fight for memory.
"""
from __future__ import annotations

import io
import logging
import os
import tempfile
import threading
import wave
from dataclasses import dataclass

import numpy as np

from .config import settings

log = logging.getLogger("pipeline")

IN_RATE = 16_000  # what clients send


@dataclass
class Speech:
    pcm: bytes          # PCM16 LE mono
    sample_rate: int


def _pcm_to_float(pcm: bytes) -> np.ndarray:
    return np.frombuffer(pcm, dtype=np.int16).astype(np.float32) / 32768.0


def _float_to_pcm(audio: np.ndarray) -> bytes:
    audio = np.clip(audio, -1.0, 1.0)
    return (audio * 32767.0).astype(np.int16).tobytes()


def _write_wav(path: str, pcm: bytes, rate: int) -> None:
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm)


class Pipeline:
    def __init__(self) -> None:
        self._gpu = threading.Lock()
        self._whisper = None
        self._voices: dict[str, object] = {}
        self._converter = None
        self._voice_se: dict[str, object] = {}

    # ------------------------------------------------------------------ loading
    def load(self) -> None:
        from faster_whisper import WhisperModel

        device = "cuda" if settings.device.startswith("cuda") else "cpu"
        compute = settings.whisper_compute_type if device == "cuda" else "int8"
        log.info("Loading faster-whisper %s on %s (%s)", settings.whisper_model, device, compute)
        self._whisper = WhisperModel(settings.whisper_model, device=device, compute_type=compute,
                                     download_root=os.path.join(settings.models_dir, "whisper"))

        for lang in settings.languages:
            self._voice(lang)

        if settings.voice_clone:
            try:
                from openvoice.api import ToneColorConverter

                ckpt = settings.openvoice_ckpt
                conv = ToneColorConverter(os.path.join(ckpt, "config.json"),
                                          device=settings.device, enable_watermark=False)
                conv.load_ckpt(os.path.join(ckpt, "checkpoint.pth"))
                self._converter = conv
                log.info("OpenVoice tone-colour converter ready")
            except Exception:  # noqa: BLE001 - cloning is an enhancement, never a hard requirement
                log.exception("Voice cloning disabled: OpenVoice could not be loaded")
                self._converter = None

    @property
    def cloning(self) -> bool:
        return self._converter is not None

    def _voice(self, lang: str):
        voice = self._voices.get(lang)
        if voice is not None:
            return voice
        from piper.voice import PiperVoice

        name = settings.voices[lang]
        model = os.path.join(settings.models_dir, "piper", f"{name}.onnx")
        # Piper runs faster than real time on CPU, which keeps the GPU free for Whisper/OpenVoice.
        voice = PiperVoice.load(model, config_path=model + ".json", use_cuda=False)
        self._voices[lang] = voice
        log.info("Piper voice %s loaded for %s", name, lang)
        return voice

    # ------------------------------------------------------------------ stages
    def transcribe(self, pcm: bytes, lang: str) -> str:
        audio = _pcm_to_float(pcm)
        with self._gpu:
            segments, _ = self._whisper.transcribe(
                audio,
                language=lang,
                beam_size=1,
                vad_filter=False,
                condition_on_previous_text=False,
                without_timestamps=True,
            )
            text = " ".join(s.text.strip() for s in segments).strip()
        return text

    @staticmethod
    def translate(text: str, src: str, tgt: str) -> str:
        if not text or src == tgt:
            return text
        import argostranslate.translate as at

        return at.translate(text, src, tgt)

    def synthesize(self, text: str, lang: str) -> Speech:
        voice = self._voice(lang)
        rate = int(voice.config.sample_rate)
        if hasattr(voice, "synthesize_stream_raw"):              # piper-tts 1.2.x
            pcm = b"".join(voice.synthesize_stream_raw(text))
        else:                                                     # newer piper API
            pcm = b"".join(chunk.audio_int16_bytes for chunk in voice.synthesize(text))
        return Speech(pcm, rate)

    # ------------------------------------------------------------------ voice cloning
    def speaker_embedding(self, pcm: bytes):
        """Timbre embedding of a real speaker, from a few seconds of their own voice."""
        if not self._converter:
            return None
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "ref.wav")
            _write_wav(path, pcm, IN_RATE)
            with self._gpu:
                return self._converter.extract_se([path])

    def _base_voice_embedding(self, lang: str):
        se = self._voice_se.get(lang)
        if se is None:
            sample = {
                "ar": "مرحبا بكم جميعا في درس اليوم، سنبدأ الآن بمراجعة سريعة لما تعلمناه في المرة الماضية.",
                "en": "Welcome everyone to today's lesson, we will start with a quick review of what we learned last time.",
            }.get(lang, "Welcome everyone to today's lesson, we will start with a quick review of last time.")
            ref = self.synthesize(sample, lang)
            with tempfile.TemporaryDirectory() as tmp:
                path = os.path.join(tmp, "base.wav")
                _write_wav(path, ref.pcm, ref.sample_rate)
                with self._gpu:
                    se = self._converter.extract_se([path])
            self._voice_se[lang] = se
        return se

    def clone(self, speech: Speech, lang: str, target_se) -> Speech:
        """Re-voices synthetic speech with the original speaker's timbre (OpenVoice V2)."""
        if not self._converter or target_se is None:
            return speech
        try:
            src_se = self._base_voice_embedding(lang)
            rate = int(self._converter.hps.data.sampling_rate)
            with tempfile.TemporaryDirectory() as tmp:
                src = os.path.join(tmp, "src.wav")
                _write_wav(src, speech.pcm, speech.sample_rate)
                with self._gpu:
                    audio = self._converter.convert(audio_src_path=src, src_se=src_se,
                                                    tgt_se=target_se, output_path=None)
            return Speech(_float_to_pcm(np.asarray(audio, dtype=np.float32)), rate)
        except Exception:  # noqa: BLE001 - fall back to the plain voice rather than dropping speech
            log.exception("Voice cloning failed for one utterance; sending base voice")
            return speech


pipeline = Pipeline()
