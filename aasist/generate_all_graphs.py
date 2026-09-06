"""
VoiceShield - Comprehensive Model Graph Generator
===================================================
Generates all evaluation & architecture graphs for:
  1) XLM-R  (NLP text classifier)  - confusion matrix, classification report,
                                      per-class bar chart, prediction distribution
  2) AASIST (Audio deepfake detector) - architecture layer chart, parameter
                                         distribution, sample inference output
"""

import os, json, torch, warnings
import numpy as np
import matplotlib
matplotlib.use("Agg")                       # headless backend
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.metrics import (confusion_matrix, classification_report,
                             accuracy_score, f1_score, precision_score,
                             recall_score)
from tqdm import tqdm

warnings.filterwarnings("ignore")
device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# ---------- style setup --------------------------------------------------- #
plt.rcParams.update({
    "figure.facecolor": "#0f0f0f",
    "axes.facecolor":   "#1a1a2e",
    "axes.edgecolor":   "#444",
    "axes.labelcolor":  "#eee",
    "text.color":       "#eee",
    "xtick.color":      "#ccc",
    "ytick.color":      "#ccc",
    "font.family":      "sans-serif",
    "font.size":        11,
})
PALETTE = ["#00d2ff", "#7b2ff7", "#ff6b6b", "#feca57", "#1dd1a1"]

os.makedirs("graphs", exist_ok=True)

# ========================================================================== #
#                              XLM-R  GRAPHS                                 #
# ========================================================================== #

