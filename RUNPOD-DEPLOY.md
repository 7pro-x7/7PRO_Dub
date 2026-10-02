# 7PRO — RunPod Serverless deployment

## GitHub
Push the contents of this repository to the `7PRO_Dub` repository/branch you use for the worker.

The important files are:
- `/Dockerfile`
- `/translation-server/handler.py`
- existing `/translation-server/app/*`

## RunPod endpoint
Choose **Serverless → Deploy from GitHub repository**.

Use:
- Repository: `7pro-x7/7PRO_Dub` (or your actual repo)
- Branch: `main`
- Dockerfile path: `/Dockerfile`
- Queue: **Default**
- Min Workers: **0**
- Max Workers: **1` initially

Start with a 24 GB GPU if the image fits. If the worker OOMs while loading Whisper/OpenVoice, move the endpoint to A40 48 GB.

## Environment variables
Set these as RunPod endpoint environment variables/secrets:

- `SUPABASE_URL`
- `SUPABASE_ANON_KEY`
- `SUPABASE_SERVICE_ROLE_KEY`  ← secret; never put this in Android
- `DEVICE=cuda`
- `WHISPER_MODEL=large-v3-turbo`
- `WHISPER_COMPUTE_TYPE=float16`
- `LANGUAGES=ar,en,fr,de,es,tr`
- `VOICE_CLONE=true`

Do not put Supabase service-role keys or RunPod API keys inside the Android app.

## Request payload
The 7PRO backend should call the RunPod endpoint with:

```json
{
  "input": {
    "job_id": "<paid-and-queued-dubbing-job-id>",
    "youtube_url": "<optional for YouTube jobs>"
  }
}
```

The worker refuses jobs that are not already `QUEUED`, so payment remains controlled by the existing 7PRO/Supabase flow.
