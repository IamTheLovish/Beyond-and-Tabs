# Persistence: what survives a save and reload

A Minecraft world is saved by autosave (every few minutes), by `/save-all`, when the last player leaves an integrated
(singleplayer) world, and when the server stops. Chunks also save and reload on their own whenever nobody is near
them. Everything below is either **saved**, **rebuilt** from something saved, or **reset on purpose**. Nothing a
player paid for and expects to keep may silently disappear.

All SavedData files live in `<world>/data/` in the overworld's data storage.

## Units

| Record | Where it is saved | Notes |
|---|---|---|
| The unit entity itself | Its chunk (vanilla entity NBT) | Loaded through `EntityType.create(tag, level)`. Any exception in `readAdditionalSaveData` makes the chunk loader drop the unit ("Exception loading entity"), so the unit vanishes. GameTests `units_survive_reload_0..7` run every unit type down this path. |
| Owner, anchor, scenario role, equipment, on-death command | Entity NBT (`Unit.addUnitSaveData`) | |
| Engagement stance | Entity NBT (`bt_stance`, `EngagementStance.save/load`) plus Forge persistent data | |
| Kills | Entity NBT (`bt_kills`, `UnitKills`) plus Forge persistent data | |
| Veterancy XP | Forge persistent data (`bt_veteran_xp`) | The rank's health/damage boosts are permanent attribute modifiers, so vanilla saves them with the attributes. |
| Hero XP and level | Entity NBT (`HeroUnit.addHeroUnitSaveData`) and `saved-herounit-data` | |
| Gather targets | `saved-target-resources-data` (`TargetResourcesSaveData`) | |
| Harpooner throw counter | Reset | A reloaded Harpooner opens with a pull. This is harmless. |

**Fixed in reload-fix-6:**
- `FoxCourierUnit` (Verdant scout). Vanilla `Fox.readAdditionalSaveData` ends in its private `setTargetGoals()`. That
  method adds the fox's `landTargetGoal`, `turtleEggTargetGoal` and `fishTargetGoal`, but only vanilla
  `Fox.registerGoals` creates those fields, and the unit replaces that method. When a courier loaded from NBT, all
  three fields were null. `GoalSelector.addGoal(4, null)` then put a `WrappedGoal` with a null goal into a
  LinkedHashSet, and `WrappedGoal.hashCode` threw NPE "this.goal is null", so every courier was lost on any chunk
  reload. The spawn path never hit this because `finalizeSpawn`, the other caller, is overridden. The fix: right
  before loading, the courier fills those null fields with an inert goal that never runs, then removes that goal
  right after.
- `KillerRabbitUnit`. Vanilla `Rabbit.readAdditionalSaveData` calls `setVariant(EVIL)`, which also adds the killer
  bunny's own goals: attack players and wolves on sight. Every reload bolted that vanilla AI onto the RTS unit. Its
  `setVariant` is now a no-op (`getVariant` always returns EVIL anyway). The reload test checks that a reloaded unit
  has the same number of goals and target goals as a fresh one.

## Buildings, economy, players

