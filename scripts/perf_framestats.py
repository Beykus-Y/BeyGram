#!/usr/bin/env python3
"""Frame statistics from `adb shell dumpsys gfxinfo <package> framestats`.

The tool is deliberately offline: it reads a saved dump, never a device, so a measurement can be reviewed and compared
with the same parser months later. Two durations are reported for every frame:

* ``FrameStartTime -> FrameCompleted`` — the work the app and RenderThread spent on the frame;
* ``IntendedVsync -> FrameCompleted`` — the user-visible latency from the vsync that should have shown the frame.

Android's own "janky" counter uses a different definition (missed vsyncs per window) and is printed separately when the
dump contains the summary line. The definitions are never mixed.

Usage:
    adb shell dumpsys gfxinfo com.beykus.beygram.dev framestats > gfxinfo.txt
    python3 scripts/perf_framestats.py gfxinfo.txt --refresh-hz 60
"""

from __future__ import annotations

import argparse
import re
import sys

PROFILE_START = "---PROFILEDATA---"
PROFILE_END = "---PROFILEDATA---"
JANKY_RE = re.compile(r"Janky frames:\s*(\d+)\s*\(([\d.]+)%\)")


def parse_framestats(text: str) -> list[dict[str, int]]:
    """Returns the rows of the first profiledata block; unknown or unparsable columns are skipped."""
    lines = text.splitlines()
    header = None
    start = None
    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("Flags,") and "FrameCompleted" in stripped:
            header = [part.strip() for part in stripped.split(",")]
            start = index + 1
            break
    if header is None or start is None:
        return []

    rows: list[dict[str, int]] = []
    for line in lines[start:]:
        stripped = line.strip()
        if not stripped or stripped == PROFILE_START or stripped == PROFILE_END:
            break
        values = stripped.split(",")
        if len(values) != len(header):
            continue
        row: dict[str, int] = {}
        try:
            for name, value in zip(header, values):
                row[name] = int(value)
        except ValueError:
            continue
        rows.append(row)
    return rows


def durations_ms(
    rows: list[dict[str, int]], start_field: str, end_field: str = "FrameCompleted"
) -> list[float]:
    values: list[float] = []
    for row in rows:
        start = row.get(start_field, 0)
        end = row.get(end_field, 0)
        if start > 0 and end > start:
            values.append((end - start) / 1_000_000.0)
    return values


def percentile(values: list[float], p: float) -> float:
    """Linear-interpolated percentile, the definition Android Studio and Perfetto reports use."""
    if not values:
        raise ValueError("percentile of an empty sample")
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    position = (p / 100.0) * (len(ordered) - 1)
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    fraction = position - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction


def summarize(values: list[float], refresh_hz: float) -> dict[str, float]:
    if not values:
        return {"frames": 0}
    budget_ms = 1000.0 / refresh_hz
    janky = sum(1 for value in values if value > budget_ms)
    return {
        "frames": len(values),
        "p50": percentile(values, 50),
        "p90": percentile(values, 90),
        "p95": percentile(values, 95),
        "p99": percentile(values, 99),
        "max": max(values),
        "budget_ms": budget_ms,
        "over_budget": janky,
        "over_budget_percent": 100.0 * janky / len(values),
    }


def janky_from_summary(text: str) -> tuple[int, float] | None:
    match = JANKY_RE.search(text)
    if match is None:
        return None
    return int(match.group(1)), float(match.group(2))


def render(text: str, refresh_hz: float, label: str) -> str:
    rows = parse_framestats(text)
    lines = [f"[{label}]"]
    if not rows:
        lines.append("no framestats rows found (is the package debuggable/profileable and did it draw?)")
        return "\n".join(lines)
    for name, values in (
        ("FrameStartTime->FrameCompleted", durations_ms(rows, "FrameStartTime")),
        ("IntendedVsync->FrameCompleted", durations_ms(rows, "IntendedVsync")),
    ):
        stats = summarize(values, refresh_hz)
        lines.append(
            "{name}: frames={frames} p50={p50:.2f} p90={p90:.2f} p95={p95:.2f} p99={p99:.2f} "
            "max={max:.2f} ms, over {budget_ms:.2f} ms: {over_budget} ({over_budget_percent:.1f}%)".format(
                name=name, **stats
            )
        )
    summary = janky_from_summary(text)
    if summary is not None:
        lines.append(f"dumpsys janky summary: {summary[0]} ({summary[1]:.1f}%) — different definition, keep separate")
    return "\n".join(lines)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dump", nargs="?", help="saved gfxinfo output; stdin when omitted")
    parser.add_argument("--refresh-hz", type=float, default=60.0, help="display refresh rate (default: 60)")
    parser.add_argument("--label", default="gfxinfo", help="name printed before the numbers")
    args = parser.parse_args(argv)

    if args.dump:
        with open(args.dump, encoding="utf-8", errors="replace") as handle:
            text = handle.read()
    else:
        text = sys.stdin.read()
    print(render(text, args.refresh_hz, args.label))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))