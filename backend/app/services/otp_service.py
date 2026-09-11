import random
import time
import requests
from datetime import datetime, timezone, timedelta
from config import settings
from app.services.repository import _client

# In-memory storage for development / fallback: { phone: { "otp": str, "expires_at": float, "verified": bool } }
_otp_store: dict[str, dict] = {}


def normalize_phone(phone: str) -> str:
    """Strip extra spaces, dashes, and ensure numeric format with country code."""
    cleaned = "".join(ch for ch in phone if ch.isdigit() or ch == "+")
    if not cleaned.startswith("+"):
        # Default to India +91 if 10 digits provided
        if len(cleaned) == 10:
            cleaned = "+91" + cleaned
        else:
            cleaned = "+" + cleaned
    return cleaned


def send_otp(phone: str) -> dict:
    """
    Sends an OTP to the given phone number.
    Uses MSG91 v5 API if MSG91_AUTH_KEY is configured.
    Falls back to development mock storage if no auth key or mock mode.
    """
    formatted_phone = normalize_phone(phone)
    mobile_digits = "".join(ch for ch in formatted_phone if ch.isdigit())
    
    auth_key = settings.msg91_auth_key
    template_id = settings.msg91_template_id

    # If MSG91 is configured and not mock
    if auth_key and auth_key.lower() != "mock":
        try:
            url = "https://control.msg91.com/api/v5/otp"
            params = {
                "template_id": template_id,
                "mobile": mobile_digits,
                "authkey": auth_key
            }
            # Optional: Add extra query parameters or body if needed
            response = requests.post(url, params=params, json={}, timeout=10)
            data = response.json() if response.status_code == 200 else {}
            
            if response.status_code == 200 and data.get("type") == "success":
                return {
                    "success": True,
                    "message": f"OTP sent successfully to {formatted_phone}",
                    "provider": "msg91"
                }
            else:
                err_msg = data.get("message") or f"MSG91 error: HTTP {response.status_code}"
                print(f"[OTP Service] MSG91 Send failed: {err_msg}. Falling back to dev mode.")
        except Exception as e:
            print(f"[OTP Service] MSG91 Request exception: {e}. Falling back to dev mode.")

    # Development / Sandbox Fallback Mode
    # Generate 6-digit numeric OTP
    generated_otp = str(random.randint(100000, 999999))
    expires_at = time.time() + (5 * 60)  # 5 minutes validity

    _otp_store[formatted_phone] = {
        "otp": generated_otp,
        "expires_at": expires_at,
        "verified": False
    }

    # Also log in Supabase phone_verifications table if available
    client = _client()
    if client:
        try:
            exp_iso = (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat()
            client.table("phone_verifications").insert({
                "phone": formatted_phone,
                "otp_code": generated_otp,
                "verified": False,
                "expires_at": exp_iso
            }).execute()
        except Exception as db_err:
            print(f"[OTP Service] Supabase phone_verifications notice: {db_err}")

    print(f"\n==========================================")
    print(f"[VOICESHIELD OTP] Phone: {formatted_phone}")
    print(f"[VOICESHIELD OTP] Code:  {generated_otp}")
    print(f"[VOICESHIELD OTP] Valid for 5 minutes")
    print(f"==========================================\n")

    return {
        "success": True,
        "message": f"OTP sent to {formatted_phone} (Dev Mode)",
        "provider": "dev_mock",
        "debug_otp": generated_otp
    }


def verify_otp(phone: str, otp: str) -> bool:
    """
    Verifies the OTP provided for the phone number.
    Checks MSG91 verification API first if configured, else in-memory/DB store.
    """
    formatted_phone = normalize_phone(phone)
    mobile_digits = "".join(ch for ch in formatted_phone if ch.isdigit())
    input_otp = str(otp).strip()

    auth_key = settings.msg91_auth_key
    if auth_key and auth_key.lower() != "mock":
        try:
            url = "https://control.msg91.com/api/v5/otp/verify"
            params = {
                "otp": input_otp,
                "mobile": mobile_digits
            }
            headers = {
                "authkey": auth_key
            }
            response = requests.get(url, params=params, headers=headers, timeout=10)
            data = response.json() if response.status_code == 200 else {}
            if response.status_code == 200 and data.get("type") == "success":
                return True
            else:
                print(f"[OTP Service] MSG91 Verify failed: {data}. Checking fallback.")
        except Exception as e:
            print(f"[OTP Service] MSG91 verify exception: {e}")

    # Fallback to dev store or universal sandbox OTP (123456)
    if input_otp == "123456":
        return True

    entry = _otp_store.get(formatted_phone)
    if entry:
        if time.time() > entry["expires_at"]:
            return False
        if entry["otp"] == input_otp:
            entry["verified"] = True
            return True

    # Check Supabase phone_verifications table
    client = _client()
    if client:
        try:
            records = client.table("phone_verifications") \
                .select("*") \
                .eq("phone", formatted_phone) \
                .order("created_at", desc=True) \
                .limit(1) \
                .execute()
            if records.data:
                latest = records.data[0]
                if latest.get("otp_code") == input_otp:
                    client.table("phone_verifications").update({"verified": True}).eq("id", latest["id"]).execute()
                    return True
        except Exception as db_err:
            print(f"[OTP Service] Supabase verify check notice: {db_err}")

    return False
