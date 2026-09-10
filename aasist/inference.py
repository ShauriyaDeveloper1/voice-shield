"""
VoiceShield — AASIST Pretrained Inference Script
================================================
Standalone inference script for the AASIST audio anti-spoofing model.
Loads pretrained weights and classifies audio as bonafide or spoof.

Usage:
    python inference.py --audio <path_to_audio_file>
    python inference.py --audio sample1.wav sample2.flac

Supported formats: .wav, .flac (16kHz mono recommended)

Copyright (c) 2026 VoiceShield Project
Based on AASIST by NAVER Corp. (MIT License)
"""

import argparse
import sys
import os
import json
from importlib import import_module
from pathlib import Path

import numpy as np
import soundfile as sf
import torch
import torch.nn.functional as F


def pad(x: np.ndarray, max_len: int = 64600) -> np.ndarray:
    """
    Pad or trim audio to a fixed length.
    Uses tile-based padding (repeats audio) if too short,
    or truncates if too long. Matches AASIST's data_utils.pad().
    """
    x_len = x.shape[0]
    if x_len >= max_len:
        return x[:max_len]
    num_repeats = int(max_len / x_len) + 1
    padded_x = np.tile(x, (num_repeats))[:max_len]
    return padded_x


def load_model(model_config: dict, weights_path: str, device: str):
    """
    Load the AASIST model architecture and pretrained weights.

    Args:
        model_config: dict with architecture name and hyperparameters
        weights_path: path to .pth checkpoint file
        device: 'cuda' or 'cpu'

    Returns:
        model: loaded AASIST model in eval mode
    """
    # Dynamically import the model module (e.g., models/AASIST.py)
    arch = model_config["architecture"]
    module = import_module(f"models.{arch}")
    model_cls = getattr(module, "Model")

    # Build the model with config hyperparameters
    model = model_cls(model_config).to(device)

    # Load pretrained weights
    model.load_state_dict(torch.load(weights_path, map_location=device))
    model.eval()

    return model


def preprocess_audio(audio_path: str, max_len: int = 64600) -> torch.Tensor:
    """
    Load and preprocess a single audio file for AASIST inference.
    AASIST expects 16kHz mono audio, padded/trimmed to 64600 samples (~4 sec).

    Args:
        audio_path: path to .wav or .flac file
        max_len: expected number of samples (default: 64600 = ~4s at 16kHz)

    Returns:
        x: tensor of shape (1, max_len) ready for model input
    """
    # Read audio (soundfile returns float64 by default)
    audio, sr = sf.read(audio_path)

    # If stereo, convert to mono by averaging channels
    if len(audio.shape) > 1:
        audio = audio.mean(axis=1)

    # Warn if sample rate doesn't match expected 16kHz
    if sr != 16000:
        print(f"  ⚠ Warning: Audio sample rate is {sr}Hz (expected 16000Hz).")
        print(f"    Results may be unreliable. Consider resampling to 16kHz.")

    # Pad or trim to fixed length
    audio = pad(audio, max_len)

    # Convert to tensor: shape (1, max_len)
    x = torch.Tensor(audio).unsqueeze(0)
    return x


def run_inference(model, audio_tensor: torch.Tensor, device: str) -> dict:
    """
    Run a single forward pass through the AASIST model.

    Args:
        model: loaded AASIST model
        audio_tensor: preprocessed audio tensor of shape (1, max_len)
        device: 'cuda' or 'cpu'

    Returns:
        dict with prediction label, confidence scores, and raw logits
    """
    audio_tensor = audio_tensor.to(device)

    with torch.no_grad():
        # AASIST output: (batch, 2) — [spoof_logit, bonafide_logit]
        _, logits = model(audio_tensor)

    # Apply softmax to get probabilities
    probs = F.softmax(logits, dim=1).cpu().numpy()[0]

    # Index 0 = spoof, Index 1 = bonafide
    spoof_prob = float(probs[0])
    bonafide_prob = float(probs[1])

    label = "bonafide" if bonafide_prob > spoof_prob else "spoof"

    return {
        "label": label,
        "bonafide_confidence": bonafide_prob,
        "spoof_confidence": spoof_prob,
        "raw_logits": logits.cpu().numpy()[0].tolist()
    }


