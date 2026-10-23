import hashlib
import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name("publish_release.py")

class PublishReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.previous_cwd = Path.cwd()
        os.chdir(self.temp.name)
        assets = Path("release-assets")
        assets.mkdir()
        for name in ["ScheduleBoard-0.3.0.apk", "RELEASE-NOTES-0.3.0.md", "SIGNATURE.txt", "SHA256SUMS.txt"]:
            (assets / name).write_bytes(name.encode("utf-8"))
        self.release = {"id": 100, "tag_name": "v0.3.0", "target_commitish": "fixture-sha", "draft": True, "assets": []}
        self.remote_assets = [{"name": file.name, "size": file.stat().st_size,
            "digest": "sha256:" + hashlib.sha256(file.read_bytes()).hexdigest()} for file in assets.iterdir()]
        self.commands = []
        self.created = False
        self.existing = True
        self.stale_reads = 0

    def tearDown(self):
        os.chdir(self.previous_cwd)
        self.temp.cleanup()

    def fake_gh(self, command, **kwargs):
        self.commands.append(command)
        if command[1] == "api":
            if "?per_page=100" in command[2]:
                if self.created and self.stale_reads > 0:
                    self.stale_reads -= 1
                    return "[]"
                return json.dumps([self.release] if self.existing or self.created else [])
            return json.dumps(dict(self.release, assets=self.remote_assets))
        if command[1:3] == ["release", "create"]:
            self.created = True
        return ""

    def run_publisher(self):
        with patch.dict(os.environ, {"VERSION": "0.3.0", "GITHUB_REPOSITORY": "fixture/repo", "GITHUB_SHA": "fixture-sha"}), \
                patch("subprocess.check_output", side_effect=self.fake_gh), patch("time.sleep"):
            with contextlib.redirect_stdout(io.StringIO()):
                runpy.run_path(str(SCRIPT), run_name="__main__")

    def published(self):
        return any(command[1:3] == ["release", "edit"] for command in self.commands)

    def test_published_release_can_never_be_overwritten(self):
        self.release["draft"] = False
        with self.assertRaises(SystemExit):
            self.run_publisher()
        self.assertFalse(any(command[1:3] == ["release", "upload"] for command in self.commands))

    def test_draft_from_other_commit_cannot_be_modified(self):
        self.release["target_commitish"] = "other-sha"
        with self.assertRaises(SystemExit):
            self.run_publisher()
        self.assertFalse(self.published())

    def test_new_release_is_published_only_after_upload_and_digest_verification(self):
        self.existing = False
        self.run_publisher()
        self.assertTrue(self.created)
        self.assertTrue(self.published())
        create = next(command for command in self.commands if command[1:3] == ["release", "create"])
        self.assertIn("--draft", create)
        upload_index = next(i for i, command in enumerate(self.commands) if command[1:3] == ["release", "upload"])
        self.assertEqual("api", self.commands[upload_index + 1][1])
        self.assertEqual(["release", "edit"], self.commands[upload_index + 2][1:3])

    def test_same_commit_draft_can_resume(self):
        self.run_publisher()
        self.assertFalse(self.created)
        self.assertTrue(self.published())

    def test_new_draft_readback_retries_temporary_visibility_delay(self):
        self.existing = False
        self.stale_reads = 2
        self.run_publisher()
        self.assertTrue(self.published())
        self.assertEqual(0, self.stale_reads)

    def test_missing_remote_asset_leaves_release_as_draft(self):
        self.remote_assets.pop()
        with self.assertRaises(SystemExit):
            self.run_publisher()
        self.assertFalse(self.published())

    def test_bad_digest_leaves_release_as_draft(self):
        self.remote_assets[0]["digest"] = "sha256:incorrect"
        with self.assertRaises(SystemExit):
            self.run_publisher()
        self.assertFalse(self.published())

if __name__ == "__main__":
    unittest.main()
