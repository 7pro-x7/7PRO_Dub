"""
Rooms, participants and the per-speaker utterance flow.

Wire protocol (one WebSocket per participant):

client -> server
  text   {"type":"hello","token":"<supabase jwt>","session_id":"<uuid>","lang":"ar","speak":false}
  text   {"type":"lang","lang":"en"}
  text   {"type":"speak","on":true}
  binary PCM16 LE, mono, 16 kHz — the speaker's microphone (only while speak=true)

server -> client
  text   {"type":"ready","languages":[...],"cloning":true,"user_id":"..."}
  text   {"type":"caption","speaker":"<uid>","name":"...","src":"ar","lang":"en","text":"...","original":"..."}
  text   {"type":"error","code":"MIC_LOCKED"|...}
  binary [uint32 BE header length][UTF-8 JSON header][PCM16 LE mono]
         header = {"kind":"orig"|"tr","speaker":"<uid>","sr":16000}

A speaker's voice reaches:
  * listeners who chose the SAME language -> the original audio, forwarded immediately ("orig");
  * listeners who chose ANOTHER language  -> the translated sentence, re-voiced with the
    speaker's own timbre ("tr"), one utterance at a time.
"""
from __future__ import annotations

import asyncio
import json
import logging
import struct
import time
from dataclasses import dataclass, field

import webrtcvad
from fastapi import WebSocket

from .pipeline import IN_RATE, pipeline

log = logging.getLogger("rooms")

FRAME_MS = 20
FRAME_BYTES = IN_RATE * FRAME_MS // 1000 * 2       # 640 bytes of PCM16
END_SILENCE_FRAMES = 30                            # 600 ms of silence ends a sentence
MAX_UTTERANCE_FRAMES = 350                         # 7 s: cut long monologues into pieces
MIN_UTTERANCE_FRAMES = 20                          # ignore < 400 ms blips
CLONE_FIRST_SECONDS = 6
CLONE_REFINE_SECONDS = 20


def frame_packet(header: dict, pcm: bytes) -> bytes:
    raw = json.dumps(header, separators=(",", ":")).encode()
    return struct.pack(">I", len(raw)) + raw + pcm


@dataclass
class Participant:
    ws: WebSocket
    user_id: str
    name: str
    lang: str
    room: "Room"
    speak: bool = False
    mic_locked: bool = False
    # segmentation
    _buf: bytearray = field(default_factory=bytearray)
    _utt: bytearray = field(default_factory=bytearray)
    _voiced: int = 0
    _silence: int = 0
    _in_speech: bool = False
    # voice cloning reference
    _ref: bytearray = field(default_factory=bytearray)
    _ref_level: int = 0
    embedding: object = None
    # outbound
    _send_lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    _queue: asyncio.Queue = field(default_factory=lambda: asyncio.Queue(maxsize=8))
    _worker: asyncio.Task | None = None
    _vad: webrtcvad.Vad = field(default_factory=lambda: webrtcvad.Vad(2))

    def start(self) -> None:
        self._worker = asyncio.create_task(self._drain())

    async def close(self) -> None:
        if self._worker:
            self._worker.cancel()

    # ------------------------------------------------------------------ outbound
    async def send_json(self, payload: dict) -> None:
        async with self._send_lock:
            await self.ws.send_text(json.dumps(payload, ensure_ascii=False))

    async def send_audio(self, header: dict, pcm: bytes) -> None:
        async with self._send_lock:
            await self.ws.send_bytes(frame_packet(header, pcm))

    # ------------------------------------------------------------------ inbound audio
    async def on_audio(self, chunk: bytes) -> None:
        if not self.speak or self.mic_locked:
            return
        # 1) same-language listeners hear the real voice right away
        await self.room.forward_original(self, chunk)
        # 2) sentence segmentation for everybody else
        self._buf.extend(chunk)
        while len(self._buf) >= FRAME_BYTES:
            frame = bytes(self._buf[:FRAME_BYTES])
            del self._buf[:FRAME_BYTES]
            self._on_frame(frame)

    def _on_frame(self, frame: bytes) -> None:
        try:
            voiced = self._vad.is_speech(frame, IN_RATE)
        except Exception:  # noqa: BLE001
            voiced = False
        if voiced:
            self._in_speech = True
            self._silence = 0
            self._voiced += 1
            self._ref.extend(frame)
        elif self._in_speech:
            self._silence += 1
        if self._in_speech:
            self._utt.extend(frame)
        frames = len(self._utt) // FRAME_BYTES
        if self._in_speech and (self._silence >= END_SILENCE_FRAMES or frames >= MAX_UTTERANCE_FRAMES):
            self._flush()

    def _flush(self) -> None:
        utt, voiced = bytes(self._utt), self._voiced
        self._utt.clear()
        self._voiced = self._silence = 0
        self._in_speech = False
        if voiced < MIN_UTTERANCE_FRAMES:
            return
        try:
            self._queue.put_nowait(utt)
        except asyncio.QueueFull:
            # Falling behind: drop the oldest pending sentence so live speech stays live.
            try:
                self._queue.get_nowait()
            except asyncio.QueueEmpty:
                pass
            self._queue.put_nowait(utt)

    def stop_speaking(self) -> None:
        if self._in_speech:
            self._flush()
        self._buf.clear()

    async def _drain(self) -> None:
        loop = asyncio.get_running_loop()
        while True:
            utt = await self._queue.get()
            try:
                await self._maybe_refresh_embedding(loop)
                await self.room.translate_utterance(self, utt, loop)
            except asyncio.CancelledError:
                raise
            except Exception:  # noqa: BLE001
                log.exception("utterance failed for %s", self.user_id)

    async def _maybe_refresh_embedding(self, loop) -> None:
        if not pipeline.cloning:
            return
        seconds = len(self._ref) / (IN_RATE * 2)
        level = 2 if seconds >= CLONE_REFINE_SECONDS else 1 if seconds >= CLONE_FIRST_SECONDS else 0
        if level > self._ref_level:
            ref = bytes(self._ref[-CLONE_REFINE_SECONDS * IN_RATE * 2:])
            self.embedding = await loop.run_in_executor(None, pipeline.speaker_embedding, ref)
            self._ref_level = level


