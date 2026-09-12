# Architecture Diagram

VoiceShield is a multi-client voice security platform. The web and Android clients use a FastAPI backend for account, call, analysis, and reporting workflows, while Supabase provides persistence and authentication services. Audio intelligence is split between a hosted AASIST inference service, a backend signal-processing fallback, and Android on-device analysis.

## Application Architecture

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart TD
    subgraph Clients["Client Layer"]
        WebClient["React 19 Web Dashboard"]
        AndroidClient["Android Kotlin Compose App"]
        BrowserAudio["Browser Audio and WebRTC"]
    end
    subgraph Api["Application Layer"]
        FastApi["FastAPI REST and WebSocket API"]
        CallRoutes["Auth Calls Contacts Reports"]
        AnalysisRoutes["Audio and Risk Analysis Routes"]
        RiskEngine["Weighted Risk Engine"]
        AudioService["Audio Processing Service"]
    end
    subgraph Intelligence["AI and Signal Layer"]
        HostedModel["Hosted AASIST Hugging Face Space"]
        LocalSignals["Librosa and SoundFile Fallback"]
        DeviceSignals["Android Prosody and On Device Risk"]
        Xlmr["XLM R Text Classifier"]
    end
    subgraph Data["Data Layer"]
        Supabase[("Supabase PostgreSQL and Auth")]
        Room[("Android Room Local History")]
        BrowserStore[("Browser Local Storage")]
    end
    subgraph External["External Services"]
        WebRtc["WebRTC Peer Media"]
        Stun["Google STUN Servers"]
        Otp["MSG91 OTP Provider"]
        Vercel["Vercel Frontend Hosting"]
        Render["Render Backend Hosting"]
    end

    WebClient -->|"REST requests"| FastApi
    WebClient -->|"sign in and OAuth"| Supabase
    WebClient -->|"call signaling"| FastApi
    BrowserAudio -->|"microphone and peer audio"| WebClient
    AndroidClient -->|"Retrofit REST requests"| FastApi
    AndroidClient -->|"local call history"| Room
    AndroidClient -->|"on device audio signals"| DeviceSignals
    WebClient -->|"call state and history"| BrowserStore
    FastApi -->|"routes requests"| CallRoutes
    FastApi -->|"routes uploads"| AnalysisRoutes
    AnalysisRoutes -->|"calculate score"| RiskEngine
    AnalysisRoutes -->|"process uploaded audio"| AudioService
    AudioService -->|"remote inference"| HostedModel
    AudioService -->|"fallback features"| LocalSignals
    RiskEngine -->|"persist analyses and alerts"| Supabase
    CallRoutes -->|"profiles calls contacts reports"| Supabase
    AndroidClient -->|"WebRTC signaling and media"| WebRtc
    WebRtc -->|"ICE discovery"| Stun
    FastApi -->|"OTP delivery"| Otp
    WebClient -->|"deployed static assets"| Vercel
    FastApi -->|"deployed API"| Render
    Xlmr -.->|"optional transcription classification"| HostedModel
~~~

### Technology Stack Summary

| Layer | Technology | Version | Purpose |
| --- | --- | ---: | --- |
| Web client | React, Vite, React Router | React 19, Vite 8 | Dashboard, authentication, call UI, analytics |
| Web networking | Axios, Supabase JS | Axios 1.19, Supabase JS 2.115 | REST calls and browser authentication |
| Android client | Kotlin, Jetpack Compose, Material 3 | Kotlin 2.1.20, Compose BOM 2025.05.01 | Native call protection and user interface |
| Android networking | Retrofit, OkHttp, Gson | Retrofit 2.11, OkHttp 4.12 | API and multipart audio communication |
| Android local data | Room, DataStore | Room 2.7.1, DataStore 1.1.7 | Local call history and preferences |
| Call transport | WebRTC and WebSocket signaling | Stream WebRTC 1.2.0 | Peer audio and call negotiation |
| Backend | FastAPI, Uvicorn, Pydantic | FastAPI 0.115, Uvicorn 0.30.6 | REST API, WebSocket signaling, validation |
| Backend audio | Librosa, SoundFile, NumPy | Librosa 0.10.2, NumPy 2.1 | Local feature extraction fallback |
| Persistence and auth | Supabase PostgreSQL and Auth | Supabase client 2.31 | Profiles, calls, analyses, alerts, contacts, OTP records |
| AI | PyTorch AASIST and XLM-RoBERTa | Project-managed weights | Audio anti-spoofing and text risk classification |
| Hosting | Vercel and Render | Platform managed | Static frontend and backend API deployment |

### Data Storage & External Services

