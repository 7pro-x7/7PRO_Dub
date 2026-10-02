"""
Batch video dubbing, built on the SAME open-source stack already used by
translation-server/app/pipeline.py (all MIT-licensed):

    yt-dlp   ──► downloads the YouTube source (Unlicense) — or a phone upload is used as-is
    ffmpeg   ──► extract audio, time-stretch each translated segment, remux (LGPL/GPL build)
    faster-whisper ──► transcribe WITH timestamps (word/segment level)
    Argos Translate ──► translate each segment, ar<->en
    Piper    ──► synthesize the translated segment
    OpenVoice V2 ──► re-voice it in the original speaker's timbre, using that
                      speaker's own segment audio as the reference clip

This is a starting skeleton to slot into translation-server as a new module
(e.g. translation-server/app/dubbing.py) reusing the already-loaded `pipeline`
singleton so no models are loaded twice. It is NOT wired to auth/Supabase/queueing
yet — see main.py's existing /v1/clone-voice handler for that pattern.
"""
from __future__ import annotations

import logging
import os
import subprocess
import tempfile
from dataclasses import dataclass

from .pipeline import pipeline, Speech, _write_wav  # reuse the loaded models

log = logging.getLogger("dubbing")

LANG_PAIRS = {"EN_TO_AR": ("en", "ar"), "AR_TO_EN": ("ar", "en")}


@dataclass
class Segment:
    start: float
    end: float
    text: str


def fetch_source(job_dir: str, *, youtube_url: str | None, upload_path: str | None) -> str:
    """Returns a path to a local video file, either downloaded or the uploaded one."""
    if upload_path:
        return upload_path
    if not youtube_url:
        raise ValueError("NO_SOURCE")
    out = os.path.join(job_dir, "source.mp4")
    # yt-dlp: keep it to a single mp4 for a predictable ffmpeg pipeline downstream.
    subprocess.run(
        ["yt-dlp", "-f", "mp4", "-o", out, youtube_url],
        check=True, capture_output=True,
    )
    return out


def probe_duration_seconds(video_path: str) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=noprint_wrappers=1:nokey=1", video_path],
        check=True, capture_output=True, text=True,
    )
    return float(out.stdout.strip())


def extract_audio(video_path: str, out_wav: str) -> None:
    subprocess.run(
        ["ffmpeg", "-y", "-i", video_path, "-ac", "1", "-ar", "16000", out_wav],
        check=True, capture_output=True,
    )


def transcribe_segments(wav_path: str, src_lang: str) -> list[Segment]:
    """Segment-level transcription with timestamps (faster-whisper supports this natively;
    the realtime pipeline.transcribe() strips timestamps, so this calls the model directly)."""
    segments, _ = pipeline._whisper.transcribe(  # noqa: SLF001 - internal reuse, see note below
        wav_path, language=src_lang, beam_size=5, vad_filter=True,
        condition_on_previous_text=True, without_timestamps=False,
    )
    return [Segment(s.start, s.end, s.text.strip()) for s in segments if s.text.strip()]
    # TODO: promote a public `pipeline.transcribe_timed()` method instead of reaching
    # into `_whisper` directly, once this module is merged into pipeline.py.


def _speaker_reference_clip(wav_path: str, seg: Segment, tmp_dir: str) -> str:
    ref = os.path.join(tmp_dir, f"ref_{seg.start:.2f}.wav")
    subprocess.run(
        ["ffmpeg", "-y", "-i", wav_path, "-ss", str(seg.start), "-to", str(seg.end),
         "-ac", "1", "-ar", "16000", ref],
        check=True, capture_output=True,
    )
    return ref


def _time_stretch_to_fit(speech: Speech, target_seconds: float, out_path: str) -> None:
    """Keeps the dub roughly lip-synced: stretch/compress the synthesized segment
    to the original segment's duration using ffmpeg's atempo filter."""
    with tempfile.TemporaryDirectory() as tmp:
        raw = os.path.join(tmp, "raw.wav")
        _write_wav(raw, speech.pcm, speech.sample_rate)
        produced = len(speech.pcm) / 2 / speech.sample_rate
        tempo = max(0.5, min(2.0, produced / max(target_seconds, 0.05)))
        subprocess.run(
            ["ffmpeg", "-y", "-i", raw, "-filter:a", f"atempo={tempo:.3f}", out_path],
            check=True, capture_output=True,
        )


def dub_video(*, job_dir: str, video_path: str, direction: str) -> str:
    """Runs the full dub and returns the path to the final muxed video."""
    src, tgt = LANG_PAIRS[direction]
    audio_wav = os.path.join(job_dir, "audio.wav")
    extract_audio(video_path, audio_wav)

    segments = transcribe_segments(audio_wav, src)
    clips_dir = os.path.join(job_dir, "clips")
    os.makedirs(clips_dir, exist_ok=True)

    dub_track = os.path.join(job_dir, "dub_track.wav")
    # Build a silence-padded track the same length as the source, then overlay each
    # stretched segment at its original start time.
    total = probe_duration_seconds(video_path)
    subprocess.run(
        ["ffmpeg", "-y", "-f", "lavfi", "-i", f"anullsrc=r=22050:cl=mono",
         "-t", str(total), dub_track],
        check=True, capture_output=True,
    )

    overlay_inputs: list[str] = []
    filter_parts: list[str] = []
    for i, seg in enumerate(segments):
        translated = pipeline.translate(seg.text, src, tgt)
        speech = pipeline.synthesize(translated, tgt)

        ref_clip = _speaker_reference_clip(audio_wav, seg, clips_dir)
        target_se = pipeline.speaker_embedding(open(ref_clip, "rb").read())
        speech = pipeline.clone(speech, tgt, target_se) if target_se is not None else speech

        stretched = os.path.join(clips_dir, f"seg_{i:04d}.wav")
        _time_stretch_to_fit(speech, seg.end - seg.start, stretched)

        overlay_inputs += ["-i", stretched]
        filter_parts.append(f"[{i+1}:a]adelay={int(seg.start*1000)}|{int(seg.start*1000)}[a{i}]")

    mix = "".join(f"[a{i}]" for i in range(len(segments))) + f"amix=inputs={len(segments)}:normalize=0[mixed]"
    filter_complex = ";".join(filter_parts + [mix]) if segments else "anull"

    mixed_track = os.path.join(job_dir, "mixed.wav")
    subprocess.run(
        ["ffmpeg", "-y", "-i", dub_track, *overlay_inputs,
         "-filter_complex", filter_complex, "-map", "[mixed]" if segments else "0:a",
         mixed_track],
        check=True, capture_output=True,
    )

    output_path = os.path.join(job_dir, "dubbed.mp4")
    subprocess.run(
        ["ffmpeg", "-y", "-i", video_path, "-i", mixed_track,
         "-map", "0:v", "-map", "1:a", "-c:v", "copy", "-shortest", output_path],
        check=True, capture_output=True,
    )
    return output_path
