"""
VoiceShield — Universal Model Evaluation Script
=============================================
Evaluates the fully merged "universal" AASIST model on the local English dataset.
Calculates Accuracy, F1 Score, and EER.

Usage:
    python evaluate_universal.py
"""

import argparse
import json
import os
import torch
import numpy as np
import torchaudio.transforms as T
from datasets import load_from_disk
from torch.utils.data import Dataset, DataLoader
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score
from tqdm import tqdm

from models.AASIST import Model
from evaluation import compute_eer

# Enable fast GPU execution
torch.backends.cudnn.benchmark = True

def pad_random(audio_tensor, target_length=64600):
    length = audio_tensor.size(-1)
    if length > target_length:
        return audio_tensor[..., :target_length]
    elif length < target_length:
        padding = target_length - length
        return torch.nn.functional.pad(audio_tensor, (0, padding), "constant", 0)
    return audio_tensor

class UniversalEvalDataset(Dataset):
    def __init__(self, hf_dataset, dataset_type="garystafford"):
        self.dataset = hf_dataset
        self.dataset_type = dataset_type
        self.resamplers = {}

    def __len__(self):
        return len(self.dataset)

    def __getitem__(self, idx):
        item = self.dataset[idx]
        audio_array = item["audio"]["array"]
        sample_rate = item["audio"]["sampling_rate"]
        
        if self.dataset_type == "garystafford":
            label_val = item.get("label", 0)
            if isinstance(label_val, str):
                label = 1 if label_val.lower() in ["real", "bonafide"] else 0
            else:
                label = int(label_val)
        else:
            is_tts = item.get("is_tts", 0)
            label = 0 if is_tts == 1 else 1
            
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
    parser = argparse.ArgumentParser(description="Evaluate Universal AASIST Model")
    parser.add_argument("--batch_size", type=int, default=32, help="Batch size")
    args = parser.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"🖥  Device: {device}")

    # Load Model
    print("📦 Loading Universal AASIST model...")
    with open("config/AASIST.conf", "r") as f_json:
        config = json.loads(f_json.read())
    
    model = Model(config["model_config"]).to(device)
    
    model_path = "models/weights/AASIST_universal_best.pth"
    if not os.path.exists(model_path):
        print(f"❌ Error: Model not found at {model_path}. Did you run train_merged.py?")
        return

    model.load_state_dict(torch.load(model_path, map_location=device))
    model.eval()
    print("✓  Model loaded successfully!")

    # Load Datasets from Local Disk
    print("📥 Loading both local datasets for overall evaluation...")
    dir_indic = os.path.abspath("./local_dataset")
    dir_eng = os.path.abspath("./local_dataset_garystafford")
    
    if not os.path.exists(dir_indic) or not os.path.exists(dir_eng):
        print("❌ Error: One or both local datasets not found!")
        return

    # Load datasets
    ds_indic_full = load_from_disk(dir_indic)
    ds_eng_full = load_from_disk(dir_eng)
    
    # Grab a 10% random slice of Indic dataset (3,111 samples) to balance with English (1,866 samples)
    print("✂️  Slicing 10% of IndicTTS dataset to balance evaluation time...")
    ds_indic = ds_indic_full.train_test_split(test_size=0.1, seed=42)["test"]
    ds_eng = ds_eng_full
    
    dataset_indic = UniversalEvalDataset(ds_indic, dataset_type="indic")
    dataset_eng = UniversalEvalDataset(ds_eng, dataset_type="garystafford")
    
    from torch.utils.data import ConcatDataset
    merged_eval_dataset = ConcatDataset([dataset_indic, dataset_eng])
    
    print(f"📊 Merged {len(dataset_indic)} Indic + {len(dataset_eng)} English = {len(merged_eval_dataset)} Total Samples")
    eval_loader = DataLoader(merged_eval_dataset, batch_size=args.batch_size, shuffle=False, num_workers=0)

    y_true = []
    y_scores = []
    y_pred = []
    
    print("🎤 Evaluating OVERALL generalization on merged data...")
    for batch_x, batch_y in tqdm(eval_loader, desc="Testing Model"):
        batch_x = batch_x.to(device)
        
        with torch.no_grad():
            with torch.cuda.amp.autocast():
                _, logits = model(batch_x)
            
            probs = torch.softmax(logits, dim=1)
            bonafide_probs = probs[:, 1].cpu().numpy()
            preds = torch.argmax(logits, dim=1).cpu().numpy()
            
            y_scores.extend(bonafide_probs)
            y_pred.extend(preds)
            y_true.extend(batch_y.numpy())

    accuracy = accuracy_score(y_true, y_pred)
    f1 = f1_score(y_true, y_pred, zero_division=0)
    precision = precision_score(y_true, y_pred, zero_division=0)
    recall = recall_score(y_true, y_pred, zero_division=0)
    
    y_true_np = np.array(y_true)
    y_scores_np = np.array(y_scores)
    
    target_scores = y_scores_np[y_true_np == 1]
    nontarget_scores = y_scores_np[y_true_np == 0]
    
    eer = 0.0
    if len(target_scores) > 0 and len(nontarget_scores) > 0:
        eer, _ = compute_eer(target_scores, nontarget_scores)
        eer_percent = eer * 100
    else:
        eer_percent = -1

    print("\n============================================================")
    print("  📊 OVERALL Model Evaluation Results (Indic + English)")
    print("============================================================")
    print(f"Total Samples Tested: {len(y_true)}")
    print("------------------------------------------------------------")
    print(f"Accuracy     : {accuracy * 100:.2f}%")
    print(f"Precision    : {precision:.4f}")
    print(f"Recall       : {recall:.4f}")
    print(f"F1 Score     : {f1:.4f}")
    if eer_percent >= 0:
        print(f"EER          : {eer_percent:.2f}%")
    else:
        print("EER          : Not enough classes to compute.")
    print("============================================================")

if __name__ == "__main__":
    main()
