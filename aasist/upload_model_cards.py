from huggingface_hub import HfApi

api = HfApi()

# Upload AASIST model card
print("Uploading AASIST model card...")
api.upload_file(
    path_or_fileobj="./model_cards/aasist_readme.md",
    path_in_repo="README.md",
    repo_id="Shauriya24/voiceshield-aasist",
    repo_type="model"
)
print("AASIST model card uploaded!")

# Upload XLM-RoBERTa model card
print("Uploading XLM-RoBERTa model card...")
api.upload_file(
    path_or_fileobj="./model_cards/xlmr_readme.md",
    path_in_repo="README.md",
    repo_id="Shauriya24/voiceshield-xlmr",
    repo_type="model"
)
print("XLM-RoBERTa model card uploaded!")

print("Done! Both model pages now have full descriptions.")
