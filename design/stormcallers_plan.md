# Stormcallers - implementation plan

Faction 6 of `claude/design-factions.md`: a mage dynasty in jade towers and pagodas; red lacquer, jade and gold.
BAR role: **energy-heavy tech** - where the other factions spend energy on construction, the Stormcallers turn it
into spells. Signature: **Mana** - energy *is* mana: spells drain energy live, and **overcharging** empowers every
spell but stalls the player's own construction.

This plan reuses the templates of `design/verdant_court_plan.md` (section 1 there lists every faction touch point)
and `design/tidewrought_plan.md` (section 1 there lists what the Court already solved), plus what their "as built"
sections taught. Paths are relative to `bar/src/main/java/com/solegendary/reignofnether/` unless they start with
`bar/`, `tools/` or `design/`.

---

## 1. What the Court and the Tidewrought already solved (do it the same way)

| Lesson | What the Stormcallers do |
|---|---|
| One `faction/FactionTraits` entry per faction, never a fall-through | `FactionTraits.STORMCALLERS` exists from slice 0 (red-lacquer/gold livery, red-wool/gold-block banner, red-terracotta posts with jade - drawn oxidised copper - rails, red-glazed-terracotta wreck, accent `0xFFE0453A`). Slice 1 fills `commander(...)`, `fx(...)`, `extractor(...)`, `ambience(...)` and adds a `mana()` flag. |
| Register after the last faction so registry ids never move | Registered right after `TIDEWROUGHT` (slice 0; the GameTest checks the order). |
| `Factions.isLive` / `randomLive` drive "Random" | Nothing to do: the faction joins the pool the moment slice 1 drops `setPreview()`. |
| Skirmish codes are `SkirmishServerboundPacket.CODE_PATHS` (append-only, `3` = Random) | **`6` = `"stormcallers"`**, appended in slice 0. While a preview, `factionOf(6)` rolls a live faction. |
| The lobby/skirmish picker (`matchstart/FactionPicker`) is built from the registry / `CODE_PATHS` | Nothing to do: it already lists a preview greyed ("Coming soon", `LobbySounds.denied`) and scrolls, so the old "eight tiles won't fit" worry is gone. `LobbySounds.stingerOf` gets `faction_stormcallers` in slice 1 (null until then = chirp only). |
| `BotPlayer.kitFor` + `/rts bot` parse | Slice 1, same commit as going live: kit + `"stormcallers", "storm"` in `BotServerEvents.addBotImpl`. `startRTS` / `startRTSBot` / `StartPosServerboundPacket` already refuse previews. |
| Structures in **both** `assets/` and `data/`, from `tools/gen_structures.py` | `"_storm"` palette (section 6). Revert unchanged NBT after regenerating. |
| `BuildingSaveData` case per building | Same commit as the building. |
| Extend the vanilla/AM mob + copy the Unit boilerplate; don't inherit another faction's class (`instanceof VillagerUnit / IronGolemUnit / DrownedUnit` checks) | Same. Only `villagers/VindicatorUnit` is a safe base (Leafblade, Cutlass Raider and Reef Guard already extend it). |
| Death-FX faction field is 3 bits | `F_STORM = 6` (the last free value but one; 7 is the Artificers'): red-lacquer chips, gold sparks, a jade puff, vanilla `ELECTRIC_SPARK` / `END_ROD`. `N_STORM = 6` nanolathe tint (jade `0x4FD1A0`). |
| AM bodies: extend the AM class, clear its goals, register its renderer (Stag Lancer, Gull Spotter, Mudskipper, Mortar Galley) | Same (section 4). AM 1.22.9 is a hard dependency (`bar/build.gradle`, `compat/AlexsMobsCompat`). |
| Abilities that cost energy refuse when the pool can't pay and show the HUD warning (`CommanderDGun.ENERGY_COST`, `RaiseTidepool.castPool`) | Every Stormcaller spell pays through one place, `mana/ManaServerEvents` (section 3), so the cost, overcharge and mana-well rules are never re-implemented per ability. |
| Per-faction mechanics are gated by a FactionTraits flag, never `Factions.X ==` in hot loops (`tidal`) | `FactionTraits.mana` gates the Mana rules (`ManaServerEvents.isMana(owner)`). |

---

## 2. Slices

### Slice 0 - announce (this branch, `stormcallers-plan`)
- `Factions.STORMCALLERS` registered after the Tidewrought with `setPreview()` + `noCubeMap()`: not playable, not
  classic, not survival, never "Random". Icon: vanilla `red_glazed_terracotta` until a portrait exists.
- `FactionTraits.STORMCALLERS`: explicit red-lacquer/jade/gold look; commander kit, debris, quick-build and mechanics
  neutral (`PLAIN` D-gun, `NONE` signature, no formation/momentum/tidal, no extractor).
- `SkirmishServerboundPacket.CODE_PATHS` gains `"stormcallers"` = code 6 (rolls a live faction while preview).
- Lobby picker: nothing to change - `FactionPicker.lobbyEntries` / `skirmishEntries` list it greyed.
- Server guards: already in place (`startRTS`, `startRTSBot`, `StartPosServerboundPacket` refuse previews).
- Lang: `hud.faction.reignofnether.stormcallers` = "Stormcallers (preview)", tagline "Mana and spellcraft".
- GameTest `stormcallers_are_a_preview_and_the_five_live_factions_are_intact`; the skirmish-code round-trip test
  now pins code 6 too.

### Slice 1 - minimum playable Stormcallers + Mana core (L)
Goal: pick it, start, build an economy and a T1 army, cast spells that visibly drain the energy bar, flip
Overcharge and watch construction slow - and a bot can play it.

| Design unit | Body | Extend | Slice-1 mechanic |
|---|---|---|---|
| Commander - **Thunder Sage** (first Geomancer, tagged by `CommanderServerEvents.makeCommander`) | Geomancer body, gold-trimmed red robe livery | the Geomancer | `CommanderDGun.Kind.JUDGEMENT` - **Heaven's Judgement**: a lightning *column* at the aimed spot (within `RANGE` 14) instead of a line: 0.6 s telegraph (a gold glyph ring of particles - BAR rule, danger shown first), then every enemy within r2.5 takes the D-gun damage (80, commanders 25). Particle bolt from the sky (StormOak's `drawBolt` lifted into a shared `barfx/LightningFx.bolt`), no `LightningBolt` entity (no fire, no friend hit). Overcharged: r3.5. `CommanderAbility.Kind.BLINK` - **Blink**: `Teleport`'s targeting and range rules (10 blocks, line of sight), 60 energy, 12 s cooldown. *Ward* waits for slice 2. |
| Constructor - **Geomancer** | `Evoker`-proportioned humanoid on the Vindicator body like `SeedshaperUnit` (avoids villager professions), red robe + jade sash | thin copy of `tide/ShipwrightUnit` (WorkerUnit plumbing) | builds normally; **Ley Link** ability (section 3.4). |
| **Paper Crane** (scout) | AM `EntityShoebill` (an `Animal`; has `isFlying`/`setFlying`) + `RenderShoebill`, paper-white retexture with red seal marks | extend `EntityShoebill implements Unit`, flight plumbing and goal-clearing from `tide/GullSpotterUnit` (drop `ITargetsDroppedItems` behaviour: it must never pick up items) | fast flyer, sight 26, no attack, `registerScoutEntity`. Fallback body: white vanilla Parrot like the Owl Watcher if the shoebill's flight looks wrong at RTS scale. |
| **Blade Dancer** (raider) | Vindicator, red silk + twin sabres | `villagers/VindicatorUnit` (as Leafblade/Cutlass did) | fast melee; **Blink Strike**: `LeafDash`-style autocast onto the nearest enemy ranged unit 3-9 blocks away, but a teleport (no path) - 25 energy through `ManaServerEvents.tryCastQuiet` (autocast: no HUD spam), 10 s cooldown. |
| **Fire Adept** (skirmisher) | `Pillager` body with a fire fan | thin copy of `tide/HarpoonerUnit` (bow-cooldown goal) | small fireball every 2 s, range 16, 6 dmg + 2 s burn, `BarFx.K_FIREBALL` shot; **no terrain fire** (entity hit only; `TidepoolServerEvents.evaporate` on impact like the other fire attacks). Each shot is a *spell*: 2 energy through `ManaServerEvents.channelShot` - a starved Adept fires at half rate (section 3.2). |
| **Jade Guard** (line) | Vindicator + jade tower-shield, lacquered lamellar | `VindicatorUnit` (Reef Guard pattern: passive shield) | **Jade Mirror** (passive): projectile damage (`DamageTypeTags.IS_PROJECTILE`, the same test `BastionAegis` uses) is cut 40 %, and 30 % of the original is sent back to the shooter as `indirectMagic` damage if it stands within 24 blocks (one `LivingHurtEvent` handler; never reflects onto friends, never chains between two Guards). |
| **Wind Monk** (AA) | Vindicator-body monk with a fan staff (no weapon held), saffron + jade | thin copy of `verdant/WispChoirUnit` (the AA template) | gusts: `WispChoirUnit.FLYER_MULT` 3.0 / `GROUND_MULT` 0.5 through `getUnitAttackDamage`, flyer-first target priority in `MiscUtil`. Implements the new `WindAntiAir` marker (section 3.6: grounds the Celestial Dragon). 1 energy per gust. |