def run_xlmr_evaluation():
    from transformers import AutoTokenizer, AutoModelForSequenceClassification
    from datasets import load_from_disk

    model_path = os.path.abspath("./models/xlm-roberta-finetuned")
    if not os.path.exists(model_path):
        print("[SKIP] XLM-R model not found.")
        return

    dataset_path = os.path.abspath("./data/voiceshield_nlp_dataset")
    if not os.path.exists(dataset_path):
        print("[SKIP] NLP dataset not found.")
        return

    print("[XLM-R] Loading model & tokenizer ...")
    model = AutoModelForSequenceClassification.from_pretrained(model_path, num_labels=3).to(device)
    tokenizer = AutoTokenizer.from_pretrained(model_path)
    model.eval()

    dataset = load_from_disk(dataset_path)
    val_ds  = dataset["validation"]
    val_len = min(1000, len(val_ds))
    val_ds  = val_ds.select(range(val_len))

    CLASS_NAMES = ["Normal", "Suspicious", "Social Eng."]

    y_true, y_pred, y_probs = [], [], []
    print(f"[XLM-R] Evaluating on {val_len} samples ...")
    for item in tqdm(val_ds, desc="XLM-R"):
        inputs = tokenizer(item["text"], padding="max_length", truncation=True,
                           max_length=128, return_tensors="pt")
        inputs = {k: v.to(device) for k, v in inputs.items()}
        with torch.no_grad():
            logits = model(**inputs).logits
            probs  = torch.softmax(logits, dim=1).cpu().numpy()[0]
        y_true.append(item["label"])
        y_pred.append(int(np.argmax(probs)))
        y_probs.append(probs)

    y_true  = np.array(y_true)
    y_pred  = np.array(y_pred)
    y_probs = np.array(y_probs)

    # --- 1. Confusion Matrix ---------------------------------------------- #
    cm = confusion_matrix(y_true, y_pred)
    fig, ax = plt.subplots(figsize=(8, 6))
    sns.heatmap(cm, annot=True, fmt="d", cmap="cividis",
                xticklabels=CLASS_NAMES, yticklabels=CLASS_NAMES,
                linewidths=.8, linecolor="#333", ax=ax,
                annot_kws={"size": 16, "weight": "bold"})
    ax.set_title("XLM-R  Confusion Matrix", fontsize=16, fontweight="bold", pad=12)
    ax.set_ylabel("True Label", fontsize=13)
    ax.set_xlabel("Predicted Label", fontsize=13)
    fig.tight_layout()
    fig.savefig("graphs/XLMR_confusion_matrix.png", dpi=200)
    plt.close(fig)
    print("  -> Saved graphs/XLMR_confusion_matrix.png")

    # --- 2. Classification Report Heatmap --------------------------------- #
    report = classification_report(y_true, y_pred, target_names=CLASS_NAMES,
                                   output_dict=True, zero_division=0)
    metrics_df_data = {m: [] for m in ["precision", "recall", "f1-score"]}
    for cls in CLASS_NAMES:
        for m in metrics_df_data:
            metrics_df_data[m].append(report[cls][m])

    import pandas as pd
    df = pd.DataFrame(metrics_df_data, index=CLASS_NAMES)

    fig, ax = plt.subplots(figsize=(8, 4))
    sns.heatmap(df, annot=True, fmt=".3f", cmap="YlGnBu", vmin=0, vmax=1,
                linewidths=.8, linecolor="#333", ax=ax,
                annot_kws={"size": 14, "weight": "bold"})
    ax.set_title("XLM-R  Classification Report", fontsize=16, fontweight="bold", pad=12)
    fig.tight_layout()
    fig.savefig("graphs/XLMR_classification_report.png", dpi=200)
    plt.close(fig)
    print("  -> Saved graphs/XLMR_classification_report.png")

    # --- 3. Per-Class Metrics Bar Chart ----------------------------------- #
    acc = accuracy_score(y_true, y_pred)
    f1  = f1_score(y_true, y_pred, average="weighted", zero_division=0)
    prec = precision_score(y_true, y_pred, average="weighted", zero_division=0)
    rec  = recall_score(y_true, y_pred, average="weighted", zero_division=0)

    fig, ax = plt.subplots(figsize=(9, 5))
    metrics_names = ["Accuracy", "Precision", "Recall", "F1 Score"]
    metrics_vals  = [acc, prec, rec, f1]
    bars = ax.barh(metrics_names, metrics_vals, color=PALETTE[:4], edgecolor="#222", height=0.55)
    for bar, val in zip(bars, metrics_vals):
        ax.text(bar.get_width() + 0.01, bar.get_y() + bar.get_height()/2,
                f"{val*100:.1f}%", va="center", fontsize=13, fontweight="bold")
    ax.set_xlim(0, 1.15)
    ax.set_title("XLM-R  Overall Metrics", fontsize=16, fontweight="bold", pad=12)
    ax.axvline(x=1.0, color="#555", linestyle="--", linewidth=0.8)
    fig.tight_layout()
    fig.savefig("graphs/XLMR_overall_metrics.png", dpi=200)
    plt.close(fig)
    print("  -> Saved graphs/XLMR_overall_metrics.png")

    # --- 4. Prediction Probability Distribution --------------------------- #
    fig, axes = plt.subplots(1, 3, figsize=(14, 4), sharey=True)
    for i, cls in enumerate(CLASS_NAMES):
        axes[i].hist(y_probs[:, i], bins=30, color=PALETTE[i], edgecolor="#222", alpha=0.85)
        axes[i].set_title(cls, fontsize=13, fontweight="bold")
        axes[i].set_xlabel("Probability")
        if i == 0:
            axes[i].set_ylabel("Count")
    fig.suptitle("XLM-R  Prediction Probability Distribution", fontsize=15,
                 fontweight="bold", y=1.02)
    fig.tight_layout()
    fig.savefig("graphs/XLMR_probability_distribution.png", dpi=200, bbox_inches="tight")
    plt.close(fig)
    print("  -> Saved graphs/XLMR_probability_distribution.png")


# ========================================================================== #
#                             AASIST  GRAPHS                                 #
# ========================================================================== #

