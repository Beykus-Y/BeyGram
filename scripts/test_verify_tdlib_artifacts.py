"""Tests for scripts/verify_tdlib_artifacts.py."""

import hashlib
import io
import json
import tempfile
import unittest
from pathlib import Path

from verify_tdlib_artifacts import verify_manifest


def write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


class TdlibArtifactTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        write(self.root / "app" / "libs" / "tdlib-java.jar", b"jar")
        write(self.root / "app" / "src" / "main" / "jniLibs" / "arm64-v8a" / "libtdjni.so", b"elf")

    def manifest(self, **overrides):
        jar = hashlib.sha256(b"jar").hexdigest()
        elf = hashlib.sha256(b"elf").hexdigest()
        entries = [
            {"path": "app/libs/tdlib-java.jar", "size": 3, "sha256": jar},
            {"path": "app/src/main/jniLibs/arm64-v8a/libtdjni.so", "size": 3, "sha256": elf},
        ]
        for index, change in overrides.items():
            entries[int(index)].update(change)
        return {"artifacts": entries}

    def test_matching_artifacts_pass(self):
        self.assertEqual([], verify_manifest(self.manifest(), self.root))

    def test_changed_binary_is_reported(self):
        write(self.root / "app" / "src" / "main" / "jniLibs" / "arm64-v8a" / "libtdjni.so", b"ELF")
        errors = verify_manifest(self.manifest(), self.root)
        self.assertEqual(1, len(errors))
        self.assertIn("sha256", errors[0])

    def test_missing_binary_is_reported(self):
        (self.root / "app" / "libs" / "tdlib-java.jar").unlink()
        errors = verify_manifest(self.manifest(), self.root)
        self.assertEqual(1, len(errors))
        self.assertIn("missing", errors[0])

    def test_manifest_without_artifacts_is_rejected(self):
        self.assertEqual(["manifest has no artifacts"], verify_manifest({}, self.root))

    def test_recorded_size_is_enforced(self):
        errors = verify_manifest(self.manifest(**{"0": {"size": 4}}), self.root)
        self.assertEqual(1, len(errors))
        self.assertIn("size", errors[0])


if __name__ == "__main__":
    unittest.main()