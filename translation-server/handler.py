"""RunPod Serverless entrypoint for 7PRO video dubbing.

Input:
  {"job_id": "<existing dubbing_jobs UUID>", "youtube_url": "optional"}

The app/backend creates and pays for the job first. This worker only accepts
jobs already marked QUEUED, then reuses the existing dubbing implementation.
"""
from __future__ import annotations

import logging
import os

import httpx
import runpod

from app.config import settings
from app.dub_routes import _run_job

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("7pro-runpod")


def _service_client() -> httpx.Client:
    key = settings.supabase_service_key
    return httpx.Client(
        base_url=settings.supabase_url,
        timeout=30,
        headers={"apikey": key, "Authorization": f"Bearer {key}"},
    )


def _get_job(job_id: str) -> dict:
    svc = _service_client()
    try:
        resp = svc.get("/rest/v1/dubbing_jobs", params={"id": f"eq.{job_id}", "select": "*"})
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            raise ValueError("JOB_NOT_FOUND")
        return rows[0]
    finally:
        svc.close()


def handler(job: dict) -> dict:
    inp = job.get("input") or {}
    job_id = inp.get("job_id")
    youtube_url = inp.get("youtube_url")

    if not isinstance(job_id, str) or not job_id.strip():
        raise ValueError("MISSING_JOB_ID")
    if not settings.supabase_url or not settings.supabase_service_key:
        raise RuntimeError("SUPABASE_SERVER_CONFIG_MISSING")

    row = _get_job(job_id)
    if row.get("status") != "QUEUED":
        raise ValueError(f"JOB_NOT_READY:{row.get('status')}")

    # The existing implementation performs the full pipeline and updates
    # dubbing_jobs to PROCESSING/DONE/FAILED through Supabase RPCs.
    _run_job(job_id, row, youtube_url or row.get("source_url"))

    # Return the final DB state so the caller can correlate the RunPod job.
    final = _get_job(job_id)
    return {
        "ok": final.get("status") == "DONE",
        "job_id": job_id,
        "status": final.get("status"),
        "output_path": final.get("output_path"),
        "error_code": final.get("error_code"),
    }


if __name__ == "__main__":
    # RunPod Serverless automatically scales workers to zero when Min Workers=0.
    runpod.serverless.start({"handler": handler})
