# 7PRO Dubbing - RunPod Serverless worker
FROM nvidia/cuda:12.3.2-cudnn9-runtime-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive PIP_NO_CACHE_DIR=1 PYTHONUNBUFFERED=1 \
    MODELS_DIR=/models ARGOS_PACKAGES_DIR=/models/argos \
    DEVICE=cuda WHISPER_MODEL=large-v3-turbo WHISPER_COMPUTE_TYPE=float16

RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 python3-pip git ffmpeg libsndfile1 espeak-ng-data \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY translation-server/requirements.txt ./requirements.txt
RUN pip3 install torch==2.4.1 torchaudio==2.4.1 --index-url https://download.pytorch.org/whl/cu121 \
 && pip3 install -r requirements.txt \
 && pip3 install --no-deps "git+https://github.com/myshell-ai/OpenVoice.git@main" \
 && pip3 install "runpod>=1.7.0,<2"

COPY translation-server/app ./app
COPY translation-server/download_models.py ./download_models.py
COPY handler.py ./handler.py

ARG LANGUAGES=ar,en,fr,de,es,tr
ENV LANGUAGES=${LANGUAGES}

# Bake models into the image so a cold start does not download them.
RUN python3 download_models.py

CMD ["python3", "handler.py"]
