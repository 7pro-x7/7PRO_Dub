# 7PRO Live Voice Translation server — GPU image (NVIDIA). CPU works too (DEVICE=cpu) but is slow.
FROM nvidia/cuda:12.3.2-cudnn9-runtime-ubuntu22.04

ENV DEBIAN_FRONTEND=noninteractive PIP_NO_CACHE_DIR=1 PYTHONUNBUFFERED=1 \
    MODELS_DIR=/models ARGOS_PACKAGES_DIR=/models/argos
RUN apt-get update && apt-get install -y --no-install-recommends \
        python3 python3-pip git ffmpeg libsndfile1 espeak-ng-data \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv
COPY requirements.txt .
RUN pip3 install torch==2.4.1 torchaudio==2.4.1 --index-url https://download.pytorch.org/whl/cu121 \
 && pip3 install -r requirements.txt \
 && pip3 install --no-deps "git+https://github.com/myshell-ai/OpenVoice.git@main"

COPY app ./app
COPY download_models.py .
# Models are baked into the image so a restart never waits on a download.
ARG LANGUAGES=ar,en,fr,de,es,tr
ENV LANGUAGES=${LANGUAGES}
RUN python3 download_models.py

EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080", "--ws-max-size", "4194304"]
