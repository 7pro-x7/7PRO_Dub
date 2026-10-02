"""
Dubbing HTTP routes — mount on the existing FastAPI app in translation-server/app/main.py:

    from .dub_routes import router as dub_router
    app.include_router(dub_router)

Auth follows the exact pattern already used by /v1/clone-voice in main.py: a Supabase
user JWT in `Authorization: Bearer <token>`, verified against `${SUPABASE_URL}/auth/v1/user`.

Pricing/payment do NOT live here — they go through the project's existing `checkout` edge
function and `quote_checkout` / `create_order` / `confirm_order_payment` Postgres functions
(see supabase/migrations/20260927000000_dubbing_feature.sql), the same rails courses and AI
Tutor plans already use. This server only does two things a database function cannot:
look up a YouTube video's duration before it's paid for (/probe-youtube), and run the actual
dub once a job is paid and QUEUED (/start).

Processing runs in a background thread per job (FastAPI BackgroundTasks); for real
production load, swap that for a proper queue (e.g. a Redis/RQ worker) without changing
this HTTP contract.
"""
from __future__ import annotations

import json
import logging
import os
import shutil
import subprocess
import tempfile

import httpx
from fastapi import APIRouter, BackgroundTasks, HTTPException, Request

from .config import settings
from .dubbing import dub_video, fetch_source, probe_duration_seconds

log = logging.getLogger("dub_routes")
router = APIRouter(prefix="/v1/dub", tags=["dubbing"])


async def _auth_user(request: Request) -> tuple[str, str]:
    """Returns (user_id, jwt). Same check as /v1/clone-voice in main.py."""
    bearer = request.headers.get("Authorization", "")
    jwt = bearer.removeprefix("Bearer ").strip() if bearer.startswith("Bearer ") else ""
    if not jwt:
        raise HTTPException(status_code=401, detail="UNAUTHORIZED")
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(
            f"{settings.supabase_url}/auth/v1/user",
            headers={"apikey": settings.supabase_anon_key, "Authorization": f"Bearer {jwt}"},
        )
    if resp.status_code != 200:
        raise HTTPException(status_code=401, detail="UNAUTHORIZED")
    return resp.json()["id"], jwt


def _service_client() -> httpx.Client:
    """A client authenticated as service_role, for writes only the server should do
    (dubbing_complete_job, job status updates, storage upload of the result)."""
    key = settings.supabase_service_key
    return httpx.Client(
        base_url=settings.supabase_url, timeout=30,
        headers={"apikey": key, "Authorization": f"Bearer {key}"},
    )


@router.post("/probe-youtube")
async def probe_youtube(request: Request) -> dict:
    """Duration (seconds) of a YouTube video, without downloading it — used by the app to get
    a quote via the normal `checkout` edge function BEFORE any download or charge happens."""
    await _auth_user(request)
    body = await request.json()
    url = (body.get("youtube_url") or "").strip()
    if not url:
        raise HTTPException(status_code=400, detail="MISSING_URL")
    try:
        out = subprocess.run(
            ["yt-dlp", "--dump-json", "--no-download", url],
            check=True, capture_output=True, text=True, timeout=30,
        )
        info = json.loads(out.stdout.splitlines()[0])
    except Exception:  # noqa: BLE001 - any lookup failure is just "couldn't read that URL"
        raise HTTPException(status_code=422, detail="COULD_NOT_READ_VIDEO") from None
    duration = info.get("duration")
    if not duration:
        raise HTTPException(status_code=422, detail="NO_DURATION")
    return {"source_seconds": float(duration), "title": info.get("title", "")}


