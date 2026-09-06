# VoiceShield Backend

This backend provides the API and services for the VoiceShield project.

## Run locally

```bash
cd backend
python -m venv venv
source venv/bin/activate  # or venv\Scripts\activate on Windows
pip install -r requirements.txt
uvicorn app.main:app --reload
```

## API

- `/` - health check root
- `/health` - service health endpoint
- `/api/auth/*` - authentication endpoints
- `/api/calls/*` - call management endpoints
- `/api/analysis/*` - audio analysis endpoints
- `/api/users/*` - user endpoints
