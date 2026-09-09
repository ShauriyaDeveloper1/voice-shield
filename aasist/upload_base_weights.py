import os
from dotenv import load_dotenv
from huggingface_hub import HfApi, login

load_dotenv()
TOKEN = os.getenv("HF_TOKEN")
if not TOKEN:
    print("Error: HF_TOKEN not found in .env file")
    exit(1)

login(token=TOKEN)
api = HfApi()

username = api.whoami()["name"]
repo_id = f"{username}/voiceshield-aasist"

# Upload ALL weight files to the same repo
weights_dir = "./models/weights"
for filename in os.listdir(weights_dir):
    if filename.endswith(".pth"):
        filepath = os.path.join(weights_dir, filename)
        print(f"Uploading {filename} ({os.path.getsize(filepath) / 1024:.0f} KB)...")
        api.upload_file(
            path_or_fileobj=filepath,
            path_in_repo=filename,
            repo_id=repo_id,
            repo_type="model"
        )
        print(f"  Done: {filename}")

print(f"\nAll weights uploaded to: https://huggingface.co/{repo_id}")