def run_aasist_graphs():
    from models.AASIST import Model

    with open("config/AASIST.conf", "r") as f:
        config = json.loads(f.read())

    model_config = config["model_config"]
    model = Model(model_config)

    best_path = "models/weights/AASIST_universal_best.pth"
    base_path = "models/weights/AASIST.pth"
    used_path = best_path if os.path.exists(best_path) else base_path

    if os.path.exists(used_path):
        model.load_state_dict(torch.load(used_path, map_location="cpu"))
        print(f"[AASIST] Loaded weights: {used_path}")
    model.eval()

    # --- 1. Layer Parameter Count Bar Chart ------------------------------- #
    layer_names, layer_params = [], []
    for name, param in model.named_parameters():
        short = name.split(".")[0]          # top-level module name
        if short not in layer_names:
            layer_names.append(short)
            layer_params.append(param.numel())
        else:
            idx = layer_names.index(short)
            layer_params[idx] += param.numel()

    fig, ax = plt.subplots(figsize=(10, max(5, len(layer_names)*0.45)))
    colors = [PALETTE[i % len(PALETTE)] for i in range(len(layer_names))]
    bars = ax.barh(layer_names, layer_params, color=colors, edgecolor="#222", height=0.6)
    for bar, val in zip(bars, layer_params):
        ax.text(bar.get_width() + max(layer_params)*0.01,
                bar.get_y() + bar.get_height()/2,
                f"{val:,}", va="center", fontsize=9, fontweight="bold")
    ax.set_xlabel("Number of Parameters")
    ax.set_title("AASIST  Layer-wise Parameter Count", fontsize=16, fontweight="bold", pad=12)
    ax.invert_yaxis()
    fig.tight_layout()
    fig.savefig("graphs/AASIST_layer_parameters.png", dpi=200)
    plt.close(fig)
    print("  -> Saved graphs/AASIST_layer_parameters.png")

    total_params = sum(p.numel() for p in model.parameters())
    trainable   = sum(p.numel() for p in model.parameters() if p.requires_grad)

    # --- 2. Parameter Weight Distribution (Histogram) --------------------- #
    all_weights = []
    for p in model.parameters():
        all_weights.append(p.data.cpu().numpy().flatten())
    all_weights = np.concatenate(all_weights)

    fig, ax = plt.subplots(figsize=(9, 5))
    ax.hist(all_weights, bins=120, color="#7b2ff7", edgecolor="#222", alpha=0.85)
    ax.set_title("AASIST  Weight Distribution", fontsize=16, fontweight="bold", pad=12)
    ax.set_xlabel("Weight Value")
    ax.set_ylabel("Frequency")
    ax.axvline(0, color="#ff6b6b", linestyle="--", linewidth=1)
    textstr = f"Total params: {total_params:,}\nTrainable: {trainable:,}\nMean: {all_weights.mean():.5f}\nStd: {all_weights.std():.4f}"
    props = dict(boxstyle="round,pad=0.4", facecolor="#222", edgecolor="#555", alpha=0.85)
    ax.text(0.97, 0.95, textstr, transform=ax.transAxes, fontsize=10,
            verticalalignment="top", horizontalalignment="right", bbox=props)
    fig.tight_layout()
    fig.savefig("graphs/AASIST_weight_distribution.png", dpi=200)
    plt.close(fig)
    print("  -> Saved graphs/AASIST_weight_distribution.png")

    # --- 3. Architecture Summary Block Diagram ---------------------------- #
    arch_blocks = [
        ("RawNet2\nSincConv", "Sinc filters\n128 channels", "#00d2ff"),
        ("Residual Blocks\n(conv1d + BN + SELU)", "Filters: 1->32->64", "#7b2ff7"),
        ("Graph Attention\nNetwork (GAT)", "dims: 64 -> 32\n4 pooling layers", "#ff6b6b"),
        ("Heterogeneous\nGraph Attention", "Spectral + Temporal\nfusion", "#feca57"),
        ("Readout +\nClassifier", "FC -> 2 classes\n(bonafide / spoof)", "#1dd1a1"),
    ]

    fig, ax = plt.subplots(figsize=(14, 4))
    ax.set_xlim(-0.5, len(arch_blocks)*2.5)
    ax.set_ylim(-1, 2.5)
    ax.axis("off")
    ax.set_title("AASIST  Architecture Overview", fontsize=18, fontweight="bold", pad=20)

    for i, (title, desc, color) in enumerate(arch_blocks):
        x = i * 2.5
        rect = plt.Rectangle((x, 0), 2, 2, linewidth=2, edgecolor=color,
                              facecolor=color+"22", clip_on=False, zorder=2)
        ax.add_patch(rect)
        ax.text(x+1, 1.35, title, ha="center", va="center", fontsize=11,
                fontweight="bold", color=color)
        ax.text(x+1, 0.6, desc, ha="center", va="center", fontsize=9, color="#ccc")
        if i < len(arch_blocks) - 1:
            ax.annotate("", xy=(x+2.35, 1), xytext=(x+2.05, 1),
                        arrowprops=dict(arrowstyle="->", color="#888", lw=2))

    fig.tight_layout()
    fig.savefig("graphs/AASIST_architecture_overview.png", dpi=200, bbox_inches="tight")
    plt.close(fig)
    print("  -> Saved graphs/AASIST_architecture_overview.png")

    # --- 4. Sample inference on sample_test.wav (if exists) --------------- #
    sample_wav = "sample_test.wav"
    if os.path.exists(sample_wav):
        from scipy.io import wavfile
        sr, audio_np = wavfile.read(sample_wav)
        # Normalize to float32 [-1, 1]
        if audio_np.dtype == np.int16:
            audio_np = audio_np.astype(np.float32) / 32768.0
        elif audio_np.dtype == np.int32:
            audio_np = audio_np.astype(np.float32) / 2147483648.0
        elif audio_np.dtype != np.float32:
            audio_np = audio_np.astype(np.float32)
        # Handle stereo
        if audio_np.ndim > 1:
            audio_np = audio_np.mean(axis=1)
        waveform = torch.tensor(audio_np, dtype=torch.float32).unsqueeze(0)
        if sr != 16000:
            import torchaudio.transforms as T_resample
            resampler = T_resample.Resample(sr, 16000)
            waveform = resampler(waveform)

        length = waveform.shape[-1]
        if length < 64600:
            waveform = torch.nn.functional.pad(waveform, (0, 64600 - length))
        else:
            waveform = waveform[..., :64600]

        with torch.no_grad():
            _, logits = model(waveform)
            probs = torch.softmax(logits, dim=1).cpu().numpy()[0]

        fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(12, 4),
                                        gridspec_kw={"width_ratios": [2, 1]})
        # waveform plot
        t = np.linspace(0, waveform.shape[-1]/16000, waveform.shape[-1])
        ax1.plot(t, waveform.squeeze().numpy(), color="#00d2ff", linewidth=0.4)
        ax1.set_title("Input Waveform (sample_test.wav)", fontsize=13, fontweight="bold")
        ax1.set_xlabel("Time (s)")
        ax1.set_ylabel("Amplitude")
        ax1.fill_between(t, waveform.squeeze().numpy(), alpha=0.15, color="#00d2ff")

        # prediction bar
        labels = ["Spoof (0)", "Bonafide (1)"]
        colors_bar = ["#ff6b6b", "#1dd1a1"]
        bars = ax2.barh(labels, probs, color=colors_bar, edgecolor="#222", height=0.5)
        for bar, val in zip(bars, probs):
            ax2.text(bar.get_width() + 0.02, bar.get_y() + bar.get_height()/2,
                     f"{val*100:.1f}%", va="center", fontsize=13, fontweight="bold")
        ax2.set_xlim(0, 1.3)
        ax2.set_title("Prediction", fontsize=13, fontweight="bold")

        pred_label = "BONAFIDE" if np.argmax(probs) == 1 else "DEEPFAKE"
        pred_color = "#1dd1a1" if np.argmax(probs) == 1 else "#ff6b6b"
        fig.suptitle(f"AASIST Inference Result:  {pred_label}", fontsize=16,
                     fontweight="bold", color=pred_color, y=1.02)
        fig.tight_layout()
        fig.savefig("graphs/AASIST_sample_inference.png", dpi=200, bbox_inches="tight")
        plt.close(fig)
        print("  -> Saved graphs/AASIST_sample_inference.png")
    else:
        print("  [SKIP] sample_test.wav not found, skipping inference graph.")


# ========================================================================== #
if __name__ == "__main__":
    print("=" * 60)
    print("  VoiceShield  -  Graph Generator")
    print("=" * 60)
    run_xlmr_evaluation()
    print()
    run_aasist_graphs()
    print("\nDone! All graphs saved to ./graphs/")
