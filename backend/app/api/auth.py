from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel
from uuid import uuid4
import hashlib
import httpx

from app.models.schemas import (
    RegisterRequest, 
    LoginRequest, 
    SendOtpRequest, 
    VerifyOtpSignupRequest, 
    VerifyPhoneRequest, 
    PhoneLoginRequest,
    VerifyOtpRequest
)
from app.services.repository import _client, insert, find_by, update
from app.services.otp_service import send_otp, verify_otp, normalize_phone
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
    client = _client()
    if client and req.phone:
        try:
            rpc_resp = client.rpc("register_phone_user", {
                "phone_input": normalize_phone(req.phone),
                "name_input": req.name or ""
            }).execute()
            if rpc_resp.data:
                return {"message": "Profile confirmed", "profile": rpc_resp.data}
        except Exception as rpc_err:
            print(f"[Auth Error] confirm_profile RPC notice: {rpc_err}")

    # Check if ID exists and is valid UUID
    if req.id and not req.id.startswith("user_"):
        existing = find_by("profiles", "id", req.id)
        if existing:
            if req.name and req.name != existing[0].get("name"):
                try:
                    update("profiles", req.id, {"name": req.name})
                    existing[0]["name"] = req.name
                except Exception:
                    pass
            return {"message": "Profile already exists", "profile": existing[0]}

    if req.phone:
        existing_by_phone = find_by("profiles", "phone", normalize_phone(req.phone))
        if existing_by_phone:
            if req.name and req.name != existing_by_phone[0].get("name"):
                try:
                    update("profiles", existing_by_phone[0]["id"], {"name": req.name})
                    existing_by_phone[0]["name"] = req.name
                except Exception:
                    pass
            return {"message": "Profile already exists", "profile": existing_by_phone[0]}
    
    if req.email:
        existing_by_email = find_by("profiles", "email", req.email)
        if existing_by_email:
            return {"message": "Profile already exists", "profile": existing_by_email[0]}

    profile_id = req.id if (req.id and not req.id.startswith("user_")) else str(uuid4())
    profile_data = {
        "id": profile_id,
        "name": req.name or (req.email.split("@")[0] if req.email else "User"),
        "email": req.email or f"phone_{profile_id[:8]}@voiceshield.com",
        "phone": normalize_phone(req.phone) if req.phone else "",
        "phone_verified": bool(req.phone),
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


@router.post("/otp/send")
@router.post("/send-otp")
async def request_otp(req: SendOtpRequest):
    """Send an OTP code to user's phone via MSG91 (or dev fallback)."""
    if not req.phone or len(req.phone.strip()) < 7:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Valid phone number is required"
        )
    result = send_otp(req.phone)
    return result


@router.post("/otp/verify-signup")
async def verify_otp_signup(req: VerifyOtpSignupRequest):
    """
    Case 1: Calling app manual registration.
    Verifies OTP sent to the phone. If valid, registers the user in Supabase Auth & public.profiles,
    automatically generating all 5 user sub-tables via PostgreSQL triggers.
    Directs directly to dashboard upon return.
    """
    if not req.name or not req.phone or not req.otp:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Name, phone number, and OTP code are required"
        )

    formatted_phone = normalize_phone(req.phone)
    is_valid = verify_otp(formatted_phone, req.otp)
    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired OTP. Please try again or request a new code."
        )

    # Check if user already exists in profiles with this phone
    existing_users = find_by("profiles", "phone", formatted_phone)
    if existing_users:
        user = existing_users[0]
        # Ensure phone_verified is marked True
        if not user.get("phone_verified"):
            try:
                user = update("profiles", user["id"], {"phone_verified": True})
            except Exception:
                pass
        return {
            "message": "Account already exists. Logged in successfully.",
            "access_token": f"token-{user['id']}",
            "user": user
        }

    # Connect to Supabase
    client = _client()
    synthetic_email = f"phone_{''.join(ch for ch in formatted_phone if ch.isdigit())}@voiceshield.com"

    if client:
        try:
            # Atomic creation in Supabase Auth & public.profiles & user sub-tables via RPC
            rpc_resp = client.rpc("register_phone_user", {
                "phone_input": formatted_phone,
                "name_input": req.name.strip()
            }).execute()

            if rpc_resp.data:
                user_data = rpc_resp.data
                return {
                    "message": "Account verified and created successfully.",
                    "access_token": f"token-{user_data.get('id')}",
                    "user": user_data
                }
        except Exception as rpc_err:
            print(f"[Auth Error] register_phone_user RPC notice: {rpc_err}")

    # Fallback if RPC fails: check if profile already exists or insert manually
    existing_users = find_by("profiles", "phone", formatted_phone)
    if existing_users:
        user = existing_users[0]
        return {
            "message": "Account verified and logged in.",
            "access_token": f"token-{user['id']}",
            "user": user
        }

    user_id = str(uuid4())
    if client:
        try:
            auth_resp = client.auth.sign_up({
                "email": synthetic_email,
                "password": str(uuid4()),
                "options": {
                    "data": {
                        "name": req.name.strip(),
                        "phone": formatted_phone,
                        "phone_verified": True
                    }
                }
            })
            if auth_resp.user:
                user_id = auth_resp.user.id
        except Exception as auth_err:
            print(f"[Auth Error] Supabase auth signup notice: {auth_err}")

    profile_data = {
        "id": user_id,
        "name": req.name.strip(),
        "email": synthetic_email,
        "phone": formatted_phone,
        "phone_verified": True,
        "role": "user"
    }

    try:
        saved_profile = insert("profiles", profile_data)
        return {
            "message": "Account verified and created successfully.",
            "access_token": f"token-{user_id}",
            "user": saved_profile
        }
    except Exception as e:
        existing = find_by("profiles", "id", user_id)
        if existing:
            return {
                "message": "Account verified and created successfully.",
                "access_token": f"token-{user_id}",
                "user": existing[0]
            }
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create user profile in Supabase: {str(e)}"
        )



