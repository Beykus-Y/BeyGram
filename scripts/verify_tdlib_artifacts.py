#!/usr/bin/env python3
"""Checks the committed TDLib binaries against docs/tdlib-artifacts.json.

The record is the only provenance the repository has for libtdjni.so and tdlib-java.jar. A binary that changes
without the manifest changing is either an unreviewed replacement or a corrupted checkout; CI must stop on both
instead of silently shipping a native library nobody can reproduce.

Usage:
    python3 scripts/verify_tdlib_artifacts.py [--manifest docs/tdlib-artifacts.json] [--root .]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_manifest(manifest: dict, root: Path) -> list[str]:
    """Returns one human-readable error per mismatch; an empty list means every artifact matches."""
    errors: list[str] = []
    artifacts = manifest.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        return ["manifest has no artifacts"]
    for artifact in artifacts:
        relative = artifact.get("path")
        if not isinstance(relative, str) or not relative:
            errors.append("artifact without a path")
            continue
        path = root / relative
        if not path.is_file():
            errors.append(f"{relative}: file is missing")
            continue
        expected_size = artifact.get("size")
        if isinstance(expected_size, int) and path.stat().st_size != expected_size:
            errors.append(f"{relative}: size {path.stat().st_size} != recorded {expected_size}")
        expected_sha = artifact.get("sha256")
        if not isinstance(expected_sha, str) or not expected_sha:
            errors.append(f"{relative}: manifest has no sha256")
            continue
        actual = sha256_of(path)
        if actual != expected_sha:
            errors.append(f"{relative}: sha256 {actual} != recorded {expected_sha}")
    return errors


def load_manifest(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", default="docs/tdlib-artifacts.json")
    parser.add_argument("--root", default=".")
    args = parser.parse_args(argv)

    manifest_path = Path(args.manifest)
    if not manifest_path.is_file():
        print(f"error: manifest {manifest_path} is missing", file=sys.stderr)
        return 1
    try:
        manifest = load_manifest(manifest_path)
    except (OSError, json.JSONDecodeError) as error:
        print(f"error: cannot read {manifest_path}: {error}", file=sys.stderr)
        return 1

    errors = verify_manifest(manifest, Path(args.root))
    if errors:
        for error in errors:
            print(f"error: {error}", file=sys.stderr)
        print("TDLib artifacts do not match docs/tdlib-artifacts.json", file=sys.stderr)
        return 1

    names = ", ".join(sorted(os.path.basename(item["path"]) for item in manifest["artifacts"]))
    print(f"TDLib artifacts match the recorded sha256 and size: {names}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))