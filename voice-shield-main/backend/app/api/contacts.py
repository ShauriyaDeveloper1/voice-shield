from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel
import requests

from app.models.schemas import ContactCreate
from app.services.repository import insert, find_by, delete, _client

router = APIRouter()


class GoogleSyncRequest(BaseModel):
    google_email: str
    token: str


@router.get("/")
async def get_contacts(user_id: str):
    try:
        contacts = find_by("trusted_contacts", "user_id", user_id)
        return contacts
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to fetch contacts: {str(e)}"
        )


@router.post("/")
async def add_contact(user_id: str, contact: ContactCreate):
    contact_data = {
        "user_id": user_id,
        "name": contact.name,
        "phone": contact.phone,
        "relation": contact.relation
    }
    try:
        saved_contact = insert("trusted_contacts", contact_data)
        return saved_contact
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to save contact: {str(e)}"
        )


@router.delete("/{contact_id}")
async def delete_contact(contact_id: str):
    try:
        success = delete("trusted_contacts", contact_id)
        if not success:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Contact not found"
            )
        return {"message": "Contact deleted successfully"}
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to delete contact: {str(e)}"
        )


@router.post("/sync-google")
async def sync_google_contacts(user_id: str, req: GoogleSyncRequest):
    imported_contacts = []
    
    # If the token is real, try fetching using Google People API
    if req.token and not req.token.startswith("mock_"):
        try:
            headers = {"Authorization": f"Bearer {req.token}"}
            base_url = "https://people.googleapis.com/v1/people/me/connections"
            params = {
                "personFields": "names,phoneNumbers,relations",
                "pageSize": "100"
            }
            
            while True:
                response = requests.get(base_url, headers=headers, params=params, timeout=15)
                if response.status_code == 401:
                    raise HTTPException(
                        status_code=status.HTTP_401_UNAUTHORIZED,
                        detail="Google access token expired or invalid. Please re-authenticate with Google."
                    )
                if response.status_code != 200:
                    print(f"Google People API error: {response.status_code} - {response.text}")
                    break
                    
                data = response.json()
                connections = data.get("connections", [])
                for person in connections:
                    names = person.get("names", [])
                    phone_numbers = person.get("phoneNumbers", [])
                    relations = person.get("relations", [])
                    
                    if names and phone_numbers:
                        name = names[0].get("displayName", "Unknown Google Contact")
                        phone = phone_numbers[0].get("value", "")
                        relation = relations[0].get("type", "Friend") if relations else "Friend"
                        
                        imported_contacts.append({
                            "name": name,
                            "phone": phone,
                            "relation": relation.capitalize()
                        })
                    elif names:
                        # Include contacts even without phone numbers
                        name = names[0].get("displayName", "Unknown")
                        imported_contacts.append({
                            "name": name,
                            "phone": "",
                            "relation": "Friend"
                        })
                
                next_page = data.get("nextPageToken")
                if not next_page:
                    break
                params["pageToken"] = next_page
                
        except HTTPException:
            raise
        except Exception as e:
            print(f"Failed to fetch from Google People API: {e}")
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail=f"Failed to fetch Google contacts: {str(e)}"
            )
            
    # If no real contacts were imported, return an informative error
    if not imported_contacts:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Could not fetch contacts from Google. Please ensure you have granted contacts permission and try again. If the problem persists, re-authenticate with Google."
        )
        
    saved_contacts = []
    try:
        for contact in imported_contacts:
            contact_data = {
                "user_id": user_id,
                "name": contact["name"],
                "phone": contact["phone"],
                "relation": contact["relation"]
            }
            # Avoid duplicate phones for the same user in our database/memory
            existing = find_by("trusted_contacts", "user_id", user_id)
            if not any(c.get("phone") == contact["phone"] for c in existing):
                saved = insert("trusted_contacts", contact_data)
                saved_contacts.append(saved)
            else:
                dup = next(c for c in existing if c.get("phone") == contact["phone"])
                saved_contacts.append(dup)
                
        return {
            "message": f"Successfully synchronized {len(saved_contacts)} contacts.",
            "contacts": saved_contacts
        }
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to save synchronized contacts: {str(e)}"
        )
