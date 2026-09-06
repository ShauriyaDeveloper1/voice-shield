from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import analysis, auth, calls, users, contacts

app = FastAPI(
    title="VoiceShield API",
    version="0.1.0",
    description="Audio-based risk analysis service for call monitoring.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router, prefix="/api/auth", tags=["auth"])
app.include_router(calls.router, prefix="/api/calls", tags=["calls"])
app.include_router(analysis.router, prefix="/api/analysis", tags=["analysis"])
app.include_router(users.router, prefix="/api/users", tags=["users"])
app.include_router(contacts.router, prefix="/api/contacts", tags=["contacts"])


@app.get("/")
async def root():
    return {"message": "VoiceShield backend is running"}


@app.get("/health")
async def health_check():
    return {"status": "ok", "service": "voiceshield"}
