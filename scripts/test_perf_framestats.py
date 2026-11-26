"""Tests for the saved-gfxinfo frame parser (scripts/perf_framestats.py)."""

import unittest

from perf_framestats import (
    durations_ms,
    janky_from_summary,
    parse_framestats,
    percentile,
    summarize,
)

HEADER = (
    "Flags,FrameTimelineVsyncId,IntendedVsync,Vsync,InputEventId,HandleInputStart,AnimationStart,"
    "PerformTraversalsStart,DrawStart,FrameDeadline,FrameInterval,FrameStartTime,SyncQueued,SyncStart,"
    "IssueDrawCommandsStart,SwapBuffers,FrameCompleted,DequeueBufferDuration,QueueBufferDuration,"
    "GpuCompleted,SwapBuffersCompleted,DisplayPresentTime,CommandSubmissionCompleted"
)

DUMP = "\n".join(
    [
        "Applications Graphics Acceleration Info:",
        "Janky frames: 3 (25.00%)",
        "---PROFILEDATA---",
        HEADER,
        # A normal frame: 16 ms of work, shown one vsync after the intended one.
        "0,1,1000000000,1010000000,0,1020000000,1021000000,1022000000,1023000000,1030000000,16666666,"
        "1025000000,0,0,1024000000,1026000000,1033000000,0,0,0,0,0,0",
        # A janky frame: 40 ms of work.
        "0,2,1100000000,1110000000,0,1120000000,1121000000,1122000000,1123000000,1130000000,16666666,"
        "1125000000,0,0,1124000000,1136000000,1143000000,0,0,0,0,0,0",
        "---PROFILEDATA---",
        "Some unrelated tail",
    ]
)

BROKEN_ROWS = "\n".join(
    [
        "---PROFILEDATA---",
        HEADER,
        "0,1,not-an-int,1010000000,0,1020000000,1021000000,1022000000,1023000000,1030000000,16666666,"
        "1025000000,0,0,1024000000,1026000000,1033000000,0,0,0,0,0,0",
        "0,2,1100000000,1110000000,0,1120000000,1121000000,1122000000,1123000000,1130000000,16666666,"
        "1125000000,0,0,1124000000,1136000000,1143000000,0,0,0,0,0,0",
        "---PROFILEDATA---",
    ]
)


class PerfFramestatsTests(unittest.TestCase):
    def test_parses_only_the_profiledata_block(self):
        rows = parse_framestats(DUMP)
        self.assertEqual(2, len(rows))
        self.assertEqual(1033000000, rows[0]["FrameCompleted"])

    def test_ignores_unparsable_rows_instead_of_crashing(self):
        rows = parse_framestats(BROKEN_ROWS)
        self.assertEqual(1, len(rows))

    def test_reports_both_durations(self):
        rows = parse_framestats(DUMP)
        self.assertEqual([8.0, 18.0], durations_ms(rows, "FrameStartTime"))
        self.assertEqual([33.0, 43.0], durations_ms(rows, "IntendedVsync"))

    def test_percentiles_are_interpolated(self):
        self.assertEqual(2.0, percentile([1.0, 2.0, 3.0], 50))
        self.assertAlmostEqual(2.9, percentile([1.0, 2.0, 3.0], 95))

    def test_summary_counts_frames_over_the_refresh_budget(self):
        stats = summarize([8.0, 18.0], refresh_hz=60)
        self.assertEqual(2, stats["frames"])
        self.assertEqual(1, stats["over_budget"])
        self.assertAlmostEqual(50.0, stats["over_budget_percent"])

    def test_janky_summary_is_read_but_kept_separate(self):
        self.assertEqual((3, 25.0), janky_from_summary(DUMP))

    def test_empty_dump_has_no_rows(self):
        self.assertEqual([], parse_framestats("no graphics info here"))


if __name__ == "__main__":
    unittest.main()