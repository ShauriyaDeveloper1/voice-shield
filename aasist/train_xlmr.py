import os
import torch
import numpy as np
from datasets import load_from_disk
from transformers import (
    AutoTokenizer,
    AutoModelForSequenceClassification,
    TrainingArguments,
    Trainer
)
from sklearn.metrics import accuracy_score, f1_score

def compute_metrics(eval_pred):
    logits, labels = eval_pred
    predictions = np.argmax(logits, axis=-1)
    acc = accuracy_score(labels, predictions)
    f1 = f1_score(labels, predictions, average='weighted')
    return {'accuracy': acc, 'f1': f1}

def main():
    print("⏳ Loading Dataset...")
    dataset = load_from_disk('./data/voiceshield_nlp_dataset')
    
    model_path = os.path.abspath('./models/xlm-roberta-finetuned')
    print(f"📦 Loading Tokenizer from {model_path}...")
    tokenizer = AutoTokenizer.from_pretrained(model_path)
    
    def tokenize_function(examples):
        return tokenizer(examples["text"], padding="max_length", truncation=True, max_length=128)
        
    print("✂️  Tokenizing Dataset...")
    tokenized_datasets = dataset.map(tokenize_function, batched=True)
    
    print(f"📦 Loading Model from {model_path}...")
    # 0: Normal, 1: Suspicious, 2: Social Engineering
    model = AutoModelForSequenceClassification.from_pretrained(model_path, num_labels=3)
    
    out_dir = './models/xlm-roberta-finetuned'
    
    # Fast training parameters
    training_args = TrainingArguments(
        output_dir=out_dir,
        eval_strategy="epoch",
        save_strategy="no",
        learning_rate=2e-5,
        per_device_train_batch_size=32,
        per_device_eval_batch_size=32,
        num_train_epochs=2,
        weight_decay=0.01,
        fp16=torch.cuda.is_available(),
        logging_steps=100,
        report_to="none"
    )
    
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=tokenized_datasets["train"],
        eval_dataset=tokenized_datasets["validation"],
        compute_metrics=compute_metrics
    )
    
    print("🚀 Starting Fine-tuning...")
    trainer.train()
    
    print(f"💾 Saving Best Model to {out_dir}...")
    trainer.save_model(out_dir)
    tokenizer.save_pretrained(out_dir)
    
    print("✅ Fine-tuning Complete!")

if __name__ == '__main__':
    main()
