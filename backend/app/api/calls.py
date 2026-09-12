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


import logging
from fastapi import WebSocket, WebSocketDisconnect

logger = logging.getLogger("call_signaling")

def normalize_phone_key(phone: str) -> str:
    digits = "".join(c for c in phone if c.isdigit())
    if len(digits) >= 10:
        return f"+91{digits[-10:]}"
    return phone.strip()

class CallSignalingManager:
    def __init__(self):
        self.active_sockets: dict[str, WebSocket] = {}

    async def connect(self, phone: str, websocket: WebSocket):
        await websocket.accept()
        norm = normalize_phone_key(phone)
        self.active_sockets[phone] = websocket
        if norm != phone:
            self.active_sockets[norm] = websocket
        logger.info(f"WebSocket registered for phone: {phone} (normalized: {norm})")

    def disconnect(self, phone: str, websocket: WebSocket):
        norm = normalize_phone_key(phone)
        if self.active_sockets.get(phone) == websocket:
            del self.active_sockets[phone]
        if self.active_sockets.get(norm) == websocket:
            del self.active_sockets[norm]
        logger.info(f"WebSocket disconnected for phone: {phone}")

    def find_socket(self, target_phone: str) -> tuple[str | None, WebSocket | None]:
        target_digits = "".join(c for c in target_phone if c.isdigit())
        if not target_digits:
            return None, None
            
        for phone, socket in list(self.active_sockets.items()):
            phone_digits = "".join(c for c in phone if c.isdigit())
            if phone_digits == target_digits:
                return phone, socket
            # Match last 10 digits
            if len(phone_digits) >= 10 and len(target_digits) >= 10:
                if phone_digits[-10:] == target_digits[-10:]:
                    return phone, socket
        return None, None

    async def send_message(self, message: dict, to_phone: str):
        socket = self.active_sockets.get(to_phone)
        if socket:
            try:
                await socket.send_json(message)
            except Exception as e:
                logger.warning(f"Error sending message to {to_phone}: {e}")

signaling_manager = CallSignalingManager()


@router.websocket("/ws/{phone}")
async def websocket_endpoint(websocket: WebSocket, phone: str):
    await signaling_manager.connect(phone, websocket)
    try:
        while True:
            data = await websocket.receive_json()
            msg_type = data.get("type")

            if msg_type == "ping":
                await websocket.send_json({"type": "pong"})
                continue

            target_phone = data.get("to_phone")
            if target_phone:
                actual_target, target_socket = signaling_manager.find_socket(target_phone)
                data["from_phone"] = phone
                
                if msg_type == "call_initiate":
                    if not target_socket:
                        logger.info(f"Call initiate target {target_phone} not online. Online: {list(signaling_manager.active_sockets.keys())}")
                        await websocket.send_json({
                            "type": "call_status",
                            "status": "offline",
                            "to_phone": target_phone,
                            "message": f"User {target_phone} is offline."
                        })
                        continue
                
                if target_socket:
                    data["to_phone"] = actual_target
                    try:
                        await target_socket.send_json(data)
                    except Exception as send_err:
                        logger.warning(f"Failed forwarding {msg_type} to target {actual_target}: {send_err}")
                        signaling_manager.disconnect(actual_target, target_socket)
                        if msg_type == "call_initiate":
                            await websocket.send_json({
                                "type": "call_status",
                                "status": "offline",
                                "to_phone": target_phone,
                                "message": "Peer connection lost."
                            })
    except WebSocketDisconnect:
        pass
    except Exception as e:
        logger.debug(f"WebSocket session exception for {phone}: {e}")
    finally:
        signaling_manager.disconnect(phone, websocket)
