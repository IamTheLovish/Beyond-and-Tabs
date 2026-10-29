# Handoff: Itachi 2, session G (study session: roof kit, 49 refs, 26 builds measured, master-architect rules), Oct 29 00:30

Repo `IamTheLovish/Beyond-and-Tabs`, branch **`claude/vehicle-labs`** (draft PR #306; never merge without lovish's OK).
Head at handoff **3e0c8a2** (+ the r8 builder's commit if it landed - see "Open 1"). lovish relays messages by hand.

**First message to lovish: only `~/start-runners.sh`.** Then read, in this order: `claude/north-star-2026-10-21.md`, this
note, `design/architecture_rules.md` (the rulebook, ~80 lines), `design/vehicle_labs/studies/detail_recipes.md` (top-8
section at the end), `design/research/rts-buildings-frostpunk-anno-2026-10-29.md` (rules + T1->T4 table only).
All also in Project "4": `claude/architecture-rules-2026-10-29.md`, `claude/detail-recipes-2026-10-29.md`,
`claude/rts-buildings-frostpunk-anno-2026-10-29.md`, `claude/engine-shed-detail-grammar-2026-10-28.md`.

## lovish this session (his words / decisions)
- 22:42 "its not looking realistic enough" (r7). 23:44 "become a master architect". 23:49 "should you study it more".
- **Decided 22:59: red brick walls, grey SLATE roofs on halls / shed / wings, weathered GREEN COPPER on the tower and
  spires** (option "Slate + copper").
- Sent 26 reference images (ref45-ref70 in design/refs/vehicle_labs/, each analysed in
  design/vehicle_labs/engine_shed_detail_grammar.md sections (g)-(z)). ref50/53/54 look like the new AI faction (unconfirmed).
- Asked "can we get the game to look like this" (ref58, shader villa): half is rendering - Complementary + LabPBR is
  already wired into eyes (`shaders=true`); r7 with shaders: design reads the same toy -> the architecture is the gap.
  From now on every building is shot plain AND shaders.
- Had Itachi 3 download 26 builds (zip, study only, NEVER commit): they are only in this session's scratchpad. The
  next session needs lovish to re-attach `Itachi2-schematics.zip` (or Itachi 3 to upload it to the Project) to study more.
- Approved the Frostpunk + Anno 1800 study (done).

## Where things stand
| What | State | Next |
|---|---|---|
| tools/roof_kit.py (+test, 11 cases, mutation-checked) | pushed | slab-pair slate pitch, eave + verge overhang 1, copper gutters, ridge, SLATE / COPPER tones; swap to Macaw's shapes once ids are known |
| tools/schem_study.py (+test, 13 cases) | pushed | reads .nbt / .schem v2+v3 / .litematic / legacy .schematic; eave_overhang, roof_cells, depth_layers, palette_shares, section |
| design/architecture_rules.md | pushed | principles + measured table + gates G1-G7 (G1 counts a pale trim lip as the eave) |
| Engine Shed **r8** (roofs + walls) | Opus builder agent was mid-run at handoff (uncommitted roof_kit/test edits). Told to push green to claude/vehicle-labs or WIP to `claude/vehicle-labs-r8-wip`, then dispatch eyes plain + shaders | check which branch moved (`git log origin/claude/vehicle-labs -3`, `git ls-remote origin claude/vehicle-labs-r8-wip`); if nothing landed, rebuild r8 from the brief below |
| r9 (detail) | not started | detail_recipes top 8: eave band R1+R2, pilasters + lanterns R7, tall lit windows R8, cone corbel table R10, copper cone ribs R12, shed roof lights R5, banded stack R11, receding portal R9; gates G2 (>= 8 % thin blocks) + G5 (depth) |
| Macaw's Roofs block ids | NOT obtained | CI job logs are on blob.core.windows.net, unreachable from the cloud workspace (only annotations via api.github.com are). Fastest: ask Itachi 3 (on lovish's PC) to run `unzip -Z1 <mods>/mcw-roofs-2.3.2-mc1.20.1forge.jar \| grep blockstates/` and save the list to the Project. `[ModBlocksAll]` logging (bar BuildingBlockModGameTests) is pushed and harmless. |
| CI run 40814065387 | shard 8 failed `raid_t1_vehicles_break_windmills_and_extractors_faster_than_infantry` - the known raid flake #353 diagnoses | not caused by this session's changes |
| #353 raid diagnostics | green, waiting for lovish's OK to merge (asked twice, no answer yet) | claim `flake-raid-party-times` held |

Claims held: `vehicle-labs`, `flake-raid-party-times`.

## r8 brief (if it must be redone)
Roofs and walls only, frozen massing (cone tops 11 15 19 23 25 23 19 15 11 over z 8..16; lane x 0..8 z 10..14 y 1..8;
spawn column (8,*,12)); walls may step back 1 from the plot edge so eaves oversail (no block outside 17 x 25, h <= 26).
Halls / wing / shed: roof_kit SLATE courses, dark verges, ridge, copper gutters; eave band R1+R2. Cone: COPPER courses,
dark ribs on the arrises (R12), a corbel/ledge course at each tier foot (R10). Walls `bricks` (~80/20 with a second
tone, no chequer), pale stone plinth + string course. red_terracotta <= 5 %. Tests first (seen red on r7), retire the r7
tests that encode the rejected red look. Judge in eyes plain + shaders, fresh Fable critic, then lovish.

## Commit rules (hook + lovish)
Author **IamTheLovish <IamTheLovish@users.noreply.github.com>** (lovish's rule); committer **Claude
<noreply@anthropic.com>** (stop hook): `git config user.name Claude; git config user.email noreply@anthropic.com`, then
`git commit --author="IamTheLovish <IamTheLovish@users.noreply.github.com>" ...`. Push access: call add_repo
(IamTheLovish/Beyond-and-Tabs, push) first; use `env -u GH_TOKEN gh ...` (the GH_TOKEN in the env is invalid; REST
works, GraphQL doesn't). A push cancels the branch's running CI - batch pushes.

## Lessons (LEARN)
- worked: measuring builds (schem_study) turned "not realistic" into numbers - r7: 0 eave overhang, 0 % thin detail
  blocks (good builds 9-20 %), 41 % one block (good <= 25 %); asking lovish one palette question (slate + copper) instead
  of guessing; a parallel Fable recipe study while the Opus builder worked.
- failed: three CI runs cancelled by my own pushes while waiting for a log line - and the logs were unreachable anyway
  (blob storage). Check reachability of an output before building a pipeline around it.
- reusable: a building reads real through structure + depth + roof + light, not colour; the eave shadow comes from a
  pale trim lip as much as from the roof; relief is half-block (wall blocks in front of full blocks); judge plain AND
  shaders.

## Not done
- Command Center ledger and the Obsidian vault were unreachable again (chat not linked to the PC): write this note's
  LEARN lines there when the PC is linked.
- Frostpunk / Anno screenshots (list at the end of the study) - ask lovish.
