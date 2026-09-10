from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel
from uuid import uuid4
import hashlib

from app.models.schemas import RegisterRequest, LoginRequest
from app.services.repository import _client, insert, find_by
from config import settings

router = APIRouter()


class VerificationRequest(BaseModel):
    email: str
    name: str | None = None
    phone: str | None = None
    redirect_to: str | None = None


class ConfirmProfileRequest(BaseModel):
    id: str
    email: str
    name: str | None = None
    phone: str | None = None


def hash_password(password: str) -> str:
    """Hash password using SHA-256 for local fallback mock storage."""
    return hashlib.sha256(password.encode("utf-8")).hexdigest()


@router.post("/send-verification")
async def send_verification(req: VerificationRequest):
    email = req.email.strip()
    if not email or "@" not in email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Valid email address is required"
        )
    
    # Check if already registered in profiles
    existing = find_by("profiles", "email", email)
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email is already registered. Please sign in instead."
        )
        
    client = _client()
    redirect_url = req.redirect_to or f"{settings.frontend_url}/verify-success"
    
    if client:
        try:
            # Use sign_in_with_otp to send a verification link to the email
            client.auth.sign_in_with_otp({
                "email": email,
                "options": {
                    "email_redirect_to": redirect_url,
                    "data": {
                        "name": req.name or "",
                        "phone": req.phone or ""
                    }
                }
            })
            return {
                "message": f"Verification email sent to {email}. Please check your inbox and click the verification link.",
                "email": email
            }
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Failed to send verification email: {str(e)}"
            )
    else:
        # Mock mode fallback
        return {
            "message": f"Verification email sent to {email} (mock mode).",
            "email": email
        }


@router.post("/confirm-profile")
async def confirm_profile(req: ConfirmProfileRequest):
    existing = find_by("profiles", "id", req.id)
    if existing:
        return {"message": "Profile already exists", "profile": existing[0]}
    
    existing_by_email = find_by("profiles", "email", req.email)
    if existing_by_email:
        return {"message": "Profile already exists", "profile": existing_by_email[0]}

    profile_data = {
        "id": req.id,
        "name": req.name or req.email.split("@")[0],
        "email": req.email,
        "phone": req.phone or "",
        "role": "user"
    }
    saved = insert("profiles", profile_data)
    return {"message": "Profile confirmed", "profile": saved}


@router.post("/register")
async def register(req: RegisterRequest):
    # Check if email is already registered in local profiles
    existing_email = find_by("profiles", "email", req.email)
    if existing_email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Email already registered"
        )
    
    # Check if phone is already registered in local profiles
    if req.phone:
        existing_phone = find_by("profiles", "phone", req.phone)
        if existing_phone:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Phone number already registered"
            )

    client = _client()
    user_id = str(uuid4())
    supabase_success = False

    if client:
        try:
            # Sign up in Supabase Auth (sends verification email)
            auth_response = client.auth.sign_up({
                "email": req.email,
                "password": req.password,
                "options": {
                    "data": {
                        "name": req.name,
                        "phone": req.phone
                    }
                }
            })
            if auth_response.user:
                user_id = auth_response.user.id
                supabase_success = True
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Supabase auth registration failed: {str(e)}"
            )

    # Save to the profiles table
    profile_data = {
        "id": user_id,
        "name": req.name,
        "email": req.email,
        "phone": req.phone,
        "role": "user"
    }
    
    # Save the password hash for local auth fallback if client is not configured
    if not supabase_success:
        profile_data["password_hash"] = hash_password(req.password)
    
    try:
        saved_profile = insert("profiles", profile_data)
        return {
            "message": "Registration successful. Please verify your email if required.",
            "supabase_auth": supabase_success,
            "profile": saved_profile
        }
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create user profile: {str(e)}"
        )


