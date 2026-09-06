<p align="center">
  <img src="logo.jpeg" alt="VoiceShield Logo" width="160" />
</p>

<h1 align="center">VoiceShield</h1>

<p align="center">
  <b>Real-time AI-powered voice deepfake detection & call security platform</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Python-3.10+-blue?logo=python&logoColor=white" alt="Python" />
  <img src="https://img.shields.io/badge/FastAPI-0.115-009688?logo=fastapi&logoColor=white" alt="FastAPI" />
  <img src="https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black" alt="React" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/PyTorch-1.6+-EE4C2C?logo=pytorch&logoColor=white" alt="PyTorch" />
  <img src="https://img.shields.io/badge/Supabase-Database-3FCF8E?logo=supabase&logoColor=white" alt="Supabase" />
</p>

---

## 📖 Overview

**VoiceShield** is a full-stack AI platform that protects users from voice-based deepfake attacks during phone calls. It combines state-of-the-art audio anti-spoofing models (AASIST) with a real-time analysis backend, a responsive web dashboard, and a native Android companion app.

### Key Features

- 🛡️ **Real-time Deepfake Detection** — AASIST-based spectro-temporal graph attention network classifies audio as bonafide or spoofed
- 📊 **Multi-Factor Risk Scoring** — Combines deepfake score, speaker verification, prosody analysis, and context scoring
- 📞 **Live Call Monitoring** — WebRTC-powered in-call analysis with instant threat alerts
- 👥 **Trusted Contacts Management** — Maintain a verified contact list with known voice profiles
- 📈 **Threat Analytics Dashboard** — Visualize call history, risk trends, and security insights
- 🔐 **User Authentication** — Secure signup/login with email verification via Supabase
- 📱 **Android Native App** — Kotlin + Jetpack Compose companion app with on-device ML support

---

## 🏗️ Architecture

```
voice-shield/
├── aasist/              # 🧠 AI/ML Engine — AASIST deepfake detection models
│   ├── models/          # Model architectures (AASIST, RawNet2, RawGAT-ST)
│   ├── config/          # Training & evaluation configurations
│   ├── inference.py     # Standalone inference script
│   ├── main.py          # Training & evaluation pipeline
│   └── requirements.txt
│
├── backend/             # ⚡ FastAPI Backend — REST API & business logic
│   ├── app/
│   │   ├── api/         # Route handlers (auth, calls, analysis, users, contacts)
│   │   ├── models/      # Pydantic data models
│   │   └── services/    # Business logic & Supabase integration
│   ├── supabase_schema.sql
│   └── requirements.txt
│
├── frontend/            # 🌐 React Web App — Dashboard & call interface
│   ├── src/
│   │   ├── pages/       # Dashboard, Call, Contacts, Settings, Analytics
│   │   ├── components/  # Reusable UI components
│   │   ├── context/     # React context providers
│   │   └── services/    # API service layer
│   └── package.json
│
├── app/                 # 📱 Android App — Kotlin + Jetpack Compose
│   └── src/main/java/com/sagar/voice_shield/
│       ├── data/        # Data layer (Room DB, API, repositories)
│       ├── ml/          # On-device ML inference
│       ├── ui/          # Compose UI screens & theme
│       ├── service/     # Background call monitoring service
│       ├── navigation/  # Navigation graph
│       └── notification/ # Alert notifications
│
├── models/              # 🗂️ Shared model weights directory
├── logo.jpeg            # Project logo
└── build.gradle.kts     # Root Gradle config (Android)
```

---

## 🚀 Getting Started

### Prerequisites

| Component | Requirement |
|-----------|-------------|
| **AASIST** | Python 3.10+, PyTorch ≥ 1.6, CUDA (optional, for GPU training) |
| **Backend** | Python 3.10+, Supabase project |
| **Frontend** | Node.js 18+, npm |
| **Android** | Android Studio, JDK 17, Android SDK 36 |

---

### 1. Clone the Repository

```bash
git clone https://github.com/ShauriyaDeveloper1/voice-shield.git
cd voice-shield
```

### 2. AASIST — AI/ML Engine

```bash
cd aasist
python -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

**Run inference on an audio file:**
```bash
python inference.py --audio sample_test.wav
```

**Train a model:**
```bash
python main.py --config ./config/AASIST.conf
```

**Evaluate a pre-trained model:**
```bash
python main.py --eval --config ./config/AASIST.conf
```

### 3. Backend — FastAPI Server

```bash
cd backend
python -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