class Room:
    def __init__(self, session_id: str) -> None:
        self.session_id = session_id
        self.members: dict[int, Participant] = {}

    def listeners(self, speaker: Participant):
        return [p for p in self.members.values() if p is not speaker]

    async def _safe(self, coro) -> None:
        try:
            await coro
        except Exception:  # noqa: BLE001 - a dead socket must not stall the room
            pass

    async def forward_original(self, speaker: Participant, chunk: bytes) -> None:
        header = {"kind": "orig", "speaker": speaker.user_id, "sr": IN_RATE}
        targets = [p for p in self.listeners(speaker) if p.lang == speaker.lang]
        if targets:
            await asyncio.gather(*(self._safe(p.send_audio(header, chunk)) for p in targets))

    async def translate_utterance(self, speaker: Participant, pcm: bytes, loop) -> None:
        by_lang: dict[str, list[Participant]] = {}
        for p in self.listeners(speaker):
            if p.lang != speaker.lang:
                by_lang.setdefault(p.lang, []).append(p)
        if not by_lang:
            return
        started = time.monotonic()
        text = await loop.run_in_executor(None, pipeline.transcribe, pcm, speaker.lang)
        if not text:
            return
        for lang, targets in by_lang.items():
            translated = await loop.run_in_executor(None, pipeline.translate, text, speaker.lang, lang)
            if not translated:
                continue
            caption = {
                "type": "caption", "speaker": speaker.user_id, "name": speaker.name,
                "src": speaker.lang, "lang": lang, "text": translated, "original": text,
            }
            await asyncio.gather(*(self._safe(p.send_json(caption)) for p in targets))
            speech = await loop.run_in_executor(None, pipeline.synthesize, translated, lang)
            speech = await loop.run_in_executor(None, pipeline.clone, speech, lang, speaker.embedding)
            header = {"kind": "tr", "speaker": speaker.user_id, "sr": speech.sample_rate}
            await asyncio.gather(*(self._safe(p.send_audio(header, speech.pcm)) for p in targets))
        log.info("utterance %s→%s in %.2fs", speaker.lang, list(by_lang), time.monotonic() - started)


class Rooms:
    def __init__(self) -> None:
        self._rooms: dict[str, Room] = {}

    def join(self, session_id: str, participant_factory) -> Participant:
        room = self._rooms.setdefault(session_id, Room(session_id))
        participant = participant_factory(room)
        room.members[id(participant)] = participant
        participant.start()
        return participant

    async def leave(self, participant: Participant) -> None:
        room = participant.room
        room.members.pop(id(participant), None)
        await participant.close()
        if not room.members:
            self._rooms.pop(room.session_id, None)


rooms = Rooms()
