#!/usr/bin/env python3
"""Self-check for tools/lag_report.py: python3 tools/test_lag_report.py (standard library unittest)."""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import lag_report  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def line(sec, msg, thread="Render thread"):
    return "[22Oct2026 14:%02d:%02d.000] [%s/INFO] [co.so.re.ReignOfNether/]: %s" % (sec // 60, sec % 60, thread, msg)


def frame(sec, fps, p95=40.0):
    return line(sec, "[Frame] p50 %.1f ms (%d fps), p95 %.1f ms, max 90.0 ms over 240 frames; RTS view, zoom 60, 80 units known" % (1000 / fps, fps, p95))


def tick(sec, p95):
    return line(sec, "[Tick] p50 8.0 ms, p95 %.1f ms, max 90.0 ms over the last 100 ticks; 80 units, 5 buildings, 1 players" % p95, "Server thread")


class LagReportTest(unittest.TestCase):
    def analyze_file(self):
        with open(os.path.join(HERE, "lag_report_sample.log"), encoding="utf-8") as fh:
            return lag_report.analyze(fh.readlines())

    def test_sample_log_is_render_bound_on_an_integrated_gpu(self):
        r = self.analyze_file()
        self.assertEqual(len(r["frames"]), 18)
        self.assertEqual(len(r["spikes"]), 4)
        self.assertEqual(len(r["gcs"]), 1)
        self.assertTrue(lag_report.is_integrated(r["gfx"]["renderer"], r["gfx"]["vendor"]))
        self.assertTrue(lag_report.verdict(r).startswith("render-bound"), lag_report.verdict(r))
        text = lag_report.report(r)
        self.assertIn("INTEGRATED GPU", text)
        self.assertIn("unitShadow", text)
        self.assertIn("VERDICT: render-bound", text)

    def test_discrete_gpu_is_not_flagged(self):
        self.assertFalse(lag_report.is_integrated("NVIDIA GeForce RTX 4070/PCIe/SSE2", "NVIDIA Corporation"))
        self.assertTrue(lag_report.is_integrated("Intel(R) UHD Graphics 630", "Intel"))

    def test_gc_verdict_when_spikes_line_up_with_gc(self):
        lines = [frame(10 * i, 60) for i in range(1, 7)] + [tick(10 * i, 10.0) for i in range(1, 7)]
        for i in range(8):
            t = 5 + 7 * i
            lines.append(line(t, "[Spike] 120 ms frame; 80 units known, zoom 60, RTS view"))
            lines.append(line(t, "[GC] pause 110 ms (G1 Young Generation, end of minor GC: Allocation Failure); heap now 3000 MB of 4096 MB"))
        self.assertTrue(lag_report.verdict(lag_report.analyze(lines)).startswith("GC"))

    def test_server_bound_verdict(self):
        lines = [frame(10 * i, 60) for i in range(1, 7)] + [tick(10 * i, 70.0) for i in range(1, 7)]
        lines.append(line(30, "[Sys] 200 ticks, mean 78.4 ms/tick; ours 51.2 ms (65%): attacker 12.30 (max 40.1, 610/t) | unit 8.80 (max 20.0, 600/t); elsewhere 27.2 ms", "Server thread"))
        r = lag_report.analyze(lines)
        self.assertTrue(lag_report.verdict(r).startswith("server-bound"))
        self.assertEqual(r["sys"].most_common(1)[0][0], "attacker")

    def test_one_server_window_near_budget_does_not_hide_a_slow_client(self):
        # vscreen run 2026-11-01 (a9ab0aec, software GL): median frame 43.7 ms (23 fps), server tick p95 13 ms in the
        # median window and 45.2 ms in the worst one, under the 50 ms budget; the verdict said server-bound
        lines = [frame(10 * i, 23) for i in range(1, 7)] + [tick(10 * i, 13.3) for i in range(1, 6)] + [tick(60, 45.2)]
        v = lag_report.verdict(lag_report.analyze(lines))
        self.assertTrue(v.startswith("render-bound"), v)
        self.assertIn("45 ms", v, "the near-budget server window is still named: " + v)

    def test_one_slow_server_window_is_a_hitch_not_server_bound(self):
        # real-GPU run 2026-11-01 (main 43b84275, 1 bot): 120 fps, tick windows p95 45.2 (match start), 6.0, 15.6, 5.6 ms;
        # the verdict said server-bound
        lines = [frame(10 * i, 120) for i in range(1, 6)] + [tick(10 * (i + 1), p) for i, p in enumerate((45.2, 6.0, 15.6, 5.6))]
        v = lag_report.verdict(lag_report.analyze(lines))
        self.assertTrue(v.startswith("nothing obviously wrong"), v)
        self.assertIn("1 of 4", v, "the one slow window is still named: " + v)
        self.assertIn("45 ms", v, v)

    def test_two_slow_server_windows_are_server_bound(self):
        lines = [frame(10 * i, 120) for i in range(1, 6)] + [tick(10 * (i + 1), p) for i, p in enumerate((48.0, 6.0, 52.0, 5.6))]
        v = lag_report.verdict(lag_report.analyze(lines))
        self.assertTrue(v.startswith("server-bound"), v)
        self.assertIn("2 of 4", v, v)

    def test_chunk_loading_verdict(self):
        lines = [frame(10 * i, 70) for i in range(1, 7)] + [tick(10 * i, 10.0) for i in range(1, 7)]
        for i in range(1, 7):
            lines.append(line(10 * i, "[FrameParts] 600 frames, mean 14.0 ms/frame; ours 2.0 ms (14%): hud 2.00 (max 4.0, 1.0/f); elsewhere 12.0 ms; 400 section re-mesh requests/s"))
        for i in range(10):
            lines.append(line(3 + 6 * i, "[Spike] 150 ms frame; 80 units known, zoom 60, RTS view"))
        self.assertTrue(lag_report.verdict(lag_report.analyze(lines)).startswith("chunk loading"))

    def test_healthy_log_and_empty_log(self):
        lines = [frame(10 * i, 90) for i in range(1, 7)] + [tick(10 * i, 10.0) for i in range(1, 7)]
        self.assertTrue(lag_report.verdict(lag_report.analyze(lines)).startswith("nothing obviously wrong"))
        self.assertTrue(lag_report.verdict(lag_report.analyze(["hello"])).startswith("no perf lines"))


if __name__ == "__main__":
    unittest.main()