@router.post("/start")
async def start(request: Request, tasks: BackgroundTasks) -> dict:
    """
    Starts a dubbing job. Body:
      { "job_id": "<uuid of an existing dubbing_jobs row, status=AWAITING_PAYMENT or QUEUED>",
        "youtube_url": "..." }        # for source_type = YOUTUBE
    or the video was already uploaded to Supabase Storage at the path recorded on the job
    (source_type = UPLOAD) — the Android app uploads first, then calls /start.

    The job row (and the payment behind it) must already exist — created client-side via
    Postgres insert + the existing order/payment flow, mirroring how `ai_tutor_entitlements`
    is only created after `confirm_order_payment`. This endpoint refuses to run a job that
    isn't marked as paid/queued, so it can't be used to bypass billing.
    """
    user_id, jwt = await _auth_user(request)
    body = await request.json()
    job_id = body.get("job_id")
    youtube_url = body.get("youtube_url")
    if not job_id:
        raise HTTPException(status_code=400, detail="MISSING_JOB_ID")

    job = _fetch_job(job_id, jwt)
    if job["user_id"] != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")
    if job["status"] not in ("QUEUED",):
        raise HTTPException(status_code=409, detail=f"JOB_NOT_READY:{job['status']}")

    # The job row already carries its own source_url (set at dubbing_create_job time); the app
    # doesn't have to resend it here. Only trust an explicit override if the caller passes one.
    if not youtube_url and job.get("source_type") == "YOUTUBE":
        youtube_url = job.get("source_url")

    tasks.add_task(_run_job, job_id, job, youtube_url)
    return {"ok": True, "job_id": job_id, "status": "PROCESSING"}


@router.get("/status/{job_id}")
async def status(job_id: str, request: Request) -> dict:
    user_id, jwt = await _auth_user(request)
    job = _fetch_job(job_id, jwt)
    if job["user_id"] != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")
    return job


# ---------------------------------------------------------------------------- internals

def _fetch_job(job_id: str, jwt: str) -> dict:
    resp = httpx.get(
        f"{settings.supabase_url}/rest/v1/dubbing_jobs",
        params={"id": f"eq.{job_id}", "select": "*"},
        headers={"apikey": settings.supabase_anon_key, "Authorization": f"Bearer {jwt}"},
        timeout=15,
    )
    rows = resp.json() if resp.status_code == 200 else []
    if not rows:
        raise HTTPException(status_code=404, detail="JOB_NOT_FOUND")
    return rows[0]


def _run_job(job_id: str, job: dict, youtube_url: str | None) -> None:
    svc = _service_client()
    svc.patch(f"/rest/v1/dubbing_jobs?id=eq.{job_id}",
              json={"status": "PROCESSING", "started_at": "now()"})

    job_dir = tempfile.mkdtemp(prefix=f"dub_{job_id}_", dir=None)
    try:
        upload_path = None
        if job["source_type"] == "UPLOAD":
            upload_path = _download_from_storage(job["source_storage_path"], job_dir, svc)

        video_path = fetch_source(job_dir, youtube_url=youtube_url, upload_path=upload_path)
        duration = probe_duration_seconds(video_path)

        output_path = dub_video(job_dir=job_dir, video_path=video_path, direction=job["direction"])

        storage_path = f"dubbing/{job['user_id']}/{job_id}.mp4"
        _upload_to_storage(output_path, storage_path, svc)

        svc.post("/rest/v1/rpc/dubbing_complete_job",
                  json={"p_job": job_id, "p_output_path": storage_path, "p_actual_seconds": duration})
    except Exception:  # noqa: BLE001 - a failed job must still update status, never hang forever
        log.exception("Dubbing job %s failed", job_id)
        svc.post("/rest/v1/rpc/dubbing_fail_job",
                  json={"p_job": job_id, "p_error": "PROCESSING_ERROR"})
    finally:
        shutil.rmtree(job_dir, ignore_errors=True)
        svc.close()


def _download_from_storage(path: str, job_dir: str, svc: httpx.Client) -> str:
    out = os.path.join(job_dir, "upload.mp4")
    resp = svc.get(f"/storage/v1/object/{path}")
    resp.raise_for_status()
    with open(out, "wb") as f:
        f.write(resp.content)
    return out


def _upload_to_storage(local_path: str, storage_path: str, svc: httpx.Client) -> None:
    with open(local_path, "rb") as f:
        resp = svc.post(f"/storage/v1/object/{storage_path}",
                         content=f.read(), headers={"Content-Type": "video/mp4"})
    resp.raise_for_status()
