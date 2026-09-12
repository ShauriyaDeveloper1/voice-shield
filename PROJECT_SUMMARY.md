# VoiceShield Project Summary

## 1. Overall Summary

VoiceShield is a full-stack AI-powered voice security platform designed to detect voice deepfakes and suspicious caller behavior during live calls. The system combines an Android application, a React web dashboard, a FastAPI backend, Supabase services, WebRTC communication, and audio anti-spoofing models.

The platform analyzes voice signals and produces a risk score using multiple signals:

- AI-generated or spoofed voice probability
- Speaker similarity
- Prosody and acoustic characteristics
- Call context

The result is presented to the user through live warnings, notifications, call history, analytics, and trusted-contact features.

## 2. Main Components

### Android Application

The Android client is built with Kotlin and Jetpack Compose. It provides:

- VoiceShield user interface
- VoIP/WebRTC calling
- Foreground audio monitoring services
- Audio chunk capture and processing
- Risk indicators and warning notifications
- Local call history and trusted-contact storage
- Authentication and profile management

### Web Application

The web dashboard is built with React and Vite. It provides:

- Login and registration
- Dashboard and threat analytics
- WebRTC call interface
- Call history
- Trusted contacts
- Profile and settings management
- Backend health monitoring
- Security alerts and risk visualization

### Backend

The backend is implemented with FastAPI and provides REST endpoints for:

- Authentication
- User profiles
- Calls
- Audio analysis
- Trusted contacts
- Alerts and reports

The backend also integrates with Supabase and provides fallback audio processing when the remote AI service is unavailable.

### AI/ML Engine

The machine-learning component is based on AASIST, an audio anti-spoofing model using spectro-temporal graph attention networks. The repository also contains RawNet2 and RawGAT-ST baseline architectures, training configurations, pretrained weights, and evaluation scripts.

## 3. Technology Stack

| Area | Technologies |
|---|---|
| Android | Kotlin, Jetpack Compose, Material 3, Retrofit, OkHttp, Room, DataStore |
| Calling | WebRTC, WebSocket signaling, Android foreground services |
| Web frontend | React 19, Vite, React Router, Axios, Recharts, Lucide React |
| Backend | Python, FastAPI, Uvicorn, Pydantic |
| Audio processing | Librosa, SoundFile, NumPy, SciPy |
| Machine learning | PyTorch, AASIST, RawNet2, RawGAT-ST, Hugging Face Transformers |
| Database and authentication | Supabase, PostgreSQL, Supabase Auth |
| Deployment | Vercel, Render, Hugging Face Spaces |
| Build tools | Gradle Kotlin DSL, npm |

## 4. System Workflow

1. A user registers or logs in through the Android or web application.
2. Authentication and profile data are handled through the backend and Supabase.
3. The user starts or receives a VoIP call.
4. WebRTC establishes the audio connection and WebSocket signaling exchanges call information.
5. The Android audio engine or web call flow collects audio data.
6. Audio is divided into short chunks for analysis.
7. Prosody and acoustic features are calculated locally where applicable.
8. Audio is sent to the Hugging Face AASIST inference service.
9. If the AI service is unavailable, the system attempts backend analysis and then a heuristic fallback.
10. The backend combines the analysis features into a risk score.
11. The result is classified as LOW, MEDIUM, or HIGH risk.
12. The application displays the result, creates alerts when necessary, and stores call information.

## 5. Risk Scoring

The current backend risk engine uses the following weighted model:

- Deepfake score: 40%
- Speaker mismatch: 25%
- Prosody score: 15%
- Context score: 20%

Risk levels are currently defined as:

- LOW: score below 34
- MEDIUM: score from 34 through 66
- HIGH: score 67 or above

This is a deterministic first-pass scoring model and should be calibrated against representative real-world call data before production use.

## 6. References

### Project Files

- [Main README](README.md)
- [Backend README](backend/README.md)
- [AASIST README](aasist/README.md)
- [Android build configuration](app/build.gradle.kts)
- [Frontend package configuration](frontend/package.json)
- [Backend analysis API](backend/app/api/analysis.py)
- [Audio processing service](backend/app/services/audio_service.py)
- [Risk scoring engine](backend/app/services/risk_engine.py)
- [Android audio analysis engine](app/src/main/java/com/sagar/voice_shield/service/AudioCallEngine.kt)
- [Hugging Face client](app/src/main/java/com/sagar/voice_shield/data/remote/HuggingFaceGradioClient.kt)

### External References

- [AASIST research paper](https://arxiv.org/abs/2110.01200)
- [AASIST source repository](https://github.com/clovaai/aasist)
- [ASVspoof 2019 dataset](https://www.asvspoof.org/index2019.html)
- [FastAPI documentation](https://fastapi.tiangolo.com/)
- [Supabase documentation](https://supabase.com/docs)
- [WebRTC documentation](https://webrtc.org/)
- [Jetpack Compose documentation](https://developer.android.com/jetpack/compose)
- [Hugging Face Spaces documentation](https://huggingface.co/docs/hub/spaces)

## 7. Feasibility Assessment

### Prototype Feasibility: Very High

The project already includes the major components required for a working prototype: Android and web clients, API routes, database integration, WebRTC calling, audio capture, model inference, risk scoring, and user-facing alerts.

### Controlled Pilot Feasibility: High

A pilot focused on controlled VoIP or WebRTC calls is feasible. The inference service and fallback path allow the application to demonstrate the complete detection workflow.

### General Cellular Call Feasibility: Medium

Capturing remote audio from ordinary cellular calls depends on Android permissions, operating-system restrictions, device manufacturers, and telecom behavior. VoIP/WebRTC calls are more controllable and should be treated as the primary supported scenario until device compatibility is proven.

### Production Feasibility: Medium

The product can become production-ready, but it needs further work in the following areas:

1. Validate the model using real telephone, VoIP, noisy, compressed, multilingual, and adversarial audio.
2. Replace heuristic fallback scoring with a validated production inference service.
3. Improve inference latency, concurrency, monitoring, and scaling.
4. Secure credentials and remove secrets from source and build configuration.
5. Enforce server-side authentication and authorization on all user-owned data.
6. Add consent, encryption, retention, deletion, and audit policies for voice data.
7. Test call capture across supported Android devices and OS versions.
8. Add automated unit, integration, Android, frontend, and end-to-end tests.

## 8. Conclusion

VoiceShield is technically feasible as a research project, academic demonstration, and early MVP. Its architecture provides a credible foundation for a voice-deepfake detection product. The most important remaining challenges are real-world model validation, Android call-audio limitations, privacy protection, credential security, and reliable low-latency inference at scale.
