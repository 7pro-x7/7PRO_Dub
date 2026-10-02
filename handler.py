"""RunPod Serverless entrypoint for 7PRO dubbing.

This root-level wrapper is intentional: RunPod's repository detector looks for
an entrypoint in the repository root. The actual dubbing implementation stays
under translation-server/app.
"""
from __future__ import annotations

import logging
import os
import sys

# Make the existing translation-server package importable without changing its
# internal module layout.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "translation-server"))

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
    with _service_client() as svc:
        resp = svc.get("/rest/v1/dubbing_jobs", params={"id": f"eq.{job_id}", "select": "*"})
        resp.raise_for_status()
        rows = resp.json()
        if not rows:
            raise ValueError("JOB_NOT_FOUND")
        return rows[0]


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

    _run_job(job_id, row, youtube_url or row.get("source_url"))
    final = _get_job(job_id)
    return {
        "ok": final.get("status") == "DONE",
        "job_id": job_id,
        "status": final.get("status"),
        "output_path": final.get("output_path"),
        "error_code": final.get("error_code"),
    }


if __name__ == "__main__":
    runpod.serverless.start({"handler": handler})
