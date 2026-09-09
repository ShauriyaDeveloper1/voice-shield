---
license: mit
language:
  - en
  - hi
  - ta
  - te
  - bn
  - mr
tags:
  - audio
  - deepfake-detection
  - anti-spoofing
  - aasist
  - voiceshield
pipeline_tag: audio-classification
---

# VoiceShield — AASIST Universal Audio Deepfake Detector

Fine-tuned **AASIST** (Audio Anti-Spoofing using Integrated Spectro-Temporal Graph Attention Networks) for detecting deepfake/synthetic speech across **multiple languages**.

## Model Details

| Property | Value |
|---|---|
| **Base Model** | [AASIST (NAVER Corp)](https://github.com/clovaai/aasist) |
| **License** | MIT |
| **Task** | Audio Classification (Bonafide vs Spoof) |
| **Input** | 16kHz mono audio (~4 seconds) |
| **Output** | Binary classification: `bonafide` or `spoof` |
| **Parameters** | ~297K |

## Training Data

Fine-tuned on a merged dataset of:
- **IndicTTS Deepfakes** — Synthetic speech in Hindi, Tamil, Telugu, Bengali, Marathi, and other Indian languages
- **ElevenLabs English Deepfakes** — High-quality English AI-generated speech (garystafford dataset)

Original AASIST was pre-trained on **ASVspoof2019 LA** (Logical Access) dataset.

## Usage

```python
import torch
from huggingface_hub import hf_hub_download

# Download weights
weights_path = hf_hub_download(
    repo_id="Shauriya24/voiceshield-aasist",
    filename="AASIST_universal_best.pth"
)

# Load model (requires AASIST architecture code)
from models.AASIST import Model

model_config = {
    "architecture": "AASIST",
    "nb_samp": 64600,
    "first_conv": 128,
    "filts": [70, [1, 32], [32, 32], [32, 64], [64, 64]],
    "gat_dims": [64, 32],
    "pool_ratios": [0.5, 0.7, 0.5, 0.5],
    "temperatures": [2.0, 2.0, 100.0, 100.0]
}

model = Model(model_config)
model.load_state_dict(torch.load(weights_path, map_location="cpu"))
model.eval()
```

## Citation

```bibtex
@inproceedings{Jung2022AASIST,
  title={AASIST: Audio Anti-Spoofing using Integrated Spectro-Temporal Graph Attention Networks},
  author={Jee-weon Jung and Hee-Soo Heo and Hemlata Tak and others},
  booktitle={ICASSP},
  year={2022}
}
```

## Acknowledgements

- Base architecture by **NAVER Corp** (MIT License)
- Fine-tuned as part of the **VoiceShield** project for **Smart India Hackathon (SIH)**