Supabase is the system of record for profiles, calls, call analysis, alerts, trusted contacts, speaker profiles, spam reports, and phone verification records. The backend repository service uses Supabase when configured and falls back to in-memory collections for local development. Android stores local call history in Room, and the web dashboard stores a lightweight call history in browser local storage. Uploaded audio is temporarily written to a backend temporary file, sent to the Hugging Face Space when available, and then removed. WebRTC carries peer audio separately from the REST API; the backend WebSocket provides signaling, while Google STUN servers assist ICE discovery. MSG91 is used for production OTP delivery when configured, with a development fallback.

### Key Architectural Decisions

- The risk calculation is duplicated with the same weights in Python and Kotlin so Android can continue to score calls locally when network access is unavailable.
- Hosted AASIST inference is preferred for uploaded audio, with deterministic local acoustic heuristics as a fallback when the Hugging Face Space or model client is unavailable.
- REST handles durable business operations and analysis persistence, while WebSocket signaling and WebRTC handle live call setup and media transport.
- The current deployment model is platform-managed: Vercel serves the Vite frontend and Render serves the FastAPI backend. No repository CI workflow is present in the inspected project.

## Component Relationships

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart LR
    subgraph Presentation["Presentation"]
        cCompose["Compose Screens"]
        cReact["React Pages and Components"]
        cCallContext["Web Call Context"]
    end
    subgraph Business["Business Logic"]
        cAuthVm["Auth ViewModel"]
        cAndroidRisk["Android Risk Engine"]
        cProsody["Prosody Analyzer"]
        cApiRoutes["FastAPI Route Modules"]
        cRisk["Backend Risk Engine"]
    end
    subgraph DataAccess["Data Access"]
        cRetrofit["Retrofit API Interfaces"]
        cRepo["Android Repositories"]
        cRoom["Room DAOs"]
        cRepository["Backend Repository Service"]
        cSupabase["Supabase Tables"]
    end
    subgraph Infrastructure["Infrastructure and Integration"]
        cAudioService["Audio Analysis Service"]
        cWebSocket["Call Signaling Manager"]
        cWebRtc["WebRTC Managers"]
        cHosted["Hugging Face Inference"]
        cOtp["MSG91 OTP"]
    end

    cCompose -->|"state and events"| cAuthVm
    cCompose -->|"renders analysis"| cAndroidRisk
    cReact -->|"REST actions"| cApiRoutes
    cCallContext -->|"signaling and media state"| cWebSocket
    cCallContext -->|"peer connection"| cWebRtc
    cAuthVm -->|"auth requests"| cRepo
    cRepo -->|"HTTP calls"| cRetrofit
    cRetrofit -->|"API contract"| cApiRoutes
    cApiRoutes -->|"score features"| cRisk
    cApiRoutes -->|"persist records"| cRepository
    cApiRoutes -->|"send verification"| cOtp
    cRepository -->|"CRUD"| cSupabase
    cRepo -->|"local persistence"| cRoom
    cProsody -->|"audio signals"| cAndroidRisk
    cAudioService -->|"extract features"| cHosted
    cAudioService -->|"fallback features"| cRisk
    cWebSocket -->|"call messages"| cWebRtc
    cWebRtc -->|"peer audio"| cCallContext
~~~

### Component Inventory

| Component | Layer | Type | Responsibility |
| --- | --- | --- | --- |
| React pages and components | Presentation | Web UI | Login, dashboard, calls, contacts, settings, alerts, and analytics |
| Web call context | Presentation | React context | Maintains call state, WebRTC peer connections, ICE candidates, and browser audio |
| Compose screens | Presentation | Android UI | Native authentication, call controls, speaker protection, and history screens |
| Auth ViewModel | Business Logic | Android ViewModel | Coordinates Android login, registration, OTP, and profile state |
| FastAPI route modules | Business Logic | API controllers | Exposes auth, calls, analysis, users, contacts, and reports endpoints |
| Backend risk engine | Business Logic | Domain service | Converts deepfake, speaker, prosody, and context signals into score and severity |
| Android risk engine | Business Logic | On-device domain service | Applies the matching weighted score and creates user explanations |
| Prosody analyzer | Business Logic | Signal analyzer | Produces local speech characteristics for Android protection mode |
| Retrofit API interfaces | Data Access | Remote client | Defines Android REST and multipart contracts |
| Android repositories | Data Access | Repository | Combines remote API calls with local Room persistence |
| Room DAOs | Data Access | Local persistence | Reads and writes device call history |
| Backend repository service | Data Access | Persistence adapter | Uses Supabase tables or in-memory development storage |
| Audio analysis service | Infrastructure | Integration service | Sends temporary audio to hosted inference and computes fallback features |
| Call signaling manager | Infrastructure | WebSocket manager | Registers online phones and forwards call negotiation messages |
| WebRTC managers | Infrastructure | Media integration | Captures microphone audio and establishes peer media sessions |
| Hugging Face inference | Infrastructure | External AI service | Runs the hosted AASIST audio analysis endpoint |
| MSG91 OTP | Infrastructure | External notification service | Sends and verifies production phone OTPs |