@router.post("/otp/verify-phone")
async def verify_phone_existing_user(req: VerifyPhoneRequest):
    """
    Case 2: Existing or Google OAuth user verifying their phone number.
    Verifies OTP and updates public.profiles with the phone and phone_verified=true.
    User's UUID remains completely untouched.
    """
    if not req.user_id or not req.phone or not req.otp:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="User ID, phone number, and OTP are required"
        )

    formatted_phone = normalize_phone(req.phone)
    is_valid = verify_otp(formatted_phone, req.otp)
    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired OTP code."
        )

    # Check if another profile is already using this phone number
    existing_phone = find_by("profiles", "phone", formatted_phone)
    for p in existing_phone:
        if p.get("id") != req.user_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="This phone number is already associated with another account."
            )

    try:
        updated = update("profiles", req.user_id, {
            "phone": formatted_phone,
            "phone_verified": True
        })
        return {
            "message": "Phone number verified and updated successfully.",
            "profile": updated
        }
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to update profile: {str(e)}"
        )


@router.post("/otp/login-phone")
async def login_with_phone_otp(req: PhoneLoginRequest):
    """Login using Phone Number and OTP."""
    if not req.phone or not req.otp:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Phone number and OTP code are required"
        )

    formatted_phone = normalize_phone(req.phone)
    is_valid = verify_otp(formatted_phone, req.otp)
    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired OTP code."
        )

    profiles = find_by("profiles", "phone", formatted_phone)
    if not profiles:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No account found with this phone number. Please create an account."
        )

    user = profiles[0]
    return {
        "message": "Login successful",
        "access_token": f"token-{user['id']}",
        "user": user
    }

# ── Phone OTP Verification (Mobile App & Direct API) ──

@router.post("/verify-otp")
async def handle_verify_otp(req: VerifyOtpRequest):
    """
    Verify OTP for Android mobile app or direct API consumers.
    Verifies OTP via MSG91/dev fallback, registers or retrieves the user in Supabase Auth & public.profiles,
    provisions user partition sub-tables via PostgreSQL triggers, and returns the confirmed user with a valid UUID.
    """
    if not req.phone or not req.otp:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Phone number and OTP code are required"
        )

    formatted_phone = normalize_phone(req.phone)
    is_valid = verify_otp(formatted_phone, req.otp)
    if not is_valid:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or expired OTP. Please try again or request a new code."
        )

    user_name = (req.name.strip() if req.name else None) or f"User {formatted_phone[-4:]}"

    client = _client()
    if client:
        try:
            rpc_resp = client.rpc("register_phone_user", {
                "phone_input": formatted_phone,
                "name_input": user_name
            }).execute()

            if rpc_resp.data:
                user_data = rpc_resp.data
                token = f"vs_token_{user_data.get('id')}_{uuid4()}"
                return {
                    "message": "OTP verified successfully",
                    "access_token": token,
                    "token": token,
                    "user": {
                        "id": user_data.get("id"),
                        "name": user_data.get("name", user_name),
                        "email": user_data.get("email", ""),
                        "phone": user_data.get("phone", formatted_phone)
                    }
                }
        except Exception as rpc_err:
            print(f"[Auth Error] register_phone_user RPC error: {rpc_err}")

    # Fallback to local profiles
    existing_users = find_by("profiles", "phone", formatted_phone)
    if existing_users:
        user = existing_users[0]
        token = f"vs_token_{user['id']}_{uuid4()}"
        return {
            "message": "OTP verified successfully",
            "access_token": token,
            "token": token,
            "user": {
                "id": user["id"],
                "name": user.get("name", user_name),
                "email": user.get("email", ""),
                "phone": user.get("phone", formatted_phone)
            }
        }

    # If new user and RPC was not available
    user_id = str(uuid4())
    user_data = {
        "id": user_id,
        "name": user_name,
        "phone": formatted_phone,
        "email": f"phone_{''.join(ch for ch in formatted_phone if ch.isdigit())}@voiceshield.com",
        "phone_verified": True,
        "role": "user"
    }
    saved_profile = insert("profiles", user_data)
    token = f"vs_token_{user_id}_{uuid4()}"
    return {
        "message": "OTP verified successfully",
        "access_token": token,
        "token": token,
        "user": {
            "id": saved_profile.get("id", user_id),
            "name": saved_profile.get("name", user_name),
            "email": saved_profile.get("email", ""),
            "phone": saved_profile.get("phone", formatted_phone)
        }
    }


