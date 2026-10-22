import unittest
from release_metadata import read_version, validate_increase, release_notes

class ReleaseMetadataTest(unittest.TestCase):
    def test_parses_single_version(self):
        self.assertEqual(("0.3.0", 5), read_version('versionCode = 5\nversionName = "0.3.0"'))

    def test_rejects_missing_or_ambiguous_version(self):
        for text in ['versionCode = 5', 'versionCode = 0\nversionName = "0.3.0"',
                     'versionCode = 5\nversionName = "0.3.0"\nversionName = "0.4.0"']:
            with self.assertRaises(ValueError):
                read_version(text)

    def test_both_identifiers_must_increase(self):
        for version, code in [("0.2.9", 6), ("0.3.0", 6), ("0.4.0", 5), ("0.4.0", 4)]:
            with self.assertRaises(ValueError):
                validate_increase(version, code, "0.3.0", 5)

    def test_compares_versions_numerically(self):
        validate_increase("0.10.0", 6, "0.9.0", 5)

    def test_only_current_version_notes_are_published(self):
        notes = release_notes("0.3.0", "# Changes\n\n## 0.3.0 · date\n\n- New feature\n\n## 0.2.0\n\n- Old feature\n")
        self.assertIn("New feature", notes)
        self.assertNotIn("Old feature", notes)

    def test_missing_changelog_version_blocks_release(self):
        with self.assertRaises(ValueError):
            release_notes("0.3.0", "## 0.2.0\n- Old feature\n")

if __name__ == "__main__":
    unittest.main()
