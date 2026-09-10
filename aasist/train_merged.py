"""
VoiceShield — Universal Deepfake Audio Training Script
======================================================
Fine-tunes the AASIST model on a MERGED, highly diverse dataset containing
both Indic language deepfakes (IndicTTS) and English deepfakes (ElevenLabs).
"""

import argparse
import json
import os
import torch
import torch.nn as nn
import numpy as np
import torchaudio.transforms as T
from torch.utils.data import Dataset, DataLoader, ConcatDataset
from tqdm import tqdm
from torchcontrib.optim import SWA
from datasets import load_from_disk

from models.AASIST import Model
from utils import create_optimizer

# Enable aggressive GPU optimizations
torch.backends.cudnn.benchmark = True

def pad_random(audio_tensor, target_length=64600):
    """Pad or truncate the audio tensor to the exact target length with random start."""
    length = audio_tensor.size(-1)
    if length >= target_length:
        stt = np.random.randint(length - target_length + 1)
        return audio_tensor[..., stt:stt + target_length]
    elif length < target_length:
        num_repeats = int(target_length / length) + 1
        padded = audio_tensor.repeat(num_repeats)[..., :target_length]
        return padded
    return audio_tensor

class UniversalAudioDataset(Dataset):
    def __init__(self, hf_dataset, dataset_type="indic"):
        self.dataset = hf_dataset
        self.dataset_type = dataset_type
        self.resamplers = {}

    def __len__(self):
        return len(self.dataset)

    def __getitem__(self, idx):
        item = self.dataset[idx]
        audio_array = item["audio"]["array"]
        sample_rate = item["audio"]["sampling_rate"]
        
        # Normalize labels from different datasets
        if self.dataset_type == "indic":
            is_tts = item.get("is_tts", 0)
            label = 0 if is_tts == 1 else 1
        elif self.dataset_type == "garystafford":
            label_val = item.get("label", 0)
            if isinstance(label_val, str):
                label = 1 if label_val.lower() in ["real", "bonafide"] else 0
            else:
                label = int(label_val)
        
        audio_tensor = torch.tensor(audio_array, dtype=torch.float32)
        if audio_tensor.dim() > 1:
            audio_tensor = audio_tensor.mean(dim=0)
            
        if sample_rate != 16000:
            if sample_rate not in self.resamplers:
                self.resamplers[sample_rate] = T.Resample(sample_rate, 16000)
            audio_tensor = self.resamplers[sample_rate](audio_tensor)
            
        audio_tensor = pad_random(audio_tensor, 64600)
        
        return audio_tensor, label

def main():
    parser = argparse.ArgumentParser(description="Train Universal Deepfake Detector")
    parser.add_argument("--epochs", type=int, default=10, help="Number of training epochs")
    parser.add_argument("--batch_size", type=int, default=16, help="Batch size")
    args = parser.parse_args()

    os.makedirs("models/weights", exist_ok=True)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"🖥  Device: {device}")

    # 1. Load Pretrained Model (Starting point)
    print("📦 Loading pretrained AASIST model...")
    with open("config/AASIST.conf", "r") as f_json:
        config = json.loads(f_json.read())
    
    model_config = config["model_config"]
    model = Model(model_config).to(device)
    
    # We will load the original pretrained weights to avoid "forgetting" issues
    # that could arise from loading the model that overfit to just IndicTTS.
    model_path = "models/weights/AASIST.pth"
    if os.path.exists(model_path):
        model.load_state_dict(torch.load(model_path, map_location=device))
        print("✓  Pretrained weights loaded.")

    # 2. Load & Merge Datasets
    print("📥 Loading local datasets for merging...")
    
    if not os.path.exists("./local_dataset") or not os.path.exists("./local_dataset_garystafford"):
        print("❌ Error: One or both local datasets not found!")
        return

    hf_dataset_indic = load_from_disk(os.path.abspath("./local_dataset"))
    hf_dataset_english = load_from_disk(os.path.abspath("./local_dataset_garystafford"))
    
    dataset_indic = UniversalAudioDataset(hf_dataset_indic, dataset_type="indic")
    dataset_english = UniversalAudioDataset(hf_dataset_english, dataset_type="garystafford")
    
    # The magical PyTorch concatenation
    merged_dataset = ConcatDataset([dataset_indic, dataset_english])
    print(f"🔗 Merged {len(dataset_indic)} Indic samples with {len(dataset_english)} English samples.")
    print(f"📊 Total Training Dataset Size: {len(merged_dataset)} samples.")
    
    train_loader = DataLoader(
        merged_dataset, 
        batch_size=args.batch_size, 
        shuffle=True, 
        num_workers=0, 
        pin_memory=True
    )

    # 3. Setup Optimizer & Mixed Precision
    optim_config = config["optim_config"]
    optim_config["epochs"] = args.epochs
    optim_config["steps_per_epoch"] = len(train_loader)
    optimizer, scheduler = create_optimizer(model.parameters(), optim_config)
    optimizer_swa = SWA(optimizer)

    weight = torch.FloatTensor([0.1, 0.9]).to(device)
    criterion = nn.CrossEntropyLoss(weight=weight)
    scaler = torch.cuda.amp.GradScaler()
    best_loss = float('inf')

    # 4. Training Loop
    print(f"🚀 Starting Universal AMP training for {args.epochs} epochs...")
    for epoch in range(args.epochs):
        model.train()
        running_loss = 0.0
        
        pbar = tqdm(train_loader, desc=f"Epoch {epoch+1}/{args.epochs}")
        for batch_x, batch_y in pbar:
            batch_x = batch_x.to(device)
            batch_y = batch_y.to(device)
            
            with torch.cuda.amp.autocast():
                _, batch_out = model(batch_x)
                loss = criterion(batch_out, batch_y)
            
            optimizer.zero_grad()
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()
            
            running_loss += loss.item() * batch_x.size(0)
            
            if scheduler is not None and optim_config["scheduler"] in ["cosine", "keras_decay"]:
                scheduler.step()
            
            pbar.set_postfix({"loss": f"{loss.item():.4f}"})
            
        optimizer_swa.update_swa()
        
        avg_loss = running_loss / len(merged_dataset)
        print(f"📊 Epoch {epoch+1} finished. Avg Loss: {avg_loss:.4f}")
        
        if avg_loss < best_loss:
            best_loss = avg_loss
            # Save the universally tuned model
            torch.save(model.state_dict(), "models/weights/AASIST_universal_best.pth")
            print("💾 Saved new universally robust model.")
            
    if args.epochs > 0:
        print("🔄 Applying SWA weights...")
        optimizer_swa.swap_swa_sgd()
        torch.save(model.state_dict(), "models/weights/AASIST_universal_swa.pth")
        print("✅ Universal Fine-tuning complete!")

if __name__ == "__main__":
    main()