@router.post("/login")
async def login(req: LoginRequest):
    username = req.username
    password = req.password

    # Resolve email from name if the input is not an email
    email = username
    profile = None

    if "@" not in username:
        profiles = find_by("profiles", "name", username)
        if not profiles:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid username or password"
            )
        profile = profiles[0]
        email = profile["email"]
    else:
        profiles = find_by("profiles", "email", username)
        if not profiles:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid email or password"
            )
        profile = profiles[0]

    client = _client()
    if client:
        try:
            # Login using Supabase Auth with resolved email
            auth_response = client.auth.sign_in_with_password({
                "email": email,
                "password": password
            })
            return {
                "message": "Login successful",
                "access_token": auth_response.session.access_token,
                "user": {
                    "id": auth_response.user.id,
                    "name": profile.get("name"),
                    "email": email,
                    "phone": profile.get("phone")
                }
            }
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Supabase login failed: {str(e)}"
            )
    else:
        # Local fallback login
        # Check password hash if stored
        stored_hash = profile.get("password_hash")
        if stored_hash and stored_hash != hash_password(password):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Invalid username or password"
            )

        return {
            "message": "Login successful (local mock)",
            "access_token": f"mock-jwt-token-{profile['id']}",
            "user": {
                "id": profile["id"],
                "name": profile["name"],
                "email": profile["email"],
                "phone": profile.get("phone")
            }
        }


import httpx


class GoogleAuthRequest(BaseModel):
    access_token: str | None = None
    id_token: str | None = None
    provider_token: str | None = None


@router.post("/google")
async def google_auth(req: GoogleAuthRequest):
    """Handle Google sign-in. Supports:
    1. Direct Google ID tokens (from Android native Google Sign-in)
    2. Supabase OAuth access tokens (from Web frontend)
    """
    token = req.id_token or req.access_token
    if not token:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="access_token or id_token is required"
        )

    client = _client()
    user_id = None
    email = ""
    name = ""
    phone = ""
    avatar_url = ""

    # Strategy 1: Try verifying as Supabase session access token
    if client and req.access_token:
        try:
            user_response = client.auth.get_user(req.access_token)
            user = user_response.user
            if user:
                user_id = str(user.id)
                email = user.email or ""
                meta = user.user_metadata or {}
                name = meta.get("full_name") or meta.get("name") or email.split("@")[0]
                phone = meta.get("phone") or ""
                avatar_url = meta.get("avatar_url") or ""
        except Exception:
            # Not a Supabase token or failed, fallback to Google token verification
            pass

    # Strategy 2: If user not resolved, verify against Google's tokeninfo API
    if not user_id:
        try:
            async with httpx.AsyncClient(timeout=10.0) as http_client:
                resp = await http_client.get(f"https://oauth2.googleapis.com/tokeninfo?id_token={token}")
                if resp.status_code == 200:
                    google_info = resp.json()
                    user_id = f"google-{google_info.get('sub', '')}"
                    email = google_info.get("email", "")
                    name = google_info.get("name") or email.split("@")[0]
                    avatar_url = google_info.get("picture", "")
                else:
                    raise HTTPException(
                        status_code=status.HTTP_401_UNAUTHORIZED,
                        detail="Invalid Google ID token"
                    )
        except HTTPException:
            raise
        except Exception as e:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Failed to verify Google token: {str(e)}"
            )

    # Ensure profile exists in our profiles table
    existing = find_by("profiles", "id", user_id)
    if not existing:
        existing_by_email = find_by("profiles", "email", email)
        if not existing_by_email:
            profile_data = {
                "id": user_id,
                "name": name,
                "email": email,
                "phone": phone,
                "role": "user"
            }
            insert("profiles", profile_data)
        else:
            profile_data = existing_by_email[0]
            user_id = profile_data.get("id", user_id)
            name = profile_data.get("name", name)
            phone = profile_data.get("phone", phone)

    return {
        "message": "Google sign-in successful",
        "user": {
            "id": user_id,
            "name": name,
            "email": email,
            "phone": phone,
            "avatar_url": avatar_url
        }
    }

