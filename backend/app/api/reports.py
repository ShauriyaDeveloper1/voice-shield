"""Spam reporting API routes."""

import logging
from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel
from uuid import uuid4
from datetime import datetime, timezone

from app.services.repository import _client

logger = logging.getLogger(__name__)

router = APIRouter()

SPAM_THRESHOLD = 20  # Number of reports before a number is marked as spam

# Resilient in-memory fallback storage for spam reports
_in_memory_reports: list[dict] = []


class ReportRequest(BaseModel):
    reporter_user_id: str
    reported_phone: str
    reason: str | None = None


class SpamCheckResponse(BaseModel):
    phone: str
    report_count: int
    is_spam: bool


def _record_in_memory(reporter_user_id: str, reported_phone: str, reason: str | None = None) -> tuple[int, bool]:
    clean_phone = "".join(filter(str.isdigit, reported_phone))
    already = any(
        r["reporter_user_id"] == reporter_user_id and "".join(filter(str.isdigit, r["reported_phone"])) == clean_phone
        for r in _in_memory_reports
    )
    if not already:
        _in_memory_reports.append({
            "id": str(uuid4()),
            "reporter_user_id": reporter_user_id,
            "reported_phone": reported_phone,
            "clean_phone": clean_phone,
            "reason": reason,
            "created_at": datetime.now(timezone.utc).isoformat(),
        })
    count = sum(1 for r in _in_memory_reports if r.get("clean_phone") == clean_phone or r.get("reported_phone") == reported_phone)
    return count, count >= SPAM_THRESHOLD


@router.post("/report", status_code=status.HTTP_201_CREATED)
async def report_number(request: ReportRequest):
    """Report a phone number as spam/scam."""
    client = _client()
    if client:
        try:
            # Check if this user already reported this number
            existing = (
                client.table("spam_reports")
                .select("id")
                .eq("reporter_user_id", request.reporter_user_id)
                .eq("reported_phone", request.reported_phone)
                .execute()
            )
            if existing.data:
                # Return current count without failing
                total = (
                    client.table("spam_reports")
                    .select("id", count="exact")
                    .eq("reported_phone", request.reported_phone)
                    .execute()
                )
                report_count = total.count or 1
                return {
                    "message": "You have already reported this number",
                    "report_count": report_count,
                    "is_spam": report_count >= SPAM_THRESHOLD,
                }

            # Insert the report into Supabase
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
            report_count = total.count or 1
            is_spam = report_count >= SPAM_THRESHOLD

            return {
                "message": "Report submitted successfully",
                "report_count": report_count,
                "is_spam": is_spam,
            }
        except Exception as e:
            logger.warning(f"Supabase spam_reports query failed ({e}), using resilient memory store")
            report_count, is_spam = _record_in_memory(request.reporter_user_id, request.reported_phone, request.reason)
            return {
                "message": "Report submitted successfully",
                "report_count": max(report_count, 1),
                "is_spam": is_spam,
            }
    else:
        # In-memory fallback
        report_count, is_spam = _record_in_memory(request.reporter_user_id, request.reported_phone, request.reason)
        return {
            "message": "Report submitted successfully",
            "report_count": max(report_count, 1),
            "is_spam": is_spam,
        }


@router.get("/check/{phone}")
async def check_spam(phone: str):
    """Check if a phone number has been reported as spam."""
    client = _client()
    if client:
        try:
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
        except Exception as e:
            logger.warning(f"Supabase spam_reports check failed ({e}), checking in-memory")
    
    # Fallback checking in-memory
    clean = "".join(filter(str.isdigit, phone))
    mem_count = sum(1 for r in _in_memory_reports if r.get("clean_phone") == clean or r.get("reported_phone") == phone)
    return SpamCheckResponse(
        phone=phone,
        report_count=mem_count,
        is_spam=mem_count >= SPAM_THRESHOLD
    )

