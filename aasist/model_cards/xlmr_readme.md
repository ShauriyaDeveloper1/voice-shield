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
  - text-classification
  - social-engineering
  - nlp
  - xlm-roberta
  - voiceshield
  - multilingual
pipeline_tag: text-classification
---

# VoiceShield — XLM-RoBERTa Social Engineering Text Detector

Fine-tuned **XLM-RoBERTa** model for detecting **social engineering** and **suspicious text patterns** in voice call transcriptions across multiple languages.

## Model Details

| Property | Value |
|---|---|
| **Base Model** | [xlm-roberta-base](https://huggingface.co/FacebookAI/xlm-roberta-base) (Meta AI) |
| **License** | MIT |
| **Task** | Text Classification (3 classes) |
| **Input** | Text (max 128 tokens) |
| **Output** | `Normal`, `Suspicious`, or `Social Engineering` |
| **Parameters** | ~278M |

## Labels

| Label ID | Class | Description |
|---|---|---|
| 0 | **Normal** | Safe, non-threatening conversation |
| 1 | **Suspicious** | Potentially manipulative language |
| 2 | **Social Engineering** | Active social engineering attempt (phishing, vishing, impersonation) |

## Usage

```python
from transformers import AutoTokenizer, AutoModelForSequenceClassification
import torch

model_id = "Shauriya24/voiceshield-xlmr"
tokenizer = AutoTokenizer.from_pretrained(model_id)
model = AutoModelForSequenceClassification.from_pretrained(model_id)
model.eval()

text = "I am calling from your bank. Please share your OTP to verify your account."
inputs = tokenizer(text, return_tensors="pt", truncation=True, max_length=128)

with torch.no_grad():
    logits = model(**inputs).logits
    probs = torch.softmax(logits, dim=-1)[0]

labels = ["Normal", "Suspicious", "Social Engineering"]
pred = labels[probs.argmax()]
print(f"Prediction: {pred} ({probs.max():.2%})")
```

## Training

- **Epochs:** 2
- **Learning Rate:** 2e-5
- **Batch Size:** 32
- **Optimizer:** AdamW with weight decay 0.01
- **Mixed Precision:** FP16 (when GPU available)
- **Evaluation:** Per-epoch on validation split

## Metrics

Evaluated with **Accuracy** and **Weighted F1 Score** on the validation set.

## Citation

```bibtex
@article{conneau2020xlmr,
  title={Unsupervised Cross-lingual Representation Learning at Scale},
  author={Conneau, Alexis and others},
  journal={ACL},
  year={2020}
}
```

## Acknowledgements

- Base model by **Meta AI / Facebook Research**
- Fine-tuned as part of the **VoiceShield** project for **Smart India Hackathon (SIH)**
