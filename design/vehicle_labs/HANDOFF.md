# Handoff: Itachi 2, session C (vehicle-lab pilot + tools), Oct 28 16:05 ET

The next session starts here. Repo `IamTheLovish/Beyond-and-Tabs`, branch **`claude/vehicle-labs`** (draft PR #306,
everything below is pushed). Coordinator: **Rita** (Command Center; replaced Rem at 05:34). lovish relays messages by
hand; every report to the coordinator is a paste block.

**First message to lovish: only `~/start-runners.sh`** (his standing rule). Then read
`claude/north-star-2026-10-21.md` (Project doc), this note, `design/vehicle_labs/LESSONS.md` (bottom section "Pilot
rounds 1-5" is new) and the last rows of `design/vehicle_labs/SCORES.md`.

## Where things stand

| What | State | Next |
|---|---|---|
| **Engine Shed pilot** (Brassworks T1 lab imitating ref18, 13x21, <= 22) | Greybox r3 = the approved massing (tower over the door, arcade halls, swept end wings). Detail rounds 1-5 scored 4/5, 4/4, 4/4, 4/5, **3/5** by fresh Fable critics. **Loop STOPPED after r5** (overnight, to stop burning usage). Latest: `brass_l3.nbt` / `brass_l1.nbt`, sheet `brass_detail_vs_ref_r5.png` | **Wait for lovish's answer** (question 1 below). Do not start round 6 on ref18 at T1 size. |
| **#306** vehicle-labs (draft) | head 363531b6; main merged in (a94f2aa2) | Stays draft, "Do not merge without lovish's OK" |
| **#324** shape kit | **MERGED** 14:41Z. Claim `shape-kit` released | - |
| **#303** breath phase | Green (run 40679402482 attempt 2), Mode A PASS, mergeable clean, head dc49693e. Paste block sent to Rita | Rita merges. **Then release claim `breath-phase`** (`python3 tools/claim.py release breath-phase`) |
| **#322** renderer lighting | MERGED 13:05Z (another session's) | Done: vehicle-labs uses main's renderer |
| T2 site-search flake (`claude/flake-t2-site-search` @ 2f1f8abe) | Red test pushed; waits for #203 (still open, Mode A round 3 PASS) | When #203 merges: merge main, fix test-first (root cause in Project doc `claude/handoff-2026-10-28-itachi2.md`) |
| #299 bombard dead-zone, #131 generated maps | With the coordinator | Nothing from us |

Claims still held on #91: `breath-phase`, `vehicle-labs`, `flake-t2-site-search`.

## Questions for lovish (ask, don't assume)
1. **The pilot:** (a) re-pilot the T1 Engine Shed on a lab-scale reference (ref11 small mossy tower, ref12 Victorian
   house, ref15 lit spired mansion, ref17 gallery towers, ref19 vine tower, ref21 gothic tower with bartizans, ref27
   armillary tower) and keep ref18 for a T2/T3 or capitol-scale Brassworks building, **or** (b) keep ref18 and give
   this lab a bigger top-level footprint. *(Recommended: a.)*
2. Is the r3 greybox massing still what he wants? (He said "keep studying it and make it super detailed and
   intricate" at 05:33 but has not looked at r3 itself yet.)
3. Still open from session B: what is tier 4; what triggers a T1/T2 refit; the AI faction (refs, name, roster, start);
   `design/vehicle_labs.md` section 5.

## Why the pilot stalled (full text in LESSONS.md)
1. **Wrong scale of reference.** ref18 is palace-scale: its spire is ~60 blocks of fine stepping, ours has 3 half-width
   cells over 12 rows, so every step is a 4-row box (the frozen outline 7/5/5/5/5/3/3/3/3/1). Its red-panel grid fits
   only a handful of panels at 13x21. The critics' own ceiling estimate: 6-7/10.
2. **Fresh critics drift.** r1 "less grey", r3 "grey-cream walls", r5 "red with black frames, grey only as trim";
   builds swung between them.
3. **Tolerance checks let regressions through** ("within 1 block of r3" passed stacked boxes twice).

## The method for the next round (whatever lovish picks)
1. Choose the reference **by scale first** (it must fit the plot at that tier).
2. Greybox, front-on outline checked column by column (`tools/ref_compare.py --front`), lovish OKs.
3. The art director writes **one fixed per-region rubric** from the picture (materials, colour shares, rhythm, light)
   before any detail; every critic scores against that rubric, not its own taste.
4. **Freeze each win as an assert** in the build script (see `check_tower`, `check_banned`, `check_greybox` in
   `tools/lab_vehicle_brass.py`): exact tables, never tolerances.
5. Colour and shape measured on **rendered pixels**, not block counts.
6. Opus builds, fresh Fable critic scores; max two heavy agents; stop after 2 rounds without progress and ask lovish.

## Tools now on the branch
- `tools/structure_render.py` = main's (#322): lit by default, `--flat` for the old look; `SR.Light` namedtuple.
- `tools/ref_compare.py`: lit by default, `--flat`, **`--front`** (west elevation + squint + corner view; stairs/slabs
  drawn at their real shapes).
- `tools/shape_kit.py` = main's (#324): SDF primitives, booleans, stair/slab edge pass, hollow shells; `build()` never
  replaces a full cell with a stair/slab; no scipy. Known: the clockwise tie-break breaks mirror symmetry (non-blocking).
- `tools/lab_vehicle_brass.py`: greybox (`--greybox`, own `GB_APEX` = 23.0 - the old 21-tall regeneration bug was it
  sharing `T_APEX` with the spire) + detail L1/L3 + the asserts above. `ruff check tools` passes.

## Block colour notes (our pack, rendered)
Red terracotta is the closest red to ref18 (mangrove stairs/slabs next); there is **no red concrete or wool texture**.
Red nether brick renders maroon; polished blackstone and deepslate tiles render grey; polished blackstone bricks is the
best near-black; polished andesite is the smoothest grey; cobblestone reads as rubble at 32 px (banned in the brass lab).

## Environment notes (cloud session)
- PC bridge was offline all session: `D:\Command Center`, `kb.ps1` ledger and the Obsidian vault were not reachable.
  LEARN lines below are for filing when it is back.
- Job logs/artifacts are blocked: read failures via check-run annotations. GraphQL blocked: REST `gh api` only.
- `*.png` is gitignored: add sheets with `git add -f`.
- Worktrees in this container: `/home/claude/wt-vl` (vehicle-labs), `wt-breath`, `wt-shape` (merged, can go);
  the generated block pack lives in `/home/claude/beyond-and-tabs/resourcepacks/...` (symlinked into wt-vl).

## LEARN (for the ledger / vault)
- worked: front-on outline checked column by column in the reference's own camera view; exact per-row tables asserted
  on every build; picking blocks by rendered pixel colour; tests first on the shape kit (red -> green) caught a second
  bug (`Structure.pal` taken as a palette).
- failed: a palace-scale reference on a T1 plot; open-ended fresh critics; tolerance checks; leaving a dead session's
  work unchecked (session B was still pushing to vehicle-labs, which made a duplicate shape kit and renderer).
- reusable rules: choose the reference by scale first; give critics a fixed rubric; freeze approved shapes as tables
  the build must equal; before building on a branch, `git fetch` and check who pushed last.
