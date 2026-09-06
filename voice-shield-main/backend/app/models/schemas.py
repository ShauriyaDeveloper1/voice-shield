from pydantic import BaseModel, Field
from typing import Any


class UserCreate(BaseModel):
    name: str
    email: str
    phone: str | None = None
    role: str = "user"


class CallCreate(BaseModel):
    caller_id: str
    receiver_id: str
    status: str = "started"


class AnalysisRequest(BaseModel):
    call_id: str | None = None
    deepfake_score: float = 0.15
    speaker_similarity: float = 0.90
    prosody_score: float = 0.12
    context_score: float = 0.20
    timestamp: str | None = None
    features: dict[str, Any] = Field(default_factory=dict)


class AudioAnalysisRequest(BaseModel):
    file_name: str
    duration_seconds: float | None = None


class RegisterRequest(BaseModel):
    name: str
    email: str
    phone: str
    password: str


class LoginRequest(BaseModel):
    username: str  # can be Name or Email
    password: str


class RiskResult(BaseModel):
    score: float
    label: str


class UserUpdate(BaseModel):
    name: str
    email: str
    phone: str | None = None


class ContactCreate(BaseModel):
    name: str
    phone: str
    relation: str | None = "Family"