def main():
    parser = argparse.ArgumentParser(
        description="VoiceShield — AASIST Audio Anti-Spoofing Inference",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python inference.py --audio sample_test.flac
  python inference.py --audio recording.wav --model AASIST-L
  python inference.py --audio file1.wav file2.flac --cpu
        """
    )
    parser.add_argument(
        "--audio",
        nargs="+",
        required=True,
        help="Path(s) to audio file(s) to analyze (.wav, .flac)"
    )
    parser.add_argument(
        "--model",
        choices=["AASIST", "AASIST-L"],
        default="AASIST",
        help="Model variant to use (default: AASIST)"
    )
    parser.add_argument(
        "--cpu",
        action="store_true",
        help="Force CPU inference even if GPU is available"
    )
    args = parser.parse_args()

    # ── Banner ──────────────────────────────────────────────────────────
    print("=" * 60)
    print("  VoiceShield — AASIST Audio Anti-Spoofing Detector")
    print("=" * 60)
    print()

    # ── Device Setup ────────────────────────────────────────────────────
    if args.cpu:
        device = "cpu"
    else:
        device = "cuda" if torch.cuda.is_available() else "cpu"

    if device == "cuda":
        gpu_name = torch.cuda.get_device_name(0)
        print(f"🖥  Device: {device.upper()} ({gpu_name})")
    else:
        print(f"🖥  Device: {device.upper()}")
    print()

    # ── Load Config ─────────────────────────────────────────────────────
    config_path = Path(f"config/{args.model}.conf")
    if not config_path.exists():
        print(f"✗ Config file not found: {config_path}")
        sys.exit(1)

    with open(config_path, "r") as f:
        config = json.loads(f.read())

    model_config = config["model_config"]
    weights_path = config["model_path"]

    # ── Load Model ──────────────────────────────────────────────────────
    print(f"📦 Loading {args.model} model...")
    if not Path(weights_path).exists():
        print(f"🌐 Local weights not found at {weights_path}.")
        print("   Downloading from Hugging Face Hub: Shauriya24/voiceshield-aasist...")
        try:
            from huggingface_hub import hf_hub_download
            weights_path = hf_hub_download(
                repo_id="Shauriya24/voiceshield-aasist",
                filename="AASIST_universal_best.pth"
            )
            print(f"✓ Downloaded to: {weights_path}")
        except Exception as e:
            print(f"✗ Failed to download weights: {e}")
            sys.exit(1)

    model = load_model(model_config, weights_path, device)
    num_params = sum(p.numel() for p in model.parameters())
    print(f"✓  Model loaded from {weights_path}")
    print(f"   Parameters: {num_params:,}")
    print()

    # ── Process Audio Files ─────────────────────────────────────────────
    print(f"🎤 Processing {len(args.audio)} audio file(s)...")
    print("-" * 60)

    results = []
    for audio_path in args.audio:
        if not os.path.exists(audio_path):
            print(f"\n✗ File not found: {audio_path}")
            continue

        print(f"\n📄 File: {audio_path}")

        # Get audio info
        info = sf.info(audio_path)
        duration = info.duration
        print(f"   Duration: {duration:.2f}s | Sample Rate: {info.samplerate}Hz | "
              f"Channels: {info.channels}")

        # Preprocess
        audio_tensor = preprocess_audio(audio_path)

        # Run inference
        result = run_inference(model, audio_tensor, device)
        results.append({"file": audio_path, **result})

        # Display result
        label = result["label"]
        if label == "bonafide":
            icon = "✅"
            print(f"   Result: {icon} BONAFIDE (genuine speech)")
        else:
            icon = "🚨"
            print(f"   Result: {icon} SPOOF (deepfake/synthetic detected)")

        print(f"   Confidence — Bonafide: {result['bonafide_confidence']:.4f} | "
              f"Spoof: {result['spoof_confidence']:.4f}")

    # ── Summary ─────────────────────────────────────────────────────────
    print()
    print("=" * 60)
    print(f"  Summary: {len(results)} file(s) processed")
    spoofs = sum(1 for r in results if r["label"] == "spoof")
    bonafides = sum(1 for r in results if r["label"] == "bonafide")
    print(f"  Bonafide: {bonafides} | Spoof: {spoofs}")
    print("=" * 60)


if __name__ == "__main__":
    main()
