import os
from pathlib import Path
from pydantic_settings import BaseSettings

_BACKEND_DIR = Path(__file__).resolve().parent
_ROOT_DIR = _BACKEND_DIR.parent

_ENV_FILES = [
    str(_BACKEND_DIR / ".env"),
    str(_ROOT_DIR / ".env"),
    ".env",
]


class Settings(BaseSettings):
    app_name: str = "VoiceShield"
    environment: str = "development"
    secret_key: str = "changeme"
    supabase_url: str | None = None
    supabase_key: str | None = None
    supabase_anon_key: str | None = None
    google_client_id: str | None = None
    google_client_secret: str | None = None
    frontend_url: str = "https://voice-shield-ten.vercel.app"
    hf_space_url: str | None = "https://shauriya24-voiceshield.hf.space"
    msg91_auth_key: str | None = None
    msg91_template_id: str | None = None
    supabase_service_role_key: str | None = None
    msg91_sender_id: str | None = "VSHILD"

    @property
    def clean_supabase_url(self) -> str | None:
        if not self.supabase_url:
            return None
        url = self.supabase_url.strip()
        for suffix in ["/rest/v1/", "/rest/v1"]:
            if url.endswith(suffix):
                url = url[:-len(suffix)]
        return url

    class Config:
        env_file = _ENV_FILES
        extra = "ignore"


settings = Settings()

