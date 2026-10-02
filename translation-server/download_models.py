"""Downloads every model the server needs into MODELS_DIR (run once; Docker does it at build)."""
import io
import os
import shutil
import sys
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(__file__))
from app.config import settings  # noqa: E402

ROOT = settings.models_dir
PIPER = "https://huggingface.co/rhasspy/piper-voices/resolve/main"
OPENVOICE_ZIP = "https://myshell-public-repo-host.s3.amazonaws.com/openvoice/checkpoints_v2_0417.zip"


def fetch(url: str, dest: str) -> None:
    if os.path.exists(dest):
        return
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    print("↓", url)
    with urllib.request.urlopen(url) as r, open(dest + ".part", "wb") as f:
        shutil.copyfileobj(r, f)
    os.replace(dest + ".part", dest)


def piper_voices() -> None:
    for lang in settings.languages:
        name = settings.voices[lang]                       # e.g. ar_JO-kareem-medium
        locale, speaker, quality = name.split("-", 2)
        base = f"{PIPER}/{locale.split('_')[0]}/{locale}/{speaker}/{quality}/{name}.onnx"
        fetch(base, os.path.join(ROOT, "piper", f"{name}.onnx"))
        fetch(base + ".json", os.path.join(ROOT, "piper", f"{name}.onnx.json"))


def argos_packages() -> None:
    import argostranslate.package as pkg

    pkg.update_package_index()
    available = pkg.get_available_packages()
    langs = set(settings.languages)
    wanted = [p for p in available
              if (p.from_code in langs and p.to_code == "en") or (p.from_code == "en" and p.to_code in langs)
              or (p.from_code in langs and p.to_code in langs)]
    installed = {(p.from_code, p.to_code) for p in pkg.get_installed_packages()}
    for p in wanted:
        if (p.from_code, p.to_code) not in installed:
            print("↓ argos", p.from_code, "→", p.to_code)
            pkg.install_from_path(p.download())


def openvoice() -> None:
    target = os.path.join(ROOT, "openvoice", "converter")
    if os.path.exists(os.path.join(target, "checkpoint.pth")):
        return
    print("↓", OPENVOICE_ZIP)
    with urllib.request.urlopen(OPENVOICE_ZIP) as r:
        archive = zipfile.ZipFile(io.BytesIO(r.read()))
    os.makedirs(target, exist_ok=True)
    for member in ("checkpoints_v2/converter/config.json", "checkpoints_v2/converter/checkpoint.pth"):
        with archive.open(member) as src, open(os.path.join(target, os.path.basename(member)), "wb") as dst:
            shutil.copyfileobj(src, dst)


def whisper() -> None:
    from faster_whisper.utils import download_model

    download_model(settings.whisper_model, cache_dir=os.path.join(ROOT, "whisper"))


if __name__ == "__main__":
    piper_voices()
    argos_packages()
    whisper()
    if settings.voice_clone:
        openvoice()
    print("models ready in", ROOT)
