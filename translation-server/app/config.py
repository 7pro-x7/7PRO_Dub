"""Runtime configuration — everything comes from environment variables (see .env.example)."""
from __future__ import annotations

import json
import os
from dataclasses import dataclass, field


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


# Default Piper voices (all MIT-licensed rhasspy/piper-voices). Override with PIPER_VOICES.
DEFAULT_VOICES = {
    "ar": "ar_JO-kareem-medium",
    "en": "en_US-lessac-medium",
    "fr": "fr_FR-siwis-medium",
    "de": "de_DE-thorsten-medium",
    "es": "es_ES-davefx-medium",
    "tr": "tr_TR-dfki-medium",
    "it": "it_IT-riccardo-x_low",
    "ru": "ru_RU-denis-medium",
}


@dataclass
class Settings:
    supabase_url: str = field(default_factory=lambda: _env("SUPABASE_URL").rstrip("/"))
    supabase_anon_key: str = field(default_factory=lambda: _env("SUPABASE_ANON_KEY"))
    supabase_service_key: str = field(default_factory=lambda: _env("SUPABASE_SERVICE_ROLE_KEY"))
    device: str = field(default_factory=lambda: _env("DEVICE", "cuda"))
    whisper_model: str = field(default_factory=lambda: _env("WHISPER_MODEL", "large-v3-turbo"))
    whisper_compute_type: str = field(default_factory=lambda: _env("WHISPER_COMPUTE_TYPE", "float16"))
    models_dir: str = field(default_factory=lambda: _env("MODELS_DIR", "/models"))
    voice_clone: bool = field(default_factory=lambda: _env("VOICE_CLONE", "true").lower() == "true")
    voice_clone_token: str = field(default_factory=lambda: _env("VOICE_CLONE_TOKEN"))
    openvoice_ckpt: str = field(default_factory=lambda: _env("OPENVOICE_CKPT", "/models/openvoice/converter"))
    allowed_origins: list[str] = field(
        default_factory=lambda: [o for o in _env("ALLOWED_ORIGINS", "*").split(",") if o]
    )
    voices: dict[str, str] = field(default_factory=lambda: {**DEFAULT_VOICES, **json.loads(_env("PIPER_VOICES", "{}"))})
    languages: list[str] = field(default_factory=list)

    def __post_init__(self) -> None:
        wanted = [l.strip() for l in _env("LANGUAGES", "ar,en,fr,de,es,tr").split(",") if l.strip()]
        self.languages = [l for l in wanted if l in self.voices]


settings = Settings()