## Architecture Workflow

1. A user opens the React dashboard or Android application and authenticates through FastAPI and Supabase-backed auth flows.
2. The client creates a call record through `POST /api/calls/`. The web client uses WebSocket signaling and WebRTC for live peer audio; Android uses its native call and foreground-service flow.
3. During monitoring, audio is either captured by the browser or Android foreground service. Android can compute prosody and risk locally without waiting for the network.
4. For uploaded or server-analyzed audio, FastAPI writes a temporary file and asks the Hugging Face AASIST Space for inference. If that call fails, Librosa and SoundFile generate fallback acoustic features.
5. The backend risk engine combines deepfake, speaker mismatch, prosody, and context signals using weights of 0.40, 0.25, 0.15, and 0.20.
6. The resulting score is classified as LOW below 34, MEDIUM below 67, or HIGH at 67 and above. The web or Android UI presents the score and explanations.
7. Call analyses are stored in Supabase. MEDIUM and HIGH results create alert records, which can later be read by client dashboards.
8. When a call ends, the web client writes a lightweight local history record and Android writes call history to Room; server-side analysis remains available through the call analysis endpoints.

## Pipeline Diagram

This diagram describes both the model lifecycle and the current application delivery path. The repository contains deployment configuration for Vercel and Render, but no checked-in CI workflow, so build and deployment automation is platform-managed or manually invoked.

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
~~~mermaid
flowchart LR
    subgraph Source["Source and Data"]
        Code["Application Source"]
        AudioData["Audio Datasets"]
        ModelConfig["AASIST and XLM R Config"]
    end
    subgraph Build["Build and Train"]
        WebBuild["Vite npm run build"]
        ApiInstall["Python requirements install"]
        AndroidBuild["Gradle Android Build"]
        Train["PyTorch Train Validate Evaluate"]
        Weights["Model Weights"]
    end
    subgraph Publish["Publish and Deploy"]
        HFPublish["Hugging Face Model or Space"]
        VercelDeploy["Vercel Frontend"]
        RenderDeploy["Render FastAPI Backend"]
        Apk["Android APK"]
    end
    subgraph Runtime["Runtime Verification and Use"]
        Browser["Web Dashboard"]
        Device["Android Device"]
        ApiRuntime["FastAPI Runtime"]
        Inference["Hosted AASIST Inference"]
        Database[("Supabase PostgreSQL")]
        Alerts["Risk Alerts and History"]
    end

    Code -->|"frontend sources"| WebBuild
    Code -->|"backend sources"| ApiInstall
    Code -->|"Android sources"| AndroidBuild
    AudioData -->|"training examples"| Train
    ModelConfig -->|"training settings"| Train
    Train -->|"checkpoint artifacts"| Weights
    Weights -->|"publish inference model"| HFPublish
    WebBuild -->|"dist assets"| VercelDeploy
    ApiInstall -->|"runtime image or service"| RenderDeploy
    AndroidBuild -->|"signed or debug package"| Apk
    HFPublish -->|"predict endpoint"| Inference
    VercelDeploy -->|"serves"| Browser
    RenderDeploy -->|"serves API"| ApiRuntime
    Apk -->|"installs"| Device
    Browser -->|"REST and WebSocket"| ApiRuntime
    Device -->|"REST and local analysis"| ApiRuntime
    ApiRuntime -->|"remote audio inference"| Inference
    ApiRuntime -->|"records and alerts"| Database
    ApiRuntime -->|"risk responses"| Alerts
    Inference -->|"scores and labels"| ApiRuntime
    Device -->|"local score and notification"| Alerts
~~~

### Pipeline Stages

| Stage | Current implementation | Output |
| --- | --- | --- |
| Source | `frontend`, `backend`, `app`, and `aasist` directories | Client, API, Android, and ML source |
| Web build | Vite `npm run build` | Static `dist` assets for Vercel |
| Backend packaging | Python requirements and Uvicorn entry point | FastAPI service for Render |
| Android build | Gradle Android application module | Debug or release APK |
| Model training | AASIST and XLM-R scripts with local model weights | Checkpoints and evaluation artifacts |
| Model publishing | Hugging Face upload scripts and hosted Space | Remote audio inference endpoint |
| Runtime persistence | Backend repository adapter to Supabase | Profiles, calls, analyses, alerts, contacts, and reports |
| Delivery control | Vercel and Render configuration; no checked-in CI workflow observed | Platform-managed or manually triggered deployment |
