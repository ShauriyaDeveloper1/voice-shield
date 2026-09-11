from pydantic_settings import BaseSettings


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

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
