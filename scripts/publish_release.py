"""Upload and verify all draft assets BEFORE making a release publicly downloadable."""
import hashlib
import json
import os
from pathlib import Path
import subprocess

version = os.environ["VERSION"]
tag = f"v{version}"
repository = os.environ["GITHUB_REPOSITORY"]
sha = os.environ["GITHUB_SHA"]
collection = f"repos/{repository}/releases?per_page=100"

def gh(*args):
    return subprocess.check_output(["gh", *args], text=True, encoding="utf-8")

def find_release():
    return next((release for release in json.loads(gh("api", collection)) if release["tag_name"] == tag), None)

release = find_release()
if release is None:
    gh("release", "create", tag, "--draft", "--target", sha, "--title", f"ScheduleBoard {version}", "--notes-file", "release-notes.md")
    release = find_release()
if release is None or not release["draft"] or release["target_commitish"] != sha:
    raise SystemExit("Refusing to overwrite a published release or a draft from another commit")
endpoint = f"repos/{repository}/releases/{release['id']}"

files = sorted(Path("release-assets").iterdir())
expected_names = {f"ScheduleBoard-{version}.apk", f"RELEASE-NOTES-{version}.md", "SIGNATURE.txt", "SHA256SUMS.txt"}
if {file.name for file in files} != expected_names:
    raise SystemExit("Release asset set does not match the expected APK, notes, signature and checksums")
gh("release", "upload", tag, *map(str, files), "--clobber")  # Only an unpublished, same-commit draft can be resumed.
release = json.loads(gh("api", endpoint))
assets = {item["name"]: item for item in release["assets"]}
if set(assets) != expected_names:
    raise SystemExit("Remote assets are incomplete or unexpected; leaving release as draft")
for file in files:
    asset = assets[file.name]
    if asset["size"] != file.stat().st_size or asset["size"] == 0:
        raise SystemExit("Asset size mismatch; leaving release as draft")
    digest = asset.get("digest")
    if digest and digest != "sha256:" + hashlib.sha256(file.read_bytes()).hexdigest():
        raise SystemExit("Asset digest mismatch; leaving release as draft")
gh("release", "edit", tag, "--draft=false", "--latest")
print(f"Published {tag} with verified downloadable APK and checksums.")
