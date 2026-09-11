"""Spam reporting API routes."""

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel
from uuid import uuid4
from datetime import datetime, timezone

from app.services.repository import _client, insert, find_by

router = APIRouter()

SPAM_THRESHOLD = 20  # Number of reports before a number is marked as spam


class ReportRequest(BaseModel):
    reporter_user_id: str
    reported_phone: str
    reason: str | None = None


class SpamCheckResponse(BaseModel):
    phone: str
    report_count: int
    is_spam: bool


@router.post("/report", status_code=status.HTTP_201_CREATED)
async def report_number(request: ReportRequest):
    """Report a phone number as spam/scam."""
    client = _client()
    if client:
        # Check if this user already reported this number
        existing = (
            client.table("spam_reports")
            .select("id")
            .eq("reporter_user_id", request.reporter_user_id)
            .eq("reported_phone", request.reported_phone)
            .execute()
        )
        if existing.data:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="You have already reported this number"
            )

        # Insert the report
        client.table("spam_reports").insert({
            "id": str(uuid4()),
            "reporter_user_id": request.reporter_user_id,
            "reported_phone": request.reported_phone,
            "reason": request.reason,
            "created_at": datetime.now(timezone.utc).isoformat(),
        }).execute()

        # Get total reports for this number
        total = (
            client.table("spam_reports")
            .select("id", count="exact")
            .eq("reported_phone", request.reported_phone)
            .execute()
        )
        report_count = total.count or 0
        is_spam = report_count >= SPAM_THRESHOLD

        return {
            "message": "Report submitted successfully",
            "report_count": report_count,
            "is_spam": is_spam,
        }
    else:
        # In-memory fallback for development
        return {
            "message": "Report submitted (in-memory)",
            "report_count": 1,
            "is_spam": False,
        }


@router.get("/check/{phone}")
async def check_spam(phone: str):
    """Check if a phone number has been reported as spam."""
    client = _client()
    if client:
        result = (
            client.table("spam_reports")
            .select("id", count="exact")
            .eq("reported_phone", phone)
            .execute()
        )
        report_count = result.count or 0
        is_spam = report_count >= SPAM_THRESHOLD
        return SpamCheckResponse(
            phone=phone,
            report_count=report_count,
            is_spam=is_spam,
        )
    else:
        return SpamCheckResponse(phone=phone, report_count=0, is_spam=False)
