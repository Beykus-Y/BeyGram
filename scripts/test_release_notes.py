"""Regression tests for Stable release notes validation."""

import tempfile
import unittest
from pathlib import Path

from release_notes import read_version, validate_release_notes


class ReleaseNotesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "docs" / "releases").mkdir(parents=True)
        (self.root / "release-version.properties").write_text(
            "versionName=0.0.0.7\n", encoding="utf-8"
        )

    def write_notes(self, value):
        (self.root / "docs" / "releases" / "v0.0.0.7.md").write_text(
            value, encoding="utf-8"
        )

    def test_valid_russian_changelog(self):
        self.write_notes(
            "# BeyGram 0.0.0.7\n\n## Исправления\n"
            "- Исправлена ошибка загрузки голосовых сообщений.\n"
        )
        self.assertEqual(read_version(self.root / "release-version.properties"), "0.0.0.7")
        self.assertEqual(validate_release_notes(self.root, "0.0.0.7").name, "v0.0.0.7.md")

    def test_missing_file_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Missing Stable changelog"):
            validate_release_notes(self.root, "0.0.0.7")

    def test_wrong_version_heading_is_rejected(self):
        self.write_notes("# BeyGram 0.0.0.8\n## Исправления\n- Исправлена ошибка загрузки.\n")
        with self.assertRaisesRegex(ValueError, "First heading"):
            validate_release_notes(self.root, "0.0.0.7")

    def test_placeholder_is_rejected(self):
        self.write_notes("# BeyGram 0.0.0.7\n## Улучшения\n- TODO: заполнить позже\n")
        with self.assertRaisesRegex(ValueError, "placeholder"):
            validate_release_notes(self.root, "0.0.0.7")

    def test_empty_changelog_is_rejected(self):
        self.write_notes("# BeyGram 0.0.0.7\n## Исправления\n")
        with self.assertRaisesRegex(ValueError, "meaningful"):
            validate_release_notes(self.root, "0.0.0.7")

    def test_technical_marker_is_reserved(self):
        self.write_notes(
            "# BeyGram 0.0.0.7\n## Улучшения\n"
            "- Добавлено описание обновления.\n<!-- TECHNICAL_CHANGELOG -->\n"
        )
        with self.assertRaisesRegex(ValueError, "reserved"):
            validate_release_notes(self.root, "0.0.0.7")


if __name__ == "__main__":
    unittest.main()