Buildings for slice 1:

| Building | Extend | Structure |
|---|---|---|
| Capitol - **Jade Pagoda** | copy of `tide/WreckHarbour` (TownCentre pattern; trains Geomancer, Paper Crane, Tier 2; hosts the **Overcharge** toggle button) | `jade_pagoda.nbt`: three-tier pagoda on a stone plinth - red-terracotta columns, jade roof tiers (waxed oxidised cut copper) with gold-block ridge ends, paper lanterns (lantern + red banner) |
| T1 lab - **Lotus Academy** | copy of `tide/Slipway` (Barracks pattern) | `lotus_academy.nbt`: walled courtyard with a training ring and a moon gate |
| Metal extractor T1/T2, wind, converter | `MetalExtractor("_storm")`, `WindGenerator("_storm")` (a wind-chime tower: red posts, gold bells, jade sails in `WindmillRenderClientEvents`), `EnergyConverter("_storm")` = **Mana Condenser** | `"_storm"` palette in `tools/gen_structures.py` (section 6) |
| Storage - **Mana Cistern** | storage vault variant (`STORAGE_VAULT_STORM`) with an energy-heavy split (section 3.3) | `"_storm"` vault |

Wiring = verdant_court_plan.md section 3 checklist items 1-11 with `STORMCALLERS` in place of `VERDANT_COURT`:
drop `setPreview()`, calm theme (vanilla `music.overworld.cherry_grove` as a sounds.json event reference, like the
Court's flower-forest), custom-building flag, register units/buildings in the standard slots (capitol `abilitySlot1`,
T1 lab `abilitySlot6`, economy `hotkey5/6/7`, storage `hotkey8`), bot kit + parse, death debris / nanolathe, lang,
skins (`tools/gen_storm_skins.py` on the Court's toolkit), portraits, stinger. Plus the Mana core (section 3):
`mana/ManaServerEvents`, `mana/LeyNetwork`, `FactionTraits.mana`, the `EconomyServerEvents` hooks, Overcharge sync.

#### As built - Mana core (branch `stormcallers-mana-core`)
The mechanic, its API, the economy hooks and the HUD are in; units and buildings build on them. Everything below is
documented again at the top of `mana/ManaServerEvents`.

```
// one-shot spell: pays cost x costMult at once or refuses (0, nothing paid - don't start the cooldown)
float power = ManaServerEvents.tryCast(caster, owner, 150);   // HUD "not enough energy" on refusal
float power = ManaServerEvents.tryCastQuiet(caster, owner, 25);   // autocasts / bots: no HUD warning
ManaServerEvents.lastRefusal();          // Refusal enum: NONE, NO_POOL, NOT_ENOUGH_ENERGY, NOT_MANA, ... (.langKey)
// caster attack: pays what is there, never refuses
float paid = ManaServerEvents.channelShot(attacker, owner, 2);
cooldown *= ManaServerEvents.shotCooldownScale(paid);         // 1 paid ... 2 starved (half rate)
// sustained spell
ManaServerEvents.Channel ch = ManaServerEvents.openChannel(caster, owner, 12);   // null = refused
float f = ManaServerEvents.channel(ch);  // once per second; ch.isActive() false once a second can't be paid at all
ch.power(); ch.fraction(); ch.stop();
// multipliers and rules
ManaServerEvents.spellCostMultiplier(owner) / spellPowerMultiplier(owner);   // 2 / 1.5 overcharged, else 1
ManaServerEvents.spellPowerAt(owner, pos) / spellCostAt(owner, pos);        // Mana Well factor, 1.0 until slice 3
ManaServerEvents.castPower(owner, pos);  // Overcharge x well - what tryCast returns
ManaServerEvents.isMana(owner); isOvercharged(owner); setOvercharge(owner, on); toggleOvercharge(owner);
ManaServerEvents.registerWell(owner, centre) / unregisterWell / insideWell(owner, pos);   // slice-3 registry, ready
LeyNetwork.link(owner, a, b) / unlink / isLinked / linksOf / onBuildingGone / incomeMultiplier (1.0 stub)
```

- **Paying**: every entry ends in `EconomyServerEvents.drawSpell(owner, energy, allOrNothing)`: taken at once, not
  throttled by the stall (so spells are paid before construction), and the whole wish is added to `energyDemand`
  (a starved channel keeps the stall down instead of construction grabbing the trickle it waits for). Shown as energy
  expense and as `PlayerEconomy.manaDrain` (the tooltip's "mana drain" line). Owners without a pool: casts refused
  (`NO_POOL`, the D-gun's rule), shots/channels free.
- **Overcharge**: per-player `Set` in `ManaServerEvents`; `ManaServerboundPacket(TOGGLE_OVERCHARGE)` (server re-checks
  Mana faction and energy, shows the refusal); keybind `key.reignofnether.overcharge` (default `\`) and a toggle chip
  under the energy bar, both only for a Mana player (`ManaClientEvents`). While on: cost x2 / power x1.5,
  `EconomyServerEvents.updateStall` caps the stall at 0.5 (`capBuildSpeed`), `convertEnergy` skips the player.
  **Auto-off the moment energy hits 0** (simpler than the 3 s/<5 % rule above; refused to switch on at 0 energy),
  plus on defeat (`clearOwner`) and reset (`clearAll`). Synced as `overcharge` (and `manaDrain`) in the existing
  `EconomyClientboundPacket`; the HUD pulses a gold OVERCHARGE frame on the energy bar.
- **Converters**: `FactionTraits.converterThreshold` (default 0.5 = `CONVERSION_THRESHOLD`), Stormcallers 0.75.
- **FactionTraits**: `.mana()` + `.converterThreshold(0.75f)` on `STORMCALLERS`; nobody else changes. The existing
  energy abilities (`CommanderDGun`, `RaiseTidepool.castPool`) still pay directly: routing them through `tryCast`
  would add their cost to the stall demand, which changes behaviour, so they stay as they are.
- Faction lookups are cached per owner (`traitsOf`), never per call; `bindTraits(owner, traits)` gives pool owners
  without an RTSPlayer (GameTests, scenarios) a faction.
- GameTests: `mana_cast_pays_energy_and_refuses_when_short`, `mana_channel_is_paid_before_construction_and_stalls_it`,
  `overcharge_doubles_cost_empowers_caps_build_and_pauses_converters`, `mana_rules_leave_other_factions_unchanged`.

#### As built - slice 1 (branch `stormcallers-slice-1`)
Live faction (skirmish code 6, "Random", bots), T1 only. What differs from the table above, and why:
- **Thunder Sage**: `CommanderDGun.Kind.JUDGEMENT` - `CommanderDGun.judgement(sl, self, owner, aim, quiet)` pays 150
  through `ManaServerEvents.tryCast`/`tryCastQuiet` (refused = nothing paid, nothing scheduled, no cooldown), pulls the
  aim in to 14, pulses a gold dust ring for `JUDGEMENT_DELAY` (12 ticks) and then hits every enemy within r2.5 (r3.5
  when the cast came back with Overcharge power) with the D-gun damage; `barfx/LightningFx.bolt` is the shared bolt
  (the Storm Oak now draws through it too). `CommanderAbility.Kind.BLINK` is the one *aimed* signature: the button arms
  the cursor with `COMMANDER_ABILITY` and the click's spot reaches `use(level, unit, pos)` through UnitActionItem's
  generic dispatch; `CommanderAbility.blink` finds the spot first (`mana/Blink`: standable, in line of sight, shortened
  along the line when the aimed spot fails), then pays 60; its own 12 s cooldown instead of the signature's 45.
- **Geomancer**: a copy of the Shipwright minus amphibious; **no Ley Link button yet** (`LeyNetwork` stays the stub).
- **Paper Crane**: `EntityShoebill` with the Gull Spotter's flight plumbing and AI cleared (`canTargetItem` false, no
  pick-up, no feeding); `PaperCraneRenderer extends RenderShoebill` with our own skin painted on the shoebill's UV
  outline (`tools/gen_storm_skins.py` reads only AM's alpha; NOTICE.md).
- **Blade Dancer**: `BlinkStrike` (UnitAction `BLINK_STRIKE`): manual on an enemy, and an always-on autocast from the
  Dancer's tick every 10 ticks while it has a target - the nearest enemy *ranged* unit (attack range >= 5, not a flyer)
  3-9 blocks away, `tryCastQuiet(25)`, and only while the owner keeps 150 energy over the cost (the Judgement reserve).
- **Fire Adept**: hit-scan (the Flak Swivel's frame, a BarFx K_FIREBALL shot/impact for the look): 6 dmg mob-projectile
  hit + 2 s entity burn, `TidepoolServerEvents.evaporate` at the target, never a fire block. Each shot pays 2 through
  `channelShot`; `getAttackCooldownMultiplier` is scaled by `shotCooldownScale(paid)` and the goal's cooldown is reset
  right after paying, so a starved shot's wait is already the doubled one. 45 HP (not 38: the balance audit's T1 HP
  median).
- **Jade Guard**: `StormUnitEvents` (LivingHurtEvent) -> `JadeGuardUnit.mirror`: projectile damage x0.6 through, 30 %
  of the hit back to an *enemy unit* shooter within 24 as indirect magic (not a projectile, so no Guard-to-Guard chain).
  No flat ranged resist on top. Melee untouched.
- **Wind Monk**: the Flak Swivel's AA rules (x3 flyers / x0.5 ground, flyer-first in MiscUtil) on an empty-handed
  pillager body, 1 energy per gust through `channelShot` (half rate starved, like the Adept). `unit/interfaces/WindAntiAir`
  marks it, the Wisp Choir, the Flak Swivel and the Windcaller.
- **Buildings**: Jade Pagoda (capitol; Tier 2 = extractor refit only, no Tier 3), Lotus Academy, `_storm`
  extractor/T2 extractor/wind-chime tower (stripped cherry hub: jade vanes on red spars)/Mana Condenser, and the Mana
  Cistern = `StorageVault("_storm", 1000, 4500)` (the vault's tooltip and its storage-lost rule now read the building's
  own split). **Overcharge** has no building button: the HUD chip and keybind (ManaClientEvents) already serve every
  Mana player whatever is selected.
- **Bot** (`stormHabits`, gated by `ManaServerEvents.isMana`): Judgement at the densest clump (2+ foes in one column)
  within 14, quiet, with a 150 (hard) / 300 reserve; Blink toward home under 30 % HP when hit in the last 3 s;
  Overcharge on while the army fights (3+ foes and 4+ of ours within 16 of its centre) with energy > 60 % of storage,
  off when the fight ends or energy < 20 %. Wind Monks via `queueAntiAir`.
- Costs (tools/balance_audit.py: no new outliers; the T1 HP/100m median moves 67 -> 65): Geomancer 50m/70e, Paper Crane 30/40, Blade Dancer 80/100
  (52 HP), Fire Adept 90/170, Jade Guard 100/120 (6 dmg: at 5.5 the first duel run had it losing to every melee line but the Reef Guard), Wind Monk 90/150, Jade Pagoda and Lotus Academy at the other
  capitols'/labs' prices.
- GameTests: `stormcallers_are_live_and_the_five_other_factions_are_intact`,
  `stormcallers_capitol_places_and_the_thunder_sage_spawns`, `stormcallers_units_are_made_and_attack`,
  `heavens_judgement_hits_foes_in_the_column_not_friends` (also refusal without energy, and Blink),
  `fire_adept_fires_slower_without_energy`, `jade_guard_reflects_projectiles_to_the_shooter_not_friends`,
  `blade_dancer_blinks_onto_an_enemy_archer`; the bot soak gains a fifth (Stormcallers) base; the duel matrix's T1
  rows gain the Jade Guard and the Fire Adept. Not built in this slice: the Ley Link order and resonance, the overcharge
  auto-off "3 s" rule (the core switches off at 0, see above), the `ley_link_*` test.

Slice-1 GameTests as planned (all in the dry ARENA template, unique owner names, no global counts):
- `stormcallers_are_live_and_the_five_other_factions_are_intact` (the slice-0 test grows, as the Tidewrought's did).
- `stormcallers_capitol_places_and_the_thunder_sage_spawns`, `stormcallers_units_are_made_and_attack`.
- `heavens_judgement_hits_foes_in_the_column_not_friends` (and nobody outside r2.5; telegraph delay respected).
- `mana_cast_pays_energy_and_refuses_when_empty` (refused cast costs nothing, cooldown not started).
- `overcharge_doubles_cost_empowers_and_stalls_construction` (stall capped while on, converters paused, auto-off
  after 3 s at zero energy).
- `ley_link_boosts_linked_generators_and_respects_caps`.
- `jade_guard_reflects_projectiles_to_the_shooter_not_friends`.

### Slice 2 - rest of T1 + Ward (M)
| Unit | Body | Extend | Mechanic |
|---|---|---|---|
| **Rocket Battery** (artillery) | `Pillager` crew + rocket rack | thin copy of `tide/BombardCrewUnit` (bow-cooldown goal + `BombardShell`-style solved arc) | volley of 4 small rockets (`BarFx.K_ROCKET`) spread over r3 every 6 s, range 24, min range 7, 5 splash dmg each, no terrain damage; Attack Ground. Not a spell (gunpowder, no energy) - the faction's one energy-free damage dealer, so a stalled Stormcaller army still has artillery. |
| **Ink Scribe** (support) | `Witch` body with a brush and scroll | thin copy of `verdant/MoonwellBearerUnit` (non-attacker) | **Seal** (aimed, 10 blocks, enemy unit, 60 energy, 20 s cooldown): the target is *sealed* for 6 s - a new `MobEffect` `SEALED` in `registrars/MobEffectRegistrar` (vanilla effect sync gives clients the icon and an ink-glyph particle for free; Bloom Priestess' cleanse removes it like any harmful effect). Section 3.5 lists where a seal is enforced. |
| **Frost Adept** (slow + ice) | `Pillager`/`Stray`-like humanoid with an ice staff | thin copy of the Fire Adept | frost bolt 4 dmg + Slowness II 2 s, 2 energy/shot. **Freeze Ground** (aimed, 10 blocks, r2, 15 s, 50 energy, 20 s cooldown): a temporary **frost sheet** (section 3.7) - enemies walking on it are slowed and slide. |
| Thunder Sage **Ward** | - | new ability with a server area record, like `tide/FogBankServerEvents` | a 5-block sigil on the ground for 8 s: friendly units inside take 30 % less spell/projectile damage and are immune to `SEALED`; channelled at 12 energy/s (stops early when starved); 40 s cooldown. Commander's third button (Fog Bank precedent: `CommanderAbility` holds one Kind, extra powers are their own abilities). |

Bot: Ink Scribes seal the priciest enemy caster/commander in reach; Frost Adepts freeze the ground in front of an
advancing enemy group; Overcharge on while an army fight is in progress and nothing is under construction (section
3.8), off otherwise.

#### As built - slice 2 (branch `stormcallers-slice-2`)
The rest of T1 at the Lotus Academy (slots 5-7), the commander's Ward and the Geomancer's Ley Link. What differs from
the table above, and why:
- **Seal** (`ability/abilities/Seal`, `mana/SealServerEvents`): 25 energy (not 60 - a silence that cost more than a
  Blink was never cast in the duel runs), 20 s cooldown, 10 blocks; `MobEffectRegistrar.SEALED` (6 s, commanders 3 s,
  x1.5 overcharged). Enforced in `UnitActionItem`'s ability dispatch (one HUD line per refused order), the Blink Strike
  autocast, the Kraken Caller's auto-grab, the Ward's channel (a sealed Sage's ward collapses) and every bot casting path
  (`BotPlayer.sealed`). Refused on friends and on units inside a friendly Ward; nothing paid when refused.
- **Frost Adept** (`FrostAdeptUnit`): the Fire Adept's frame - 4 dmg hit-scan bolt + Slowness II 2 s, 2 energy a shot,
  half rate starved. **Frost Field** (`ability/abilities/FrostField`, `mana/FrostServerEvents`, `blocks/FrostSheetBlock`):
  50 energy, 20 s, r2 for 15 s (x1.5 overcharged); a no-collision 1/16 ice skin laid by the tidepool registry pattern
  (3 fields per player, 600 cells per level, 1 Hz expiry, scheduled-tick fail-safe, fire melts it, cleared on
  defeat/reset). A 10-tick pass gives enemy ground units standing on a cell Slowness I. No pathing kind: a no-collision
  cell classifies as plain land, so there is nothing to reclassify.
- **Rocket Battery** (`RocketBatteryUnit`): the Bombard Crew's frame on `BombardShell` - a volley of 4 shells 3 ticks apart
  (the first on the aim, the rest within r3), 5 splash each, every 6 s, range 24, dead zone 7; `BarFx.K_ROCKET` trail per
  rocket; no energy. 38 HP, 110m/80e.
- **Ward** (`ability/abilities/Ward`, `mana/WardServerEvents`): the Thunder Sage's third button via
  `CommanderServerEvents.ensureAbility`. Instant, where the Sage stands: r5 (x1.5 overcharged) for 8 s; friends inside
  take 30 % less projectile *and* magic damage and can't be sealed (a seal is lifted as they enter); `openChannel` at 12/s,
  `channel` once a second from the ward's own tick, collapses when a second can't be paid at all, when the Sage dies or
  is sealed. 40 s cooldown, started only when the ward rises.
- **Ley Link** (`ability/abilities/LeyLink`, `mana/LeyNetwork`): on the Geomancer's bar (hotkey8). Two clicks on own
  completed buildings within 16; the server remembers the first per Geomancer for 30 s; the client re-arms the cursor
  for the second click (`ManaClientEvents.onClientTick`). Linking a linked pair unlinks. **Resonance** is live:
  `BuildingPlacement.getEnergyIncome` multiplies a wind generator's income by `LeyNetwork.incomeMultiplier` (+10 % per
  other generator in the connected network, cap +30 %); the 1 s income pass picks it up. Caps 24/player, 3/building;
  `BuildingPlacement.destroy` drops a building's links. Beams: one `BarFx.beam` (the nano-beam event in the jade tint)
  per link every 40 ticks. Still not saved.
- **Bot**: army mix gains a Frost Adept, a Rocket Battery and an Ink Scribe; Scribes follow the army like healers and seal
  a commander first, else the priciest enemy with an ability in reach (175 reserve); a Frost Adept freezes the ground a
  step in front of an enemy group (3+) pushing the army (200 reserve); the Sage wards when hit in the last 3 s with 2+
  foes within 12 (250 reserve); an idle Geomancer links the nearest unlinked pair of home wind generators once there are
  three, one link per 10 s.
- Costs (balance audit: no new outliers): Ink Scribe 60m/30e (= Moonwell Bearer), Frost Adept 80m/60e, Rocket Battery
  110m/80e.
- GameTests: `seal_blocks_an_enemys_abilities_for_its_duration_not_a_friends`,
  `frost_field_slows_foes_not_friends_and_melts_away`, `rocket_battery_volley_hits_its_ground_target`,
  `ward_softens_projectiles_on_friends_inside_and_drains_energy_each_second`,
  `ley_link_raises_linked_wind_income_and_respects_caps`.

### Slice 3 - T2 (L-XL)
T2 lab **Celestial Observatory** (`abilitySlot9`, needs Lotus Academy + Tier 2, `StormT2Prod` server re-check like
`VerdantT2Prod`/`TideT2Prod`). T2 defence: none in the design list - the Lantern Fortress is the shield, the Seer
the sensor; if a static tower is wanted later, a **Lightning Pagoda** on `StormOakPlacement` (shared bolt code).

| Unit | Body | Extend | Mechanic |
|---|---|---|---|
| **Ley Master** (T2 constructor) | Geomancer body, gold robes, floating jade discs | copy of `tide/HarbourmasterUnit` (extends the Geomancer); add to `T2Workers.isT2Worker` | 2x build power/HP, 160/80/40. Builds **Mana Wells** (section 3.4): small 3x3 buildings, cap 4 per player. |
| **Thunder Dragon** (super flyer) | AM `EntitySpectre` (`Animal`, `FlyingAnimal` - a single entity, no parts) recoloured jade-and-gold with a whisker/mane layer; custom serpent model later | extend `EntitySpectre implements Unit, AttackerUnit` with the Sky Corsair's flight rules (fixed altitude, `isFlyingUnit`) | **Chain lightning** every 3 s: 14 dmg to the target, then jumps to up to 3 more enemies within 5 blocks of the last hit (`UnitGrid.near`, no allocation: reuse a scratch list), x0.7 per jump; `LightningFx.bolt` between links. 10 energy per strike. |
| **Earthshaker** (stone golem) | vanilla `IronGolem` model retextured as a jade-veined stone golem (**not** `IronGolemUnit`: Sunforged research and `instanceof IronGolemUnit` checks) | extend `IronGolem` + copy the Unit boilerplate (the Sentinel Treant extends `IronGolemUnit` and inherits Sunforged golem rules - don't repeat that) | slow heavy melee; **Rift** (aimed, 10-block line, 40 s cooldown, 80 energy): `MagmaRupture`'s fixed-at-cast telegraphed crack without lava - after 1 s, 20 dmg + a vertical knock-up to enemies on the line. No terrain change (a "split" that edits blocks would break pathing and buildings). |
| **Seer** (sensor) | `Evoker` body with a lantern staff (alt: AM `EntityFarseer`, which flies via `FlyingPathNavigation`, if lovish wants an alien look - it doesn't match the dynasty) | thin copy of `verdant/MoonwellBearerUnit` (non-attacker) | sight 36; every 10 ticks lifts cover from concealed enemies within 14 (`Concealment.revealFor`, 3 s - the Lighthouse Battery's hook, so cloak and thickets/fog banks both fail near it). **Radar** (enemy blips beyond sight) needs a minimap/fog channel RoN doesn't have - section 7; slice 3 ships the reveal only. |
| **Gravity Monk** (pull) | Vindicator-body monk, violet-and-gold | thin copy of `tide/KrakenCallerUnit` (non-melee caster) | **Singularity** (aimed, 12 blocks, 70 energy, 25 s cooldown): a 1 s telegraphed vortex, then every enemy within r6 is hopped toward the centre (`HarpoonerUnit`'s pull maths, `canPull` size cap: no T3 / wide bodies) and slowed 2 s. Sets up Heaven's Judgement and Rocket Battery volleys. |
| **Lantern Fortress** (area shield) | AM `EntityElephant` (already in `alexs_mobs_is_loaded`) carrying a lantern howdah layer | extend `EntityElephant implements Unit` (clear taming/riding/trunk goals; `TamableAnimal` - the Mudskipper precedent) | **Lantern Dome**: `BastionAegis.Dome`, but anchored to the walker and *channelled* - while toggled on it drains 15 energy/s; projectile damage to friends inside r6 is soaked up to 300 per raise; starved -> the dome collapses and re-raises only after 5 s. |
| **Phoenix** (flyer, rises once) | AM `EntitySunbird` (`FlyingAnimal`; a sun-bird, the right silhouette) | extend `EntitySunbird implements Unit, AttackerUnit` | fire dive (6 dmg + burn) every 2 s, 4 energy per dive. **Rebirth**: the first lethal hit is cancelled (`LivingDeathEvent`, a once-flag in persistent data), the Phoenix drops to the ground as an **ember** for 6 s (0.0 speed, 30 HP, no attack - killing the ember denies the rebirth), then rises at 50 % HP. The Gravebound Deathless Champion (design, not built) has the same rule - write the helper once. |
| **Mirror Mage** (decoys) | `Evoker` body with a bronze mirror | thin copy of `villagers/EvokerUnit` minus its spells | **Mirror Images** (target a friendly T1/T2 ground unit within 10, 90 energy, 30 s cooldown): 3 copies of the same entity type for 12 s, tagged `bt_decoy` - 1 HP, deal no damage, no wreck, no population, not in score, discarded on chunk reload (the Awaken Thicket summon rules). Enemy clients cannot tell them apart; allies see a faint shimmer. Never copies commanders, T3s or flyers. |

### Slice 4 - T3 (M)
T3 lab **Heaven's Gate** (needs Tier 3 + a Ley Master as builder, `T2Workers.isT3Lab`, `ResearchTier3`).

**The Celestial Dragon** - storm serpent, an air unit. Body: interim a scaled (x2) `EntitySpectre` with a long
serpent-tail render layer; AM `EntityVoidWorm` has the right serpent shape but is a **boss** (`ServerBossEvent`
bar) and **multipart** (`void_worm_part` entities) - only adopt it after a spike shows RoN selection, hitboxes,
fog and the unit grid cope with parts (section 7).
- **Tempest** (aimed, 18 blocks): a roaming storm (server record `{owner, centre, velocity, ticksLeft}`, r6) that
  drifts toward the aimed spot at 0.15 blocks/tick for **10 s**; every 10 ticks it strikes one enemy inside (most
  clumped, StormOak's `pickTarget` rule) for 18 dmg with `LightningFx.bolt`. Friends never; no fire, no terrain.
- **Cost is the weakness**: 400 energy up front *and* 60 energy/s live while the storm rolls (`ManaServerEvents.channel`
  at spell priority, so it stalls its own side's construction - exactly the design's "can stall its own side").
  Starved, the storm fizzles. 45 s cooldown.
- **Grounded by wind**: hits from `WindAntiAir` units (Wind Monk, Sunforged Windcaller's gust, Wisp Choir, Flak
  Swivel - the list lives on the marker) accumulate; 40 such damage within 5 s grounds the dragon for 8 s - it
  drops to ground altitude (melee can reach it), takes +50 % damage, moves at half speed and cannot cast. Telegraph:
  storm-cloud particles thin out as it nears the threshold.
- Price per the T3 rules: ~5x a T2 heavy; no instant-win; loses to an equal-cost T2 army with Wind-type AA.

---

## 3. The Mana mechanic (slice 1 core)

### 3.1 Energy is mana - no new resource
Mana is the existing energy pool (`Resources.wood`, `EconomyServerEvents`). No new bar, packet field for a second
resource, or save data: what the player already reads as energy income/expense/storage is their mana. The design
consequence BAR players will recognise: the Stormcallers want **lots of energy** (generators, the Ley Shrine capture
site's +30 e/s, Mana Cisterns, ally overflow) and spend it on spells instead of converting it to metal.

`FactionTraits.mana` (Builder `.mana()`) marks the faction; `ManaServerEvents.isMana(ownerName)` reads it through
`PlayerServerEvents.getRTSPlayer(owner).faction` (cached per player, not per call).

### 3.2 How spells pay - `mana/ManaServerEvents`
Three entry points, so no ability talks to `Resources` directly:

| Call | Used by | Rule |
|---|---|---|
| `float tryCast(caster, owner, baseEnergy)` (`tryCastQuiet` = no HUD warning) | one-shot spells (Heaven's Judgement, Blink, Seal, Freeze Ground, Singularity, Mirror Images, Tempest's up-front cost) | Pays `baseEnergy x costMult` at once or refuses (returns 0, nothing paid, cooldown untouched, HUD "not enough energy" - the D-gun's behaviour). Returns the **power multiplier** to apply to damage/radius/duration. |
| `float channelShot(attacker, owner, energyPerShot)` | caster attacks (Fire/Frost Adept, Wind Monk, Thunder Dragon, Phoenix) | Pays what the pool has; returns the fraction paid (0-1). The unit's attack cooldown is divided by `max(0.5, fraction)`: a starved caster fires at half rate rather than not at all. |
| `Channel openChannel(source, owner, energyPerSecond)` + `float channel(handle)` | sustained spells (Ward, Lantern Dome, Tempest's drain) | `openChannel` pays the first second in full or refuses (null). Then `channel(handle)` once per second per source (not per tick) pays up to one second's drain and returns the fraction. Below 1 the spell weakens; at 0 the handle stops itself (`isActive()` false) and the spell collapses. |

All three end in one new economy call, `EconomyServerEvents.drawSpell(owner, energy)`: it takes energy **directly**
from the pool (clamped to what is there, never negative) *and adds it to `energyDemand` and `energySpentWindow`*.
That is the "spells drain energy live" rule: spells are paid first (before construction asks for its tick's
share), and because their draw counts as demand, the next tick's `stall` drops - the player's construction and
production slow evenly, exactly as BAR stalls. The HUD's energy expense already shows it. No per-tick allocation:
one float subtraction per cast/shot, one per sustained spell per second.

`power(owner, pos)` = `(overcharged ? OVERCHARGE_POWER : 1) x (inside an own/allied Mana Well ? WELL_POWER : 1)`;
`costMult(owner, pos)` = `(overcharged ? OVERCHARGE_COST : 1) x (in a well ? WELL_DISCOUNT : 1)`.

### 3.3 Overcharge, converters, storage, overflow

| Piece | Rule | Where |
|---|---|---|
| **Overcharge** (per player toggle) | Button on the Jade Pagoda and the Thunder Sage (hotkey). While on: every spell costs x2 (`OVERCHARGE_COST`) and has x1.5 power (`OVERCHARGE_POWER`; each spell documents what scales - damage, radius or duration); **construction stalls**: the player's `eco.stall` is capped at `OVERCHARGE_BUILD_CAP` = 0.5 even with a full bank (one `Math.min` in `onServerTickEnd`, after the normal stall maths). That is the design's trade: overcharge in a fight, not while you expand. Auto-off after 3 s at < 5 % energy (no silent soft-lock of a broke player) and on defeat/reset. | `ManaServerEvents.overcharged` (a `Set<String>` of owners), `EconomyServerEvents.onServerTickEnd` |
| Sync | One `boolean overcharge` added to `EconomyClientboundPacket` (already sent 4x/s): the HUD energy bar pulses gold, the button shows its state. No new packet. | `EconomyClientEvents`, resources HUD |
| **Converters** (Mana Condenser) | A Stormcaller converter only converts above **75 %** of energy storage (others: `CONVERSION_THRESHOLD` 50 %), so it never eats the spell reserve; while Overcharge is on, it doesn't convert at all. `convertEnergy` reads a per-faction threshold (`FactionTraits.converterThreshold`, default 0.5). | `EconomyServerEvents.convertEnergy` |
| **Storage** (Mana Cistern) | Vault variant +1000 metal / **+4500 energy** (the normal vault is +2000/+3000) - the Stormcallers bank mana, not metal. A bigger pool is a longer overcharge window. Losing a Cistern loses the energy held in it (existing `onStorageLost` rule) - raiding Cisterns is the counterplay. | storage vault `_storm` variant |
| **Overflow** | Unchanged: income that doesn't fit is shared with allies, then wasted. Nothing special in slice 1 - allies' overflow already flows *into* a Stormcaller's big pool, which is the team synergy. Tuning knob for later (not in slice 1): "Brimming" - spells +15 % power while energy is above 90 % of storage. | - |

### 3.4 Ley-links (Geomancer) and Mana Wells (Ley Master)
**Ley Link** (Geomancer ability, two clicks: building A then building B, both own, completed, within 16 blocks of
each other; free, 2 s cooldown; clicking a linked pair again unlinks). `mana/LeyNetwork` keeps per-owner link
records `{long posA, long posB}`; a network is a connected set of linked buildings. "Share energy" means:
- **Ley resonance**: every Stormcaller wind generator in a network gets +10 % energy income per *other* generator in
  the same network, capped at +30 % (read in the `_storm` `WindGenerator.getEnergyIncome` override; the 1 s
  `recalculateIncomeAndStorage` pass picks it up - no per-tick work).
- **Priority draw**: sustained spells cast from a building (later: a Lightning Pagoda) inside a network that holds
  the capitol are paid without the stall throttle.
- Caps: 24 links per player, 3 per building; links die with either building (`Building.destroy` hook) and on defeat.
  A thin jade particle beam per link every 40 ticks (server particles, visible-chunk only).
- Not saved in slice 1 (re-link after reload); saving rides `BuildingSaveData` later if playtests want it.

**Mana Well** (Ley Master, slice 3): a cheap 3x3 building (`mana_well.nbt`: jade basin, gold rim, soul lantern),
cap 4 per player. Inside r10 of a completed own/allied well: spells `WELL_POWER` x1.25 and `WELL_DISCOUNT` x0.8.
Wells don't stack. Lookup = per-owner list of well centres (<= 4 per player, a distance check each), no grid.

### 3.5 Seal (Ink Scribe) - where it is enforced
`SEALED` is a `MobEffect`, so it saves, syncs and expires through vanilla. Checks (all server side):
`UnitActionItem` ability dispatch (the `ability.use(...)` calls ~497-509 refuse a sealed caster, with a HUD
message); autocast and passive casts (Leaf Dash / Blink Strike autocasts, Hive Keeper bees, Bastion Aegis, the
Harpooner's every-third pull, Kraken autocast) call one `SealServerEvents.isSealed(entity)` guard; channelled
spells end when their caster is sealed. A sealed unit still moves and uses its basic attack ("silenced", not
stunned). Commanders: half duration (3 s), so a seal can't lock out a D-gun for long.

### 3.6 Wind-type AA marker
`unit/interfaces/WindAntiAir` (empty marker): Wind Monk, Windcaller (gust), Wisp Choir, Flak Swivel. Read only by
the Celestial Dragon's grounding counter (a `LivingHurtEvent` handler on that one entity type) - no global cost.

### 3.7 Frost sheet (Frost Adept, slice 2) - the Tidepool registry pattern
Not vanilla ice (a full block that would replace terrain, melt into water and break pathing). `blocks/FrostSheetBlock`:
a 1/16 layer on top of the ground (like `WraithSnowLayerBlock`), no collision, replaceable, friction 0.98 (entities
stand on the layer, so its friction applies), never placed in buildings or on metal patches. Registry
`mana/FrostServerEvents` = a copy of `tide/TidepoolServerEvents`' shape (pool records, per-player cap 3, per-level
cell budget, 1 Hz expiry, scheduled-tick fail-safe that survives reloads, cleared on defeat/reset; fire evaporates
it). A 10-tick pass applies Slowness I to enemy units standing on it (Tides-buff pattern: iterate unit lists, act on
state changes only). Pathing: `WalkabilityBuilder.KIND_FROST` (walkable for everyone, cost 1.5 for non-Stormcallers,
mirroring `KIND_TIDEPOOL`) so the dirty-chunk reclassify lag can never strand a unit.

### 3.8 Bot
`BotPlayer.kitFor(STORMCALLERS)`: Jade Pagoda / Lotus Academy / `_storm` eco / Mana Cistern; army mix Jade Guard +
Fire Adept + Blade Dancer, Wind Monks via `queueAntiAir`. `stormHabits`: Thunder Sage fires Judgement at the densest
enemy clump in range (StormOak's `pickTarget`), Blinks out under 30 % HP; Overcharge on when 6+ own army units are
within 16 of an enemy and the build queue is empty, off when the fight ends; Geomancers link generators in pairs
after the third wind generator. Keep an energy reserve of 150 (a Judgement) before spending on Blink Strike.

---

## 4. Alex's Mobs bodies (checked against the AM 1.22.9 jar: `AMEntityRegistry` ids and superclasses)

AM has **no crane, dragonfly or dragon**. What fits:

| AM entity | Superclass | Fits | Notes |
|---|---|---|---|
| `SHOEBILL` (`EntityShoebill`, `RenderShoebill`) | `Animal` (`isFlying`/`setFlying`) | **Paper Crane** | a stork-like wader that flies - closest to a crane; retexture paper-white; it implements `ITargetsDroppedItems` - clear that goal |
| `SPECTRE` | `Animal`, `FlyingAnimal` (single entity) | **Thunder Dragon**, interim **Celestial Dragon** (x2 + tail layer) | a sky manta: a flying silhouette with no parts, safe for RoN selection |
| `SUNBIRD` | `Animal`, `FlyingAnimal` | **Phoenix** | AM's sun-bird; drop its slow-falling aura effect |
| `ELEPHANT` | `TamableAnimal` | **Lantern Fortress** | already proven loaded (`alexs_mobs_is_loaded`); clear tame/ride/trunk goals |
| `VOID_WORM` | `Monster`, boss bar, multipart | Celestial Dragon (only after a spike) | boss event and `void_worm_part` entities: see section 7 |
| `FARSEER` | `Monster`, `FlyingPathNavigation` | alt **Seer** | alien look; off-theme for a mage dynasty |
| `GUSTER` / `gust` | `Monster` / projectile | Wind Monk gust or Tempest visual reference | particles are cheaper; reference only |
| `FROSTSTALKER` | `Animal` | Frost Adept summon (optional, later) | an ice wolf |
| `BALD_EAGLE`, `HUMMINGBIRD`, `CROW`, `BLUE_JAY`, `TOUCAN`, `POTOO` | mixed | spares (critters, survival-wave flavour) | `BALD_EAGLE` is `TamableAnimal` with falconry |

Humanoid casters (Geomancer, Thunder Sage, Blade Dancer, Fire/Frost Adept, Jade Guard, Wind Monk, Rocket Battery,
Ink Scribe, Seer, Gravity Monk, Mirror Mage, Ley Master) stay on vanilla Vindicator/Pillager/Witch/Evoker bodies with
drawn robes (`tools/gen_storm_skins.py`), as the Court and the Tidewrought do. Earthshaker = retextured vanilla Iron
Golem model on its own class. Pattern for every AM body: `class XUnit extends EntityAm implements Unit...`, override
`registerGoals` to drop AM AI, register AM's `RenderX::new` in `ClientModEvents`, add the id to `compat/AlexsMobsCompat`
if read directly, and add the type to `alexs_mobs_is_loaded` when a unit depends on it.

---

## 5. Which existing classes each piece builds on (summary)

| Piece | Builds on |
|---|---|
| Heaven's Judgement | `CommanderDGun` (new `Kind.JUDGEMENT`; telegraph timing from `MagmaRupture`, bolt from `StormOakPlacement.drawBolt`) |
| Blink, Blade Dancer's Blink Strike | `Teleport` (targeting/range), `LeafDash` (autocast target pick) |
| Ward | `tide/FogBankServerEvents` area record + `BastionAegis` damage hook |
| Fire / Frost Adept, Rocket Battery | `tide/HarpoonerUnit`, `tide/BombardCrewUnit` + `BombardShell` |
| Jade Guard | `VindicatorUnit` + `ReefGuardUnit` passive shield; `BastionAegis`' `LivingHurtEvent` projectile test |
| Wind Monk | `verdant/WispChoirUnit` (`FLYER_MULT`/`GROUND_MULT`, `isFlyer`) |
| Ink Scribe (Seal) | `MoonwellBearerUnit` body; new `SEALED` effect in `MobEffectRegistrar` |
| Frost sheet | `tide/TidepoolServerEvents` registry + `TidepoolBlock` lifecycle; `WraithSnowLayerBlock` shape |
| Seer | `Concealment.revealFor` (Lighthouse Battery / Holy Bell hook) against `ShadeRangerUnit` cloak and `ThicketCover` |
| Gravity Monk | `HarpoonerUnit` pull maths + `canPull`; `KrakenCallerUnit` caster frame |
| Lantern Fortress | `BastionAegis.Dome` (anchored to the walker, channelled) |
| Thunder Dragon, Tempest | `StormOakPlacement.pickTarget` / bolt; `SkyCorsairUnit` flight rules; `UnitGrid.near` |
| Mirror Mage | `AwakenThicket` temporary tagged summon rules |
| Phoenix | new once-revive helper (`LivingDeathEvent`), shared later with the Gravebound Deathless Champion |
| Geomancer / Ley Master | `ShipwrightUnit` / `HarbourmasterUnit`, `T2Workers` |
| Capitol / labs | `WreckHarbour`, `Slipway`, `Drydock` (+ `TideT2Prod` re-check pattern) |
| Mana Wells | building + per-owner list (the Storm Oak "dormant without Tier 2" rule for the button) |
| Mana Cistern / Condenser / wind | storage vault, `EnergyConverter`, `WindGenerator` `"_storm"` variants |

---

## 6. Structures and palette (`tools/gen_structures.py` `"_storm"`)
Pad: polished andesite / smooth stone. Ring: stone bricks with red-terracotta posts. Roof/"jade": **waxed** oxidised
cut copper and its stairs/slabs (unwaxed copper weathers by random tick and would change the building mid-match),
green glazed terracotta accents. Gold: gold blocks for ridge ends, `chain` + `lantern` for hanging lamps, red
banners. Glass: red stained glass panes for paper screens. Wind-chime tower: red posts, gold-block bells, jade sails
(`WindmillRenderClientEvents` colour). No cherry leaves (they shed particles and petals, and decay if unattached).

---

## 7. Risks

- **Energy economy balance.** Spells paid from the same pool as construction are the point, but a Stormcaller who
  autocasts everything can stall itself into a loss. Mitigations: caster shots degrade (half rate) instead of
  stopping; Overcharge auto-off; Rocket Battery is energy-free; the bot keeps a reserve. Tune costs against the
  1500-energy start storage and +20 e/s base income - a Judgement (150) is 7.5 s of base income.
- **Stall interplay.** `drawSpell` adds to `energyDemand`, so heavy casting also throttles *metal-only* consumers'
  progress (stall is `min(metal, energy)` per player). Intended (BAR stalls everything), but watch playtests: if it
  feels unfair, spells could be excluded from demand and only Overcharge's explicit cap would stall construction.
- **Overcharge exploits.** A team could keep one player permanently overcharged on allies' overflow. Acceptable
  (it costs their overflow); the 0.5 build cap keeps that player from also expanding.
- **Converter threshold per faction** touches shared economy code; the converter GameTests must keep passing for
  the other five factions (default 0.5 unchanged).
- **Seal coverage.** Abilities fire from several paths (UnitActionItem, autocast, passive ticks, bot code calling
  `use` directly). Every path must ask `SealServerEvents.isSealed`; grep `\.use(level` and autocast sites before
  merging slice 2. Missing one is a silent bug, not a crash - add a GameTest per path that is easy to reach.
- **Radar** doesn't exist in RoN (fog of war has sight, not blips). A real radar needs a fog channel that sends
  coarse enemy positions inside a radius - new packet, rate-limited (1 Hz, dots only). Slice 3 ships the Seer's
  decloak + long sight; radar is a separate design decision for lovish.
- **Decoys (Mirror Mage)** are real entities: they cost ticks and pathing like units. Cap 3 per cast, 12 s, at most
  2 Mirror Mages' worth alive per player (6 decoys); never counted in score, population or win checks; discarded on
  reload. The bot must not target-prioritise them differently (it can't tell either - fair).
- **Phoenix rebirth** cancelling `LivingDeathEvent` must not break kill credit, wreck creation or the com-blast
  logic; the ember state must still count as an alive unit for selection and win checks.
- **AM bodies.** Shoebill picks up items (`ITargetsDroppedItems`), Sunbird applies slow falling, Elephant can be
  ridden/tamed, Spectre drifts - each must be stripped like the Stag Lancer/Gull Spotter. **Void Worm**: boss bar
  shown to every player in range and multipart children (`void_worm_part`) that RoN's selection, `UnitGrid`, fog
  and damage routing don't know about - do not use it without a spike.
- **Flyer-heavy T2 (Thunder Dragon, Phoenix) + T3 flyer**: anti-air across factions must be adequate; the
  Celestial Dragon's grounding is the explicit counter, and every faction already has at least one `WindAntiAir`
  candidate (Windcaller, Wisp Choir, Flak Swivel) - Gravebound/Horde need one tagged before slice 4 (Banshee scream,
  Spear Thrower).
- **8v8 performance.** No new per-tick loops: shots and casts pay inline, sustained spells pay at 1 Hz, Ley
  resonance rides the 1 s income recalc, frost sheets copy the tidepool caps and 10-tick pass, chain lightning uses
  `UnitGrid.near` with a scratch list, Tempest strikes every 10 ticks per storm. Particle beams for ley-links are
  sparse (every 40 ticks) and only for visible chunks.
- **Fall-through.** Grep `Factions\.(VILLAGERS|MONSTERS|PIGLINS|VERDANT_COURT|TIDEWROUGHT)` before merging slice 1;
  hard-coded lists (GameTests, `BotServerEvents` parse/message, `LobbySounds.stingerOf`) need Stormcallers added.
- **Death-FX bits.** `F_STORM = 6` leaves only 7 for the Artificers; the faction field is full after faction 7.
- **Saves.** Ley-links and Overcharge are not saved (documented); frost sheets use the scheduled-tick fail-safe;
  every building needs its `BuildingSaveData` case.

## 8. Size estimates
| Slice | Size | Scope |
|---|---|---|
| 0 announce | XS (done) | 5 files, ~170 lines incl. the GameTest |
| 1 MVP + Mana core | L | 6 units (incl. commander kit), 4 buildings + eco variants, `ManaServerEvents` + `drawSpell` + Overcharge + converter threshold, Ley Link network, Judgement + Blink, bot kit, ~8 GameTests; 2-3 sessions |
| 2 rest of T1 | M | 3 units, Seal effect + enforcement, frost sheet registry/block/pathing kind, Ward |
| 3 T2 | L-XL | lab, Ley Master + Mana Wells, 7 units (3 AM bodies, decoys, rebirth) |
| 4 T3 | M | Celestial Dragon + lab + Tempest + grounding |
