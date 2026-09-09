import argparse
import os
import torch
import numpy as np
from transformers import AutoTokenizer, AutoModelForSequenceClassification

def main():
    parser = argparse.ArgumentParser(description="VoiceShield NLP Inference")
    parser.add_argument("text", type=str, help="The text to classify")
    args = parser.parse_args()

    local_model_dir = os.path.abspath('./models/xlm-roberta-finetuned')
    if os.path.exists(local_model_dir):
        model_source = local_model_dir
        print(f"📦 Loading local model from {model_source}...")
    else:
        model_source = "Shauriya24/voiceshield-xlmr"
        print(f"🌐 Local model not found. Fetching from Hugging Face Hub: {model_source}...")

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"🖥  Device: {device}")

    print("📦 Loading model and tokenizer...")
    tokenizer = AutoTokenizer.from_pretrained(model_source)
    model = AutoModelForSequenceClassification.from_pretrained(model_source).to(device)
    model.eval()

    print(f"\n📝 Input Text: '{args.text}'")

    inputs = tokenizer(args.text, return_tensors="pt", truncation=True, max_length=128).to(device)
    
    with torch.no_grad():
        outputs = model(**inputs)
        logits = outputs.logits
        probs = torch.softmax(logits, dim=-1).cpu().numpy()[0]

    # 0 = Normal, 1 = Suspicious, 2 = Social Engineering
    labels = ["Normal", "Suspicious", "Social Engineering"]
    pred_idx = np.argmax(probs)
    pred_label = labels[pred_idx]
    confidence = probs[pred_idx] * 100

    print("-" * 50)
    if pred_idx == 0:
        print(f"✅ Prediction: {pred_label} (Confidence: {confidence:.2f}%)")
    else:
        print(f"🚨 Prediction: {pred_label} (Confidence: {confidence:.2f}%)")
    print("-" * 50)
    print("Class Probabilities:")
    for label, prob in zip(labels, probs):
        print(f"  - {label}: {prob * 100:.2f}%")

if __name__ == "__main__":
    main()