**Configure environment variables:**
```bash
cp .env.example .env
# Edit .env with your Supabase credentials:
#   SUPABASE_URL=<your-supabase-url>
#   SUPABASE_ANON_KEY=<your-anon-key>
#   SECRET_KEY=<your-secret-key>
```

**Set up the database:**
Run `supabase_schema.sql` in your Supabase SQL editor to create all tables and triggers.

**Start the server:**
```bash
uvicorn app.main:app --reload
```

The API will be available at `http://localhost:8000`. Swagger docs at `/docs`.

### 4. Frontend — React Web App

```bash
cd frontend
npm install
npm run dev
```

The dashboard will be available at `http://localhost:5173`.

### 5. Android App

1. Open the project root in **Android Studio**
2. Sync Gradle dependencies
3. Build and run on an emulator or physical device (minSdk 26)

---

## 🔌 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/` | Health check |
| `GET` | `/health` | Service status |
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | User login |
| `GET` | `/api/calls/` | List call records |
| `POST` | `/api/calls/` | Create a call record |
| `POST` | `/api/analysis/` | Submit audio for analysis |
| `GET` | `/api/users/` | Get user profile |
| `GET` | `/api/contacts/` | List trusted contacts |
| `POST` | `/api/contacts/` | Add a trusted contact |

---

## 🗄️ Database Schema

VoiceShield uses **Supabase (PostgreSQL)** with user-partitioned tables for scalability:

| Table | Description |
|-------|-------------|
| `profiles` | User accounts (name, email, phone, role) |
| `trusted_contacts` | Per-user trusted contact list |
| `speaker_profiles` | Voice embeddings for speaker verification |
| `calls` | Call records with risk scores |
| `call_analysis` | Per-call deepfake, speaker, prosody, and context scores |
| `alerts` | Security alerts with severity and recommendations |

Each table (except `profiles`) is automatically partitioned per-user via database triggers.

---

## 🧠 AI Models

The deepfake detection engine is based on [AASIST](https://arxiv.org/abs/2110.01200) and supports multiple architectures:

| Model | Parameters | EER | min t-DCF |
|-------|-----------|-----|-----------|
| **AASIST** | ~300K | 0.83% | 0.0275 |
| **AASIST-L** | 85,306 | 0.99% | 0.0309 |
| **RawNet2** | Baseline | — | — |
| **RawGAT-ST** | Baseline | — | — |

Models are trained on the [ASVspoof 2019 LA dataset](https://www.asvspoof.org/index2019.html). Pre-trained weights can be hosted on Hugging Face.

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **AI/ML** | PyTorch, AASIST, XLM-RoBERTa, Hugging Face Transformers |
| **Backend** | FastAPI, Uvicorn, Pydantic, Librosa, SoundFile |
| **Database** | Supabase (PostgreSQL) with partitioned tables |
| **Frontend** | React 19, Vite, React Router, Recharts, Lucide Icons |
| **Android** | Kotlin, Jetpack Compose, Material 3, Room, Retrofit, WebRTC |
| **Auth** | Supabase Auth, Google Sign-In (Android) |
| **Deployment** | Vercel (frontend), Render (backend) |

---

## 📂 Deployment

### Frontend (Vercel)

The frontend is configured for Vercel deployment with SPA rewrites:

```bash
cd frontend
npm run build
# Deploy the dist/ folder to Vercel
```

### Backend (Render)

The backend is deployed on Render at:
```
https://voice-shield-backend-7xpl.onrender.com/
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

The AASIST model code is licensed under the **MIT License** (Copyright © NAVER Corp.).

---

## 🙏 Acknowledgements

- [AASIST](https://github.com/clovaai/aasist) — Audio Anti-Spoofing using Integrated Spectro-Temporal Graph Attention Networks
- [ASVspoof 2019](https://www.asvspoof.org/) — Large-scale public database for anti-spoofing research
- [Supabase](https://supabase.com/) — Open-source Firebase alternative
- [FastAPI](https://fastapi.tiangolo.com/) — Modern Python web framework
- [Vite](https://vite.dev/) — Next-generation frontend tooling

---

<p align="center">
  <b>Built with ❤️ by the VoiceShield Team</b>
</p>
