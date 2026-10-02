# RunPod detector fix

The Serverless endpoint now has a root-level `handler.py` and the Dockerfile
runs it directly. The wrapper imports the existing `translation-server/app`
implementation, so the dubbing pipeline is not replaced.

RunPod should now detect the Serverless entrypoint. Keep Min Workers at 0 for
scale-to-zero operation.
