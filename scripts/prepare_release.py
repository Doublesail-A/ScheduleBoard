"""Validate immutable versioned releases. Runs only after tests pass on GitHub."""
import json
import os
from pathlib import Path
import re
import subprocess
from release_metadata import read_version, validate_increase, release_notes

root = Path(__file__).resolve().parents[1]
os.chdir(root)
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
version, code = read_version(gradle)
tag = f"v{version}"
ref = os.environ.get("GITHUB_REF", "")
if ref.startswith("refs/tags/") and ref != f"refs/tags/{tag}":
    raise SystemExit("Tag must match app versionName")
if ref and ref != "refs/heads/main" and not ref.startswith("refs/tags/"):
    raise SystemExit("Releases must originate from main or a matching version tag")

def git(*args):
    return subprocess.check_output(["git", *args], text=True, encoding="utf-8").strip()

release = subprocess.run(["gh", "api", f"repos/{os.environ['GITHUB_REPOSITORY']}/releases?per_page=100"],
                         capture_output=True, text=True, encoding="utf-8", check=True)
published = [item for item in json.loads(release.stdout) if not item["draft"]]
existing = next((item for item in published if item["tag_name"] == tag), None)
publish = existing is None
if existing:
    tagged = git("rev-parse", f"{tag}^{{commit}}")
    changed = git("diff", "--name-only", tagged, "HEAD", "--", "app", "gradle", "gradle.properties", "build.gradle.kts", "settings.gradle.kts")
    if changed:
        raise SystemExit("Application changed after this version was released. Increase versionName AND versionCode before publishing.")
    print(f"{tag} already published; no application changes, skipping release.")
else:
    for item in published:
        previous = item["tag_name"]
        if not re.fullmatch(r"v\d+\.\d+\.\d+", previous):
            continue
        previous_gradle = git("show", f"{previous}:app/build.gradle.kts")
        _, previous_code = read_version(previous_gradle)
        validate_increase(version, code, previous[1:], previous_code)
    if subprocess.run(["git", "rev-parse", "--verify", f"refs/tags/{tag}"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        if git("rev-parse", f"{tag}^{{commit}}") != git("rev-parse", "HEAD"):
            raise SystemExit("Existing version tag points to a different commit; refusing to move it.")

changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")
notes = release_notes(version, changelog)
(root / "release-notes.md").write_text(notes, encoding="utf-8", newline="\n")
with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
    output.write(f"version={version}\npublish={str(publish).lower()}\n")
