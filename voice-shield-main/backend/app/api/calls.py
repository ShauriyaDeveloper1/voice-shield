from fastapi import APIRouter, HTTPException

from app.models.schemas import AnalysisRequest, CallCreate
from app.services.repository import get, insert, find_by
from app.services.risk_engine import calculate_risk_score, risk_severity

router = APIRouter()


@router.post("/")
async def create_call(call: CallCreate):
    call_data = call.model_dump()
    if "user_id" not in call_data or not call_data["user_id"]:
        call_data["user_id"] = call.caller_id
    return insert("calls", call_data)


@router.get("/{call_id}")
async def get_call(call_id: str):
    call = get("calls", call_id)
    if call is None:
        raise HTTPException(status_code=404, detail="Call not found")
    return call


@router.post("/{call_id}/analysis")
async def analyze_call(call_id: str, request: AnalysisRequest):
    call = get("calls", call_id)
    if call is None:
        raise HTTPException(status_code=404, detail="Call not found")
    user_id = call.get("user_id") or call.get("caller_id")
    features = request.model_dump(exclude={"call_id", "timestamp", "features"})
    score = calculate_risk_score(features)
    severity = risk_severity(score)
    analysis = insert("call_analysis", {
        "user_id": user_id,
        "call_id": call_id,
        "deepfake_score": features["deepfake_score"],
        "speaker_score": features["speaker_similarity"],
        "prosody_score": features["prosody_score"],
        "context_score": features["context_score"],
        "risk_score": score
    })
    if severity != "LOW":
        insert("alerts", {
            "user_id": user_id,
            "call_id": call_id,
            "severity": severity,
            "message": "Elevated call risk detected",
            "recommendation": "Review this call"
        })
    return {"deepfake_score": features["deepfake_score"], "speaker_score": features["speaker_similarity"], "prosody_score": features["prosody_score"], "context_score": features["context_score"], "risk_score": score, "severity": severity, "analysis": analysis}


@router.get("/{call_id}/analysis")
async def get_call_analysis(call_id: str):
    if get("calls", call_id) is None:
        raise HTTPException(status_code=404, detail="Call not found")
    return find_by("call_analysis", "call_id", call_id)


@router.get("/{call_id}/risk")
async def get_call_risk(call_id: str):
    analyses = find_by("call_analysis", "call_id", call_id)
    if not analyses:
        raise HTTPException(status_code=404, detail="No analysis found for call")
    latest = analyses[-1]
    return {"call_id": call_id, "risk_score": latest["risk_score"], "severity": risk_severity(latest["risk_score"])}


from fastapi import WebSocket, WebSocketDisconnect

class CallSignalingManager:
    def __init__(self):
        self.active_sockets: dict[str, WebSocket] = {}

    async def connect(self, phone: str, websocket: WebSocket):
        await websocket.accept()
        self.active_sockets[phone] = websocket

    def disconnect(self, phone: str, websocket: WebSocket):
        if self.active_sockets.get(phone) == websocket:
            del self.active_sockets[phone]

    def find_socket(self, target_phone: str) -> tuple[str | None, WebSocket | None]:
        target_digits = "".join(c for c in target_phone if c.isdigit())
        if not target_digits:
            return None, None
            
        for phone, socket in self.active_sockets.items():
            phone_digits = "".join(c for c in phone if c.isdigit())
            if phone_digits == target_digits:
                return phone, socket
            # If target has at least 10 digits and registered phone has at least 10, match suffixes (last 10 digits)
            if len(phone_digits) >= 10 and len(target_digits) >= 10:
                if phone_digits[-10:] == target_digits[-10:]:
                    return phone, socket
        return None, None

    async def send_message(self, message: dict, to_phone: str):
        socket = self.active_sockets.get(to_phone)
        if socket:
            await socket.send_json(message)

signaling_manager = CallSignalingManager()


@router.get("/alerts/{phone}")
async def get_user_call_alerts(phone: str):
    target_clean = "".join(c for c in phone if c.isdigit())
    all_profiles = find_by("profiles", "phone", phone)
    if not all_profiles and len(target_clean) >= 10:
        for p in find_by("profiles", "role", "user"):
            p_digits = "".join(c for c in p.get("phone", "") if c.isdigit())
            if len(p_digits) >= 10 and p_digits[-10:] == target_clean[-10:]:
                all_profiles = [p]
                break
    if not all_profiles:
        return []
    user_id = all_profiles[0]["id"]
    alerts = find_by("alerts", "user_id", user_id)
    return alerts[-15:]


@router.websocket("/ws/{phone}")
async def websocket_endpoint(websocket: WebSocket, phone: str):
    await signaling_manager.connect(phone, websocket)
    try:
        while True:
            data = await websocket.receive_json()
            msg_type = data.get("type")
            target_phone = data.get("to_phone")
            
            if target_phone:
                actual_target, target_socket = signaling_manager.find_socket(target_phone)
                data["from_phone"] = phone
                
                if msg_type == "call_initiate":
                    # Check if target is online
                    if not target_socket:
                        from uuid import uuid4
                        caller_name = data.get("from_name") or phone
                        target_clean = "".join(c for c in target_phone if c.isdigit())

                        # Check if target has a registered profile to send alert to
                        all_profiles = find_by("profiles", "phone", target_phone)
                        if not all_profiles and len(target_clean) >= 10:
                            for p in find_by("profiles", "role", "user"):
                                p_digits = "".join(c for c in p.get("phone", "") if c.isdigit())
                                if len(p_digits) >= 10 and p_digits[-10:] == target_clean[-10:]:
                                    all_profiles = [p]
                                    break

                        if all_profiles:
                            target_user = all_profiles[0]
                            insert("alerts", {
                                "user_id": target_user["id"],
                                "call_id": "missed-" + str(uuid4())[:8],
                                "severity": "MEDIUM",
                                "message": f"Missed Call: {caller_name} ({phone}) called you while you were offline.",
                                "recommendation": "Review conversation and verify caller identity"
                            })

                        await websocket.send_json({
                            "type": "call_status",
                            "status": "offline",
                            "to_phone": target_phone,
                            "notification_sent": True,
                            "message": f"Missed call notification and alert generated for {target_phone}."
                        })
                        continue
                
                if target_socket:
                    data["to_phone"] = actual_target
                    await target_socket.send_json(data)
    except WebSocketDisconnect:
        pass
    except Exception:
        pass
    finally:
        signaling_manager.disconnect(phone, websocket)
