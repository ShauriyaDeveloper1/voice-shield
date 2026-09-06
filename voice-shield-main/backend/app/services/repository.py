from datetime import datetime, timezone
from uuid import uuid4

from config import settings

try:
    from supabase import Client, create_client
except ImportError:  # Allows local API development before dependencies are installed.
    Client = object
    create_client = None


_memory: dict[str, list[dict]] = {
    "profiles": [],
    "calls": [],
    "call_analysis": [],
    "alerts": [],
    "trusted_contacts": [],
    "speaker_profiles": [],
}


def _client() -> Client | None:
    url = settings.supabase_url
    key = settings.supabase_key or settings.supabase_anon_key
    if not url or not key or create_client is None:
        return None
    # Sanitize URL if it contains REST API path suffix
    if url.endswith("/rest/v1/"):
        url = url[:-9]
    elif url.endswith("/rest/v1"):
        url = url[:-8]
    try:
        return create_client(url, key)
    except Exception as e:
        print(f"Failed to create Supabase client: {e}")
        return None


def insert(table: str, values: dict) -> dict:
    values = {"id": str(uuid4()), **values}
    if table in {"profiles", "speaker_profiles", "alerts", "trusted_contacts"}:
        values.setdefault("created_at", _now())
    if table == "call_analysis":
        values.setdefault("timestamp", _now())
    client = _client()
    if client:
        response = client.table(table).insert(values).execute()
        return response.data[0]
    _memory[table].append(values)
    return values


def get(table: str, record_id: str) -> dict | None:
    client = _client()
    if client:
        response = client.table(table).select("*").eq("id", record_id).maybe_single().execute()
        return response.data
    return next((item for item in _memory[table] if item["id"] == record_id), None)


def find_by(table: str, field: str, value: str) -> list[dict]:
    client = _client()
    if client:
        return client.table(table).select("*").eq(field, value).execute().data
    return [item for item in _memory[table] if item.get(field) == value]


def delete(table: str, record_id: str) -> bool:
    client = _client()
    if client:
        response = client.table(table).delete().eq("id", record_id).execute()
        return len(response.data) > 0
    
    global _memory
    initial_len = len(_memory[table])
    _memory[table] = [item for item in _memory[table] if item["id"] != record_id]
    return len(_memory[table]) < initial_len


def update(table: str, record_id: str, values: dict) -> dict:
    client = _client()
    if client:
        response = client.table(table).update(values).eq("id", record_id).execute()
        if len(response.data) > 0:
            return response.data[0]
        raise Exception("Record not found to update")
    
    global _memory
    for item in _memory[table]:
        if item["id"] == record_id:
            item.update(values)
            return item
    raise Exception("Record not found to update")


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()