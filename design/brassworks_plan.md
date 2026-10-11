# Brassworks Artificers - implementation plan

Faction 7 of `claude/design-factions.md`: dwarven artificer guilds - brass, copper, rivets, steam vents, foundry halls
carved into stone; brass, soot black and furnace orange. BAR role: **Armada's tech and machines** - durable,
upgradeable war-machines and turrets, built and *crewed* by people. Loses to fast raids before the machines are online.
Signature: **Heat & Upgrades** - heavy fire builds heat that must vent; Tinkers field-upgrade a machine once.

This plan follows `design/stormcallers_plan.md` exactly (its section 1 is the touch-point checklist, its "as built"
sections the lessons), with the same path conventions: paths are relative to
`bar/src/main/java/com/solegendary/reignofnether/` unless they start with `bar/`, `tools/` or `design/`.

---

## 1. Touch points (what the Stormcallers taught, done the same way)

| Touch point | Brassworks |
|---|---|
| `faction/Factions` | `BRASSWORKS` registered **after** `STORMCALLERS` (ids never move); live from slice 1 (no `setPreview()`), `noCubeMap()`, no spawn wave. Calm theme: a foundry is warm, so vanilla `music.nether.basalt_deltas` via a sounds.json event reference (`SoundRegistrar.BRASSWORKS_CALM_THEME_SONG`). Worker icon `mobheads/tinker.png`; lobby icon vanilla `textures/block/cut_copper.png` (waxed blocks share the unwaxed texture). |
| `faction/FactionTraits` | `BRASSWORKS` entry: accent `0xFFE0A040` (brass), livery commander-only (leather cap/chest dyed brass `0xC98A2E`, accent soot `0x2A2622`), banner black wool / cut copper, scaffold = polished blackstone posts with waxed cut copper rails, `ScaffoldDecor.CHAINS`, `Ambience.FORGE_FIRE`, `fx(N_BRASS, F_BRASS)` (nanolathe brass `0xE0A040`, debris = brass gears, soot puffs, furnace-orange sparks - `F_BRASS = 7`, the last value of the 3-bit death-FX field), commander `(CommanderDGun.Kind.RAIL, CommanderAbility.Kind.OVERCLOCK)`, wreck = waxed exposed cut copper, extractor `METAL_EXTRACTOR_BRASS`. No formation/momentum/tidal/mana: Heat is per machine, not per player, so it needs no FactionTraits flag - `brass/Heat` reads the `Machine` interface. |
| `matchstart/SkirmishServerboundPacket.CODE_PATHS` | `7` = `"brassworks"` appended; `SkirmishSetupScreen.Settings` comment. `LobbySounds.stingerOf` -> `faction_brassworks` (`tools/gen_ui_sounds.py`, SoundRegistrar `UI_KEYS`). |
| Lobby / skirmish picker | nothing: `FactionPicker` lists the registry. Lang: `hud.faction.reignofnether.brassworks`, tagline `matchstart...faction_tagline.brassworks` = "Steam, brass and heat". |
| `bot/BotPlayer.kitFor` + `BotServerEvents.addBotImpl` parse (`"brassworks", "brass"`) | Slice 1: capitol Foundry Hall / `_brass` eco / Machine Shop / T1 mix; `vaultFor` -> `STORAGE_VAULT_BRASS`; `queueAntiAir` -> Flak Cart; `t2Lab` etc. null until slice 3 (the Kit already tolerates null T2/T3). `brassHabits`: Overclock when 3+ machines stand within 12 of 2+ foes, Deploy Turret when the Guildmaster is hit with 2+ foes within 10, Tinkers plate the nearest unplated Iron Warden at home. |
| Structures in `assets/` **and** `data/` from `tools/gen_structures.py` | `"_brass"` palette (section 6) + bespoke Foundry Hall, Machine Shop and the Steam Boiler (a `WindGenerator("_brass")` with **no rotor hub**, so `WindmillRenderClientEvents` draws no sails: a boiler with a smokestack). Pockets audited with `tools/audit_structure_pockets.py`. |
| `BuildingSaveData` case per building | Foundry Hall, Machine Shop (the shared eco classes already map by structure name). |
| Unit base classes | vanilla bodies with the Unit boilerplate copied, never another faction's class (section 4). |
| Death-FX bits | `F_BRASS = 7`: the faction field is now full (any eighth faction widens it to 4 bits: `BarFx.FACTION_BITS`). `N_BRASS = 7`. |
| Hud | `UnitInfoCard` roles, `PortraitRendererModifiers` (the Strider and IronGolem rules already match by vanilla class; the Gyro Scout's parrot too), strategic glyphs (capitol = STAR, Machine Shop = FACTORY by its productions: nothing to add until the T2 lab). |
| Tools | `tools/gen_brass_skins.py` (deterministic: seeded `Skin`s), `tools/gen_hud_icons.py` (Foundry Hall, Machine Shop, `_brass` eco), `tools/unit_stats.py` roster rows, `tools/balance_audit.py` within bounds. |
| GameTests | appended at the END of `gametest/SkirmishGameTests` (section 7); the live-faction tests of the Court, Tidewrought and Stormcallers count "Random" over seven factions now; the skirmish-code round trip pins 7. |

---

## 2. Bodies (vanilla + Alex's Mobs 1.22.9 checked as the Stormcallers did)

What reads as clockwork from the RTS camera is **colour and silhouette**: brass + soot + a furnace-orange glow on any
body makes a machine; the crews stay dwarves (illager bodies with beards, goggles and leather aprons). AM has nothing
that reads as a machine (its insects - cockroach, tarantula hawk - read as bugs, its crow as a bird), so the machines
are vanilla bodies in brass skins and AM is not touched in slice 1 (the T2 Gyrocopter Gunship may use AM's
`EntityCrow` outline later; the T2 Clockwork Spider the vanilla spider).

| Unit | Body | Extend | Why |
|---|---|---|---|
| **Tinker** (constructor) / **Guildmaster** (the first Tinker, tagged by `CommanderServerEvents.makeCommander`) | illager (VillagerUnitModel), dwarf: beard, goggles on the hat box, leather apron, brass bracers | thin copy of `storm/GeomancerUnit` (WorkerUnit plumbing) | the only safe worker base |
| **Gyro Scout** | vanilla Parrot in a brass-and-copper skin (`GyroScoutRenderer extends ParrotRenderer`: our texture, no variant) | copy of `verdant/OwlWatcherUnit` (proven flight: `FlyingUnitMoveControl`, `FlyingPathNavigation`, bee `travel`) | a small hovering body with beating wings = a rotor drone |
| **Steam Runner** | vanilla Strider at 0.8 (boiler body on stilt legs, the cold "shiver" reads as a rattling engine), `StriderRenderer` subclass with our texture | the Earthshaker boilerplate on `Strider` + `piglins/StriderUnit`'s water/suffocation fixes | tall spindly machine; fast |
| **Rifle Squad** | illager, crossbow held as the musket | `storm/WindMonkUnit`'s hit-scan frame without mana or AA multipliers | muskets are hit-scan: `BarFx.K_ARROW` shot + impact, a smoke puff at the muzzle |
| **Iron Warden** | vanilla Iron Golem at **0.8** in brass with soot joints and a furnace-orange furnace door on the chest | copy of `storm/EarthshakerUnit` (extends `IronGolem`, never `IronGolemUnit`) | the crewed steam golem |
| **Flak Cart** | illager crew with a brass flak pack | `WindMonkUnit` frame, `WindAntiAir`, Wisp Choir multipliers, flyer-first (`MiscUtil`) | `WindAntiAir`-class AA, no mana |
| **Mortar Crawler** | illager crew hauling a mortar carriage | copy of `storm/RocketBatteryUnit` (one `BombardShell` per shot, dead zone, Attack Ground) | artillery |
| **Shield Projector** | illager with a brass projector pack, empty hands | `storm/MirrorMageUnit` non-attacker frame | support |
| **Sapper** | illager, soot apron | `villagers/VindicatorUnit` (the safe melee base, as Leafblade / Blade Dancer) | weak melee + Plant Charge |
| **Deployed Turret** (the Guildmaster's) | `RifleSquadUnit` subclass: speed 0, 20 s lifetime, population 0, no wreck | a real entity so every targeting/damage rule applies | the simplest "auto-turret" that fights back |

---

## 3. The Heat mechanic (`brass/Heat`, `brass/Machine`)

Per-machine, server-side, synced for the look:
- `Machine` (interface): `heat()` / `setHeat` (synced INT 0-100), `ventTicks()` (synced INT), `heatPerShot()`,
  `upgrade()` (synced BYTE: 0 none, 1 plating, 2 barrel), `overclockTicks()`.
- `Heat.onShot(m)`: every attack adds `heatPerShot` (x `OVERCLOCK_HEAT` 1.5 while overclocked); at **100** the machine
  **vents** for `VENT_TICKS` = 120 (6 s): `Heat.canFire(m)` is false (ranged: `performUnitRangedAttack` dry-fires;
  melee: `doHurtTarget` returns false), it takes **+25 %** damage (`BrassUnitEvents` LivingHurtEvent, one instanceof),
  and steam (`CAMPFIRE_COSY_SMOKE` + `CLOUD`) hisses off it every 10 ticks (one `sendParticles` per 10 ticks per
  venting machine). When the vent ends heat is 0. Otherwise heat cools `COOL_PER_SECOND` = 4 a second (`Heat.tick`,
  one subtraction every 5 ticks).
- `Heat.cooldownScale(m)`: 1 / 1.4 while overclocked (`OVERCLOCK_RATE`), applied in `getAttackCooldownMultiplier`.
- **Overclock** (`CommanderAbility.Kind.OVERCLOCK`, instant, 45 s): own machines within 12 get `OVERCLOCK_TICKS` = 160
  of +40 % fire rate and x1.5 heat per shot; when it runs out the machine takes `OVERCLOCK_AFTER_HEAT` = 30 heat (the
  "then heat damage" of the design: usually the vent). Never a Deployed Turret (it is already hot).
- Heat per shot: Iron Warden 9 (eleven swings, ~27 s of melee), Steam Runner 10, Flak Cart 6 (~24 s of fire),
  Mortar Crawler 25 (four shots), Deployed Turret 15 (dies venting). The Gyro Scout and the Shield Projector are
  machines (upgradeable) that never shoot: heat per shot 0.
- Saved: `bt_heat`, `bt_vent`, `bt_upgrade` in `addUnitSaveData` of each machine.
- Visible: heat >= 70 leaks a smoke mote every 20 ticks; the unit card shows "Heat n %" (tooltip line) - nothing per
  frame on the client.

## 3.1 Field Upgrade (`ability/abilities/FieldUpgrade`)
Two buttons on the Tinker (`hotkey8` Brass Plating, `hotkey9` Long Barrel; UnitAction `FIELD_UPGRADE_PLATING` /
`FIELD_UPGRADE_BARREL`): target an own machine within 6 blocks that has **no upgrade yet**; 30 metal paid from the
pool (refused with the HUD warning when short; nothing paid when the target is not a bare machine). Plating: a
permanent `ARMOR +6` modifier (`UPGRADE_ARMOUR_UUID`). Barrel: `ATTACK_RANGE +3` (ranged machines only - a Warden or
Runner is refused the barrel, a Gyro Scout and a Shield Projector too). The synced `upgrade` byte drives
`unit/modelling/renderers/BrassUpgradeLayer` (a `RenderLayer` added to every machine renderer): plating draws a waxed
cut copper block on the torso, the barrel a copper bar along the facing. Reloads re-apply the modifier from the saved
byte. One per machine, ever (the design's "once").

## 3.2 Deploy Turret (`ability/abilities/DeployTurret`, UnitAction `DEPLOY_TURRET`)
The Guildmaster's third button (the Ward/Fog Bank precedent in `CommanderServerEvents.ensureAbility`): aimed within 8,
40 metal, 30 s cooldown; spawns a `DeployedTurretUnit` (a rifle on a tripod: the Rifle Squad frame, speed 0, 40 HP,
heat per shot 15, `bt_turret` tag, cost `DEPLOYED_TURRET` = 0/0/0 pop 0 - whitelisted in `balance_audit.py`
`FREE_OK`) that lives `TURRET_TICKS` = 400 and then falls apart (steam puff, no wreck: `WreckServerEvents` skips
`MIN_COST`). Discarded on reload (`bt_turret` entities never load from disk: `BrassUnitEvents.onJoin`).

## 3.3 Rail Lance (`CommanderDGun.Kind.RAIL`)
The PLAIN line with its own look and damage path: every enemy on the 14-block line is hurt through
`UnitDamage.hurtExplicit(le, mobProjectile(self, self), DAMAGE)` (commanders `COMMANDER_DAMAGE`), so the hit is a
*projectile* (Jade Mirror, Bastion Aegis and the Projector's shield answer it) at the explicit D-gun amount rather than
the commander's melee damage; `ELECTRIC_SPARK` + `END_ROD` rail trail, an anvil ring.

## 3.4 Projector shield (`brass/ShieldServerEvents`, `ability/abilities/ProjectShield`)
`BastionAegis.Dome`'s rule at r3: the Shield Projector targets a friendly unit within 10 (UnitAction `PROJECT_SHIELD`,
20 s cooldown): for 8 s a bubble of `CAPACITY` = 60 projectile damage follows that unit; friends within 3 of it are
covered too (LOW-priority LivingHurtEvent, after the Aegis so a dome soaks first). One record per cast, ~20 live at
most; a ring of `END_ROD` every 4 ticks.

## 3.5 Sapper charge (`ability/abilities/PlantCharge`, `brass/ChargeServerEvents`)
Aimed at an enemy building: the server refuses a target beyond `REACH` = 3 (the player or the bot walks the Sapper
up first): plants a charge (a TNT block display + fuse particles) and
after `FUSE_TICKS` = 80 calls `placement.destroyRandomBlocks(CHARGE_DAMAGE = 120, sapper)`; 15 s cooldown. Nothing
else is damaged (no explosion entity: no terrain, no friends).

---

## 4. Slices

### Slice 1 - playable T1 (this branch, `brassworks-1`)
Everything in sections 1-3: faction, lobby, bot, Guildmaster (Overclock, Deploy Turret, Rail Lance), Tinker (Field
Upgrade), Foundry Hall (capitol, `TownCentre` pattern: Tinker + Gyro Scout, Tier 2/3 research in the usual slots),
Steam Boiler / extractor / T2 extractor / Smelter (`EnergyConverter("_brass")`) / **Strongbox**
(`StorageVault("_brass")`, the standard split), Machine Shop (T1 lab: all 8 T1 units), skins, portraits, HUD icons,
lang, GameTests, duel rows, soak seat. Costs (balance audit, no new fails): Tinker 50/70 (= every worker), Gyro Scout
30/40 (= Owl Watcher), Steam Runner 95/100, Rifle Squad 85/80, Iron Warden 150/80, Flak Cart 90/150 (= Wind Monk),
Mortar Crawler 110/80 (= Rocket Battery), Shield Projector 60/60, Sapper 70/40, Foundry Hall / Machine Shop at the
other capitols'/labs' prices.

### Slice 2 - T2 (L)
Machine Shop -> **Assembly Works** (T2 lab, `abilitySlot9`, needs Tier 2; `BrassT2Prod` re-check). Engineer-Captain
(T2 constructor, `T2Workers.isT2Worker`, repairs machines x2: `BuildRepairGoal` rate multiplier for `Machine`
targets), Steam Juggernaut (vanilla Ravager at 0.8 in brass: boiler plating = 40 % ranged resist, heat 15/charge),
Tesla Walker (illager crew + the Thunder Dragon's chain-lightning code, heat 20), Gyrocopter Gunship (AM `EntityCrow`
outline or the parrot at 1.6x: strafing runs = the Phoenix's dive), Rail Battery (static: a `Building` on the
`StormOakPlacement` bolt pattern with a 30-block rail shot every 5 s), Drill Burrower (the Blink teleport with a
2 s dig telegraph, lands behind a target within 16), Repair Crawler (Bloom Priestess' heal frame restricted to
machines), Clockwork Spider (vanilla spider, `isClimbing` kept, a turret volley - the Mortar Crawler's frame).

### Slice 3 - T2 defence + Heat polish (M)
Rail Battery as the faction's T2 defence (`abilitySlot5`), Engineer-Captain's double repair, Heat on the HUD energy
bar area (a small gauge for the selection), the bot's T2 habits.

### Slice 4 - T3 (M)
**The Iron Leviathan Engine**: a walking foundry (vanilla Ravager at 2.2x with a foundry howdah layer, or AM's
elephant outline like the Lantern Fortress), heavy cannon (one `BombardShell` of 40 splash every 4 s), spawns 2
clockwork drones (Gyro Scouts with a 3-dmg hit-scan) every 30 s capped at 6 alive; **overheats**: heat per cannon
shot 20 - vents 6 s at 100 like every machine, so sustained fire silences it (the design's weakness); slow (0.17),
Sappers and the Drill Burrower do x2 to it (`BrassUnitEvents`). T3 lab **Great Forge** (Tier 3 + Engineer-Captain
builder, `T2Workers.isT3Lab`). Priced like the other experimentals (1840m, 20 pop).

---

## 5. Which existing classes each piece builds on

| Piece | Builds on |
|---|---|
| Rail Lance | `CommanderDGun` (new `Kind.RAIL`, the PLAIN line's `hits`, `UnitDamage.hurtExplicit`) |
| Overclock | `CommanderAbility` (new `Kind.OVERCLOCK`, the instant area-buff path) + `Machine.setOverclockTicks` |
| Deploy Turret | `Ward` (third commander button via `ensureAbility`) + `DeployedTurretUnit` (Rifle Squad subclass) |
| Field Upgrade | `LeyLink`'s aimed building/unit click dispatch, attribute modifiers like `CommanderServerEvents.makeCommander` |
| Heat | new `brass/Heat` + `Machine`; damage hook like `StormUnitEvents` |
| Rifle Squad / Flak Cart | `storm/WindMonkUnit` (hit-scan frame, Wisp Choir multipliers for the cart) |
| Mortar Crawler | `storm/RocketBatteryUnit` + `BombardShell` |
| Shield Projector | `BastionAegis.Dome` + `storm/MirrorMageUnit` body |
| Sapper | `villagers/VindicatorUnit` + `BuildingPlacement.destroyRandomBlocks(amount, attacker)` |
| Iron Warden / Steam Runner | `storm/EarthshakerUnit` boilerplate on `IronGolem` / `Strider` |
| Gyro Scout | `verdant/OwlWatcherUnit` |
| Tinker | `storm/GeomancerUnit` |
| Capitol / lab / eco | `storm/JadePagoda`, `storm/LotusAcademy`, the shared `_variant` eco classes |

---

## 6. Structures and palette (`tools/gen_structures.py` `"_brass"`)
Pad: polished blackstone / smooth stone. Ring: deepslate bricks. Brass: **waxed** cut copper and its stairs/slabs
(unwaxed copper weathers mid-match); T1 extractor "metal" = waxed **exposed** cut copper (never `copper_block` /
`cut_copper` / `waxed_copper_block`: `MetalExtractor.getUpgradeLevel` reads those as the tier-2 marker; the T2 carries
a `waxed_copper_block`). Soot: blackstone / deepslate tiles / coal blocks. Furnace orange: magma blocks (inert: they
only hurt entities standing on them, so never where units walk), shroomlight, copper lanterns are not in 1.20.1 -
plain `lantern` + `soul_lantern` on brass posts, `redstone_lamp` (unlit: a brass gauge). Chains for pipework, iron
bars as grates, `lightning_rod`s as pipes, cauldrons as crucibles, anvils and smithing tables on the floor. No
unwaxed copper, no fire blocks (they spread), no campfire on hay.

Foundry Hall (capitol, 11x12x11): a deepslate-brick hall with a brass dome of waxed cut-copper stairs, a smokestack
of blackstone at the back corner with a chain "smoke" column, a furnace mouth (magma under iron bars) at the front,
anvils and a smithing table inside, lanterns on brass posts. Machine Shop (9x6x9): an open-sided workshop under a
brass roof: deepslate posts, a long work bench of smithing tables and anvils, a crucible (an empty cauldron on a magma block), chain hoists. Steam Boiler (3x8x3): a riveted brass boiler (waxed cut copper)
on a blackstone firebox with a magma glow behind iron bars and a chimney - no rotor hub. Smelter (3x5x3): a
blast-furnace crucible under a brass hood.

---

## 7. GameTests (slice 1, all at the END of `SkirmishGameTests`, unique owner names, no global counts)
- `brassworks_are_live_and_the_six_other_factions_are_intact` (the storm test's shape; "Random" over seven).
- `brassworks_t1_items_produce_and_structures_have_no_pockets`: every Machine Shop / Foundry Hall item produces a
  unit of the right type; every Brassworks structure is in both trees; `audit_structure_pockets` rule in-test (every
  air cell inside the footprint reachable from outside) for the Foundry Hall and the Machine Shop.
- `machine_heat_vents_at_100_and_recovers`: a Flak Cart pushed to 100 vents (no damage dealt, +25 % taken), then
  recovers to 0 and fires again.
- `field_upgrade_applies_once_per_machine`: plating on a Warden adds the armour once; a second upgrade is refused.
- `sapper_charge_damages_a_building`: a charge on an enemy Machine Shop takes ~120 health after its fuse.
- `deploy_turret_fights_then_expires`: the turret stands, shoots a foe, is gone after 20 s with no wreck.
- the duel matrix's T1 row gains the Iron Warden, the Rifle Squad and the Steam Runner; the soak gains a sixth
  (Brassworks) base with the same start checks as the others.

---

## 8. Risks
- **Heat is a nerf without a gauge.** Players must see it: the tooltip "Heat" line and the steam are the slice-1
  minimum; a HUD gauge waits for slice 3. The bot's Overclock timing matters most for the bot's own vents.
- **Melee heat.** A Warden that vents mid-brawl at +25 % damage taken is the design's cost; if the duel matrix shows
  the Warden losing every T1 line duel, raise its heat per swing budget before touching HP.
- **Parrot scout.** The parrot model is small; `GyroScoutRenderer` scales it 1.4x so it can be clicked.
- **Render layer on vanilla renderers.** `BrassUpgradeLayer` draws in model space (y down, origin 1.5 above the
  feet): the plating block sits where the torso is for every body at hand; if a body looks wrong, the per-renderer
  offset is one constant.
- **Death-FX field full.** `F_BRASS = 7` uses the last value; an eighth faction must widen `FACTION_BITS`.
- **Strider body.** `isSensitiveToWater` false, the suffocation speed modifier removed (the piglins' StriderUnit
  fixes), never a lava path (`PathType` handled by vanilla's `GroundPathNavigation` instead of the Strider's lava
  navigation).