| Record | Where it is saved |
|---|---|
| Buildings (placement, owner, progress, production queues) | `saved-building-data`, `saved-custom-building-data` |
| Nether zones | `saved-netherzone-data` |
| Metal patches | `saved-metal-patches` |
| Resource pools | `saved-resources-data` (up to 1 unit of each resource can be lost, see `Resources`) |
| Research | `saved-research-data` |
| RTS players (faction, scores, trade rates, colour, item seed) | `saved-rtsplayer-data` |
| **End-of-match award totals** (`damageDealt`, `metalProduced`, `metalReclaimed`) | `saved-rtsplayer-data` (new) |
| **Results-screen graphs** (`MatchHistory`: samples, current interval, offer count) | `saved-rtsplayer-data`, under `history` (new). Sampling continues on the same stride after a reload. |
| Start positions | `saved-start-pos-data` |
| Survival waves, tutorial, scenario roles | `saved-survival-data`, `saved-tutorial-data`, `saved-scenario-data` |
| **The match lifecycle** (`match.MatchState`: phase, origin, match clock, the map in play; while preparing the frozen setup, how far a pre-made map got and every start pick with the clock; once seated the match's sides and humans; once decided the winners and the match time at the decision) | `bt-match-lifecycle` / `state` (new, rewrite 5) |
| **Alliances between the match's sides** | `bt-match-lifecycle` / `alliances` (new: before, no alliance was saved at all, so a reloaded team game made teammates enemies) |
| **Finished sides** (beaten, or winners who haven't spectated) | `bt-match-lifecycle` / `finished`, RTSPlayerSaveData's format (new: a winner can still spectate after a reload, and the results keep the beaten sides' rows) |

Some BAR flow-economy values on `BuildingPlacement` are recomputed every second from the buildings, so they are
not saved: build/production flow rates, `tickAge`, and tower cooldowns such as the Storm Oak and Lighthouse
Battery. A reloaded tower is ready to fire at once.

### The match lifecycle across a reload (rewrite 5, code_review CR-13)

`match/MatchLifecycleSaveData` uses the same shell as `bt-match-state` (below): a snapshot on every save, a
highest-priority snapshot at ServerStoppingEvent, read on ServerStartedEvent (LOW priority, after
`PlayerServerEvents` has put the saved sides back). Then the state is **resumed** (`MatchState.resume`):

| Saved phase | On reload |
|---|---|
| LOBBY | Nothing to restart; the map's fog tint comes back. |
| ENDED | Nothing to restart; each of its sides gets the results popup again on logging in (client memory), timed at the decision. |
| MAP_RISING, economy not stamped yet | The build runs again from the saved setup and base height: it only writes blocks that differ, so the part already stamped costs reads. |
| MAP_RISING, economy stamped (saved on the build's last tick) | Straight on to the start positions (the patches and sites must not be stamped twice). |
| START_POSITIONS | Every pick and the clock come back; the clock is held until one of its humans is online, then runs at least 20 s; it ends early only once every human is back. |
| PLAYING | The match takes its sides back (victory scope, late joiners watch). |
| A setup that can't resume (unreadable, unknown map, picks missing or of another lineup) | Cancelled to LOBBY, nothing seated; everyone who logs in is told. |

A world saved before this branch has no `bt-match-lifecycle`: sides in play make it a classic match. The defeat
cleanup (a beaten base turning into wrecks over a few seconds) is finished before the final save instead of being
dropped (`DefeatCleanupServerEvents.finishAll`). Still memory only: the battlefield's 3 s settle and the wall rising
over the first half minute (see design/match-lifecycle-progress.md, Proposals).

## Faction world records

| Record | Where it is saved | On load |
|---|---|---|
| **Stormcaller Mana Wells** (owner, dimension, centre) | `bt-match-state` / `wells` (new, `MatchStateSaveData`) | `ManaWellServerEvents.restore` re-registers each well (the cap still applies) and raises fresh markers at the exact centre. Old markers loading with their chunks are discarded as stale, so a well is never doubled. |
| **Ley links** (pairs, generator flags, level per owner) | `bt-match-state` / `ley` (new) | `LeyNetwork.restore` re-links through `link()`, so the caps and distance rules still hold. Resonance income is identical. |
| **Tidepools** (owner, dimension, centre, radius, cells, ticks left, draining, trail flag) | `bt-match-state` / `tidepools` (new). The water blocks and their fail-safe block ticks are saved with the chunks. | `TidepoolServerEvents.restore` re-registers the record over the existing blocks without placing anything. The pool keeps its owner, its slot under the cap, its reclaim and evaporate handle, and the rest of its lifetime. A trail pool comes back as a trail pool with a source that no longer exists (`RESTORED_TRAIL_SOURCE`), so it never takes one of the owner's normal pool slots. |
| Tidepool drain orders (worker entity ids) | Reset | Entity ids don't survive a reload. The constructor's reclaim order has to be given again. |
| **Faction terraform auras** (source, owner, style, footprint, plan cursor, receding flag, every converted cell with its ground and cover before and after) | `bt-match-state` / `auras` (new, `FactionAuraServerEvents.saveTag`). Block states go through a per-aura palette of NBT states, not raw ids. The converted blocks themselves are saved with their chunks. | `FactionAuraServerEvents.restore` re-registers each aura over the existing blocks without placing anything (idempotent: an aura already registered for the same source is skipped, a cell that already has an owner keeps it). The plan is rebuilt from the source position. A restored aura keeps its ground while its building's built-age restarts after the reload; a receding one carries on giving the ground back. |
| **Verdant thickets** (owner, cut damage) | The block entity's NBT, saved with its chunk | The cover registry `ThicketBlockEntity.LIVE` is rebuilt by `onLoad` as each chunk loads. `ThicketCover.HIDDEN` is recomputed every 5 ticks. Reveal timers (`REVEALED_UNTIL`, 3-6 s, keyed by entity id) reset. Note: the per-player thicket cap counts only loaded thickets. |
| **Capture points** (kind, owner, progress, claimant, camp timer) | The site's marker entity (Forge persistent data), saved with its chunk | `onJoin` re-registers the site. Hired camp wolves now carry `bt_cp_hired` (their employer), so a reload counts them against `CAMP_MAX` again. Before, a reload let a camp hire three more. A site whose chunk is unloaded neither captures nor rewards until the chunk loads. |
| Frost fields (blocks) | Blocks and fail-safe block ticks, saved with the chunk | The registry resets, and the fail-safe ticks melt the ice: no permanent terrain change. |
| Seals and similar status effects | Entity NBT (mob effects) | |

## Reset on purpose

These are channelled or short-lived effects. A reload that drops one costs at most one recast, and saving them would
mean saving a caster's channel state and their entity references across the reload:

- **Decoys** (Mirror Mage): a decoy that loads from disk is discarded (`DecoyServerEvents.onJoin`). Recast it.
- **Lantern Domes** (Lantern Fortress): reset. The Fortress raises the dome again.
- **Tempest storms** (Celestial Dragon): reset. A storm lasts 10 s.
- **Wards**: reset. A ward lasts 8 s.
- **Fog Banks** (Tidewrought Admiral): reset. A bank lasts 12 s.
- **Overcharge** toggle: reset to off. It is a per-player toggle, re-enabled with one key.
- Faction traits cache, `lastRefusal` messages: derived values, rebuilt on demand.

## How `bt-match-state` works

`persistence/MatchStateSaveData` is the SavedData shell. Each owning class serialises its own records
(`saveTag()` / `restore(server, list)`). Nothing copies records into the SavedData on every change. Instead:

1. `save()` snapshots the live registries whenever the world is written. `isDirty()` is always true, so every
   autosave writes the file (a few hundred bytes).
2. Forge fires `ServerStoppingEvent` before the final save, and the registries clear themselves in their stopping
   handlers. A highest-priority stopping handler therefore takes the snapshot first, and the final save writes that
   snapshot.
3. `load()` only keeps the tag. `ServerStartedEvent` clears the static mana registries (an integrated server's
   statics outlive the world they came from), then restores the tag. Every restore is idempotent.

GameTests (end of `gametest/SkirmishGameTests.java`): `mana_wells_survive_reload`, `ley_links_survive_reload`,
`tidepools_survive_reload`, `thicket_owner_and_registry_survive_reload`,
`capture_point_and_hired_wolves_survive_reload`, `match_history_and_awards_survive_reload` and
`units_survive_reload_0..7`. Each one writes the records, sends them through real NBT bytes, clears them, loads
them back and checks they are equal.
