# Diagnosing "it was stuck" (Oct 22 2026)

A tester said he was stuck in the loading screen; his latest.log showed the world loaded, and nothing said which screen
he looked at or what it waited for. These lines, the report bundle and `tools/diagnose_log.py` close that gap. They sit
beside the perf lines in `design/perf-measurement.md`.

## Log lines (client)

| line | when | says |
|---|---|---|
| `[Screen] <Class> "<title>"; waiting for: ...` | every screen change | which screen opened. `in-game (RTS view)` / `in-game (first person)` / `no screen, no level` when none is open |
| `[Screen] <Class> "<title>" still open after N s; waiting for: ...` | every 10 s while a non-game screen stays open | what it waits for when known: `lobby seats 1/4`, `chunks in view 62%` (the match loading veil), `terrain prep 40%`, `phase start positions` / `map prep`, `the server's level (none on the client yet)` when the client has no level |
| `[Gfx] Xmx 8192 MB of 16384 MB RAM (50%); shader pack detail: ACTIVE "<pack>" [Oculus 1.8.0]; shader/GL errors seen N` | once per world join, after the old `[Gfx]` line | memory setting (flagged `TOO HIGH` at 75% of RAM or more), shader pack name and Oculus / Iris / Embeddium versions |
| `[Gfx] shader/GL errors in the last 10 s: N (T in total; first: ...)` | every 10 s, only when N > 0 | the shader pipeline is failing. Counted by a log4j appender (`perf/ShaderErrors`) that matches GL errors, GLSL "Unknown variable", failed compiles and any ERROR from an Oculus / Iris logger (`DiagRules.isShaderError`) |

Code: `perf/ScreenLog` (tick, no per-tick allocation), `perf/ShaderErrors`, `perf/GfxLog`, pure rules in `perf/DiagRules`
(tested in `DiagnoseGameTests`). Not covered: the vanilla loading-screen percent (the screen keeps it private), and the
overlay form of the match veil (only its Screen form shows as a screen).

## Report a problem

`/rts-report` (client command) or the "Report a problem" button (bottom left of the pause screen, our match menu, lobby,
skirmish setup and match-start screens, and the connecting / receiving-level / loading screens; `perf/ReportBundle` draws
it through ScreenEvent hooks, so no screen class was changed). It writes `logs/bat-report-yyyyMMdd-HHmmss.zip` and opens
the logs folder:

- `latest.log` - a copy
- `screenshot.png` - the current frame
- `report.txt` - mod version, Minecraft / Forge, Java, GPU / vendor / OpenGL (driver), shader pack, Xmx / RAM, window size,
  current screen, first shader error, the last 20 `[Screen]` lines

Lang keys (en_us only): `hud.reignofnether.report.button|done|failed`.

## `tools/diagnose_log.py`

`python3 tools/diagnose_log.py <latest.log>` (unzip a report first). Prints versions (launcher, Java, mod build), Xmx and
GPU, the shader pack with its error count and first time, world create / load, LAN / lobby open and the e4mc domain,
players joined, every `[Screen]` line, errors deduplicated, the last 15 events before `Stopping!`, and a one-paragraph
VERDICT, e.g. "World loaded at 02:13:30; opened to LAN at 02:13:45 (x.e4mc.link); sat in the lobby 92 s with 0 joins; the
shader pack was broken from 02:14:55 ...". Standard library only. Self-test:
`uv run --no-project --python 3.12 python -I tools/test_diagnose_log.py` (sample: `tools/diagnose_log_sample.log`).
For "why choppy" use `tools/lag_report.py`.
