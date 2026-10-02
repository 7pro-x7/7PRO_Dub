"""7PRO Live Voice Translation server (FastAPI + WebSocket)."""
from __future__ import annotations

import asyncio
import json
import logging
import re

import base64
import binascii
import hmac
import io
import wave
import httpx
import numpy as np
from fastapi import FastAPI, HTTPException, Request, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

from .config import settings
from .pipeline import pipeline
from .rooms import Participant, rooms
from .pipeline import IN_RATE
from .dub_routes import router as dub_router

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("server")

app = FastAPI(title="7PRO Live Voice Translation")
# GET was enough when this server only served WebSockets + /v1/clone-voice's own CORS-exempt
# path; the new /v1/dub/* routes are plain POST/GET JSON, so POST is added here too.
app.add_middleware(CORSMiddleware, allow_origins=settings.allowed_origins, allow_methods=["GET", "POST"], allow_headers=["*"])
app.include_router(dub_router)

UUID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")
REAUTH_SECONDS = 30


@app.on_event("startup")
async def startup() -> None:
    await asyncio.get_running_loop().run_in_executor(None, pipeline.load)


@app.get("/health")
async def health() -> dict:
    return {"ok": True, "languages": settings.languages, "cloning": pipeline.cloning}


@app.post("/v1/clone-voice")
async def clone_voice(request: Request) -> dict:
    """Speak text in a consented speaker sample's voice using Piper + OpenVoice V2 (MIT)."""
    if not pipeline.cloning:
        raise HTTPException(status_code=503, detail="VOICE_CLONING_UNAVAILABLE")
    if not settings.voice_clone_token:
        raise HTTPException(status_code=503, detail="VOICE_CLONE_NOT_CONFIGURED")
    supplied = request.headers.get("X-Voice-Clone-Token", "")
    if not hmac.compare_digest(supplied, settings.voice_clone_token):
        raise HTTPException(status_code=401, detail="UNAUTHORIZED")

    bearer = request.headers.get("Authorization", "")
    jwt = bearer.removeprefix("Bearer ").strip() if bearer.startswith("Bearer ") else ""
    if not jwt:
        raise HTTPException(status_code=401, detail="UNAUTHORIZED")
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            user = await client.get(
                f"{settings.supabase_url}/auth/v1/user",
                headers={"apikey": settings.supabase_anon_key, "Authorization": f"Bearer {jwt}"},
            )
        if user.status_code != 200:
            raise HTTPException(status_code=401, detail="UNAUTHORIZED")
    except HTTPException:
        raise
    except httpx.HTTPError:
        raise HTTPException(status_code=503, detail="AUTH_UNAVAILABLE") from None

    try:
        body = await request.json()
        text = body.get("text")
        character_id = body.get("character_id")
        lang = body.get("lang", "en")
        if not isinstance(text, str) or not text.strip() or len(text) > 800:
            raise HTTPException(status_code=400, detail="INVALID_TEXT")
        if not isinstance(character_id, str) or not UUID_RE.fullmatch(character_id):
            raise HTTPException(status_code=400, detail="INVALID_CHARACTER")
        if not isinstance(lang, str) or lang not in settings.languages:
            raise HTTPException(status_code=400, detail="UNSUPPORTED_LANGUAGE")
        async with httpx.AsyncClient(timeout=10) as client:
            character_response = await client.get(
                f"{settings.supabase_url}/rest/v1/ai_tutor_characters",
                params={"id": f"eq.{character_id}", "active": "eq.true", "voice_mode": "eq.clone", "select": "voice_sample_path"},
                headers={"apikey": settings.supabase_anon_key, "Authorization": f"Bearer {jwt}"},
            )
        if character_response.status_code != 200:
            raise HTTPException(status_code=503, detail="CHARACTER_LOOKUP_FAILED")
        characters = character_response.json()
        sample_path = characters[0].get("voice_sample_path") if characters else None
        if not isinstance(sample_path, str) or not sample_path:
            raise HTTPException(status_code=404, detail="VOICE_SAMPLE_NOT_FOUND")
        async with httpx.AsyncClient(timeout=20) as client:
            sample_response = await client.get(
                f"{settings.supabase_url}/storage/v1/object/authenticated/tutor-voice-samples/{sample_path}",
                headers={"apikey": settings.supabase_anon_key, "Authorization": f"Bearer {jwt}"},
            )
        if sample_response.status_code != 200:
            raise HTTPException(status_code=404, detail="VOICE_SAMPLE_NOT_FOUND")
        wav_bytes = sample_response.content
        with wave.open(io.BytesIO(wav_bytes), "rb") as source:
            if source.getnchannels() != 1 or source.getsampwidth() != 2 or source.getframerate() != IN_RATE:
                raise HTTPException(status_code=400, detail="INVALID_SAMPLE_FORMAT")
            duration = source.getnframes() / source.getframerate()
            if duration < 3 or duration > 12:
                raise HTTPException(status_code=400, detail="INVALID_SAMPLE_DURATION")
            sample = source.readframes(source.getnframes())
    except HTTPException:
        raise
    except (ValueError, binascii.Error, wave.Error):
        raise HTTPException(status_code=400, detail="INVALID_SAMPLE") from None

    async def render() -> dict:
        def work():
            embedding = pipeline.speaker_embedding(sample)
            speech = pipeline.synthesize(text.strip(), lang)
            cloned = pipeline.clone(speech, lang, embedding)
            return base64.b64encode(cloned.pcm).decode("ascii"), cloned.sample_rate
        pcm_b64, sample_rate = await asyncio.get_running_loop().run_in_executor(None, work)
        return {"ok": True, "format": "pcm_s16le", "sample_rate": sample_rate, "audio_base64": pcm_b64}

    return await render()


