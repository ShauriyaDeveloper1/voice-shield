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

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
