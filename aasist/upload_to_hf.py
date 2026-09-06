import os
from dotenv import load_dotenv
from huggingface_hub import HfApi, login

# Load token from .env file
load_dotenv()
TOKEN = os.getenv("HF_TOKEN")

if not TOKEN:
    print("Error: HF_TOKEN not found in .env file")
    exit(1)

login(token=TOKEN)
api = HfApi()

# Get username
user_info = api.whoami()
username = user_info["name"]
print(f"Logged in as: {username}")

repo_xlmr = f"{username}/voiceshield-xlmr"
repo_aasist = f"{username}/voiceshield-aasist"

# Upload XLM-RoBERTa
xlmr_path = "./models/xlm-roberta-finetuned"
if os.path.exists(xlmr_path):
    print(f"\nUploading XLM-RoBERTa to '{repo_xlmr}'...")
    api.create_repo(repo_id=repo_xlmr, repo_type="model", exist_ok=True)
    api.upload_folder(folder_path=xlmr_path, repo_id=repo_xlmr, repo_type="model")
    print(f"Done: https://huggingface.co/{repo_xlmr}")
else:
    print(f"Not found: {xlmr_path}")

# Upload AASIST weights
aasist_weights = "./models/weights/AASIST_universal_best.pth"
if os.path.exists(aasist_weights):
    print(f"\nUploading AASIST weights to '{repo_aasist}'...")
    api.create_repo(repo_id=repo_aasist, repo_type="model", exist_ok=True)
    api.upload_file(
        path_or_fileobj=aasist_weights,
        path_in_repo="AASIST_universal_best.pth",
        repo_id=repo_aasist,
        repo_type="model"
    )
    print(f"Done: https://huggingface.co/{repo_aasist}")
else:
    print(f"Not found: {aasist_weights}")

print("\nAll uploads finished!")
