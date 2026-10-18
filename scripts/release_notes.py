#!/usr/bin/env python3
"""Validate human-readable Stable release notes before publication."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


VERSION_RE = re.compile(r"^[0-9]+(?:\.[0-9]+){3}$")
SECTIONS = {"Что нового", "Улучшения", "Исправления", "Известные проблемы"}
MAX_NOTES_CHARS = 10_000
BANNED_PLACEHOLDERS = re.compile(r"\b(?:TODO|TBD|FIXME)\b|заполнить|описать изменения", re.IGNORECASE)


def read_version(path: Path) -> str:
    versions = [
        line.split("=", 1)[1].strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip().startswith("versionName=")
    ]
    if len(versions) != 1 or not VERSION_RE.fullmatch(versions[0]):
        raise ValueError(f"Expected one valid versionName in {path}")
    return versions[0]


def validate_release_notes(root: Path, version: str) -> Path:
    if not VERSION_RE.fullmatch(version):
        raise ValueError(f"Invalid four-part release version: {version!r}")

    path = root / "docs" / "releases" / f"v{version}.md"
    if not path.is_file():
        raise ValueError(f"Missing Stable changelog: {path}")

    content = path.read_text(encoding="utf-8").strip()
    if not content or len(content) > MAX_NOTES_CHARS:
        raise ValueError(f"Changelog must contain 1–{MAX_NOTES_CHARS} characters: {path}")
    if BANNED_PLACEHOLDERS.search(content):
        raise ValueError(f"Changelog contains a placeholder: {path}")
    if "<!-- TECHNICAL_CHANGELOG -->" in content:
        raise ValueError("Technical changelog marker is reserved for CI")

    lines = [line.strip() for line in content.splitlines() if line.strip()]
    if not lines or lines[0] != f"# BeyGram {version}":
        raise ValueError(f"First heading must be '# BeyGram {version}'")

    current_section = False
    has_changes = False
    for line in lines[1:]:
        if line.startswith("## "):
            heading = line[3:].strip()
            if heading not in SECTIONS:
                raise ValueError(f"Unknown release notes section: {heading}")
            current_section = True
        elif line.startswith("- "):
            if not current_section or len(line[2:].strip()) < 12:
                raise ValueError("Change entries must be meaningful bullets under a section")
            has_changes = True

    if not has_changes:
        raise ValueError("At least one meaningful '- ' change entry is required")
    return path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["validate"])
    parser.add_argument("--version", help="Tag version without the v prefix")
    parser.add_argument("--root", type=Path, default=Path("."))
    args = parser.parse_args()

    try:
        version = read_version(args.root / "release-version.properties")
        if args.version is not None and args.version != version:
            raise ValueError(f"Requested version {args.version} != release-version.properties {version}")
        path = validate_release_notes(args.root, version)
    except (ValueError, OSError, UnicodeError) as error:
        parser.exit(1, f"::error::{error}\n")

    print(f"Validated Stable release notes: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