async def authorize(token: str, session_id: str) -> dict:
    """Asks Supabase, as the caller, whether they may use translation in this session."""
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.post(
            f"{settings.supabase_url}/rest/v1/rpc/classroom_translation_authorize",
            headers={"apikey": settings.supabase_anon_key, "Authorization": f"Bearer {token}",
                     "Content-Type": "application/json"},
            json={"p_session_id": session_id},
        )
    if r.status_code != 200:
        return {"allowed": False, "reason": "UNAUTHORIZED"}
    return r.json() or {"allowed": False}


def _lang(value) -> str | None:
    return value if isinstance(value, str) and value in settings.languages else None


@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket) -> None:
    await ws.accept()
    participant: Participant | None = None
    reauth: asyncio.Task | None = None
    try:
        hello = json.loads(await asyncio.wait_for(ws.receive_text(), timeout=15))
        token, session_id = str(hello.get("token", "")), str(hello.get("session_id", ""))
        lang = _lang(hello.get("lang")) or settings.languages[0]
        if hello.get("type") != "hello" or not token or not UUID_RE.match(session_id):
            await ws.send_text(json.dumps({"type": "error", "code": "BAD_HELLO"}))
            return await ws.close(code=4400)
        auth = await authorize(token, session_id)
        if not auth.get("allowed"):
            await ws.send_text(json.dumps({"type": "error", "code": auth.get("reason", "NOT_AUTHORIZED")}))
            return await ws.close(code=4403)

        participant = rooms.join(session_id, lambda room: Participant(
            ws=ws, user_id=str(auth["user_id"]), name=str(auth.get("display_name") or ""),
            lang=lang, room=room, speak=bool(hello.get("speak")), mic_locked=bool(auth.get("mic_locked")),
        ))
        await participant.send_json({"type": "ready", "languages": settings.languages,
                                     "cloning": pipeline.cloning, "user_id": participant.user_id})

        async def recheck() -> None:
            while True:
                await asyncio.sleep(REAUTH_SECONDS)
                again = await authorize(token, session_id)
                if not again.get("allowed"):
                    await participant.send_json({"type": "error", "code": again.get("reason", "NOT_AUTHORIZED")})
                    await ws.close(code=4403)
                    return
                locked = bool(again.get("mic_locked"))
                if locked and not participant.mic_locked:
                    participant.stop_speaking()
                    await participant.send_json({"type": "error", "code": "MIC_LOCKED"})
                participant.mic_locked = locked

        reauth = asyncio.create_task(recheck())

        while True:
            message = await ws.receive()
            if message["type"] == "websocket.disconnect":
                break
            if message.get("bytes") is not None:
                await participant.on_audio(message["bytes"])
                continue
            data = json.loads(message.get("text") or "{}")
            kind = data.get("type")
            if kind == "lang" and _lang(data.get("lang")):
                participant.lang = data["lang"]
            elif kind == "speak":
                participant.speak = bool(data.get("on"))
                if not participant.speak:
                    participant.stop_speaking()
                elif participant.mic_locked:
                    await participant.send_json({"type": "error", "code": "MIC_LOCKED"})
    except (WebSocketDisconnect, asyncio.TimeoutError, json.JSONDecodeError):
        pass
    except Exception:  # noqa: BLE001
        log.exception("socket failed")
    finally:
        if reauth:
            reauth.cancel()
        if participant:
            await rooms.leave(participant)
