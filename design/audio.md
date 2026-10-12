# Audio: audit and sound-design language

Audio was the weakest sense: units and buildings mostly fell back on vanilla mob/block sounds, the BAR-style
battle effects (BarFx) were silent, and the factions only sounded different in their calm themes and lobby stingers.
This pass (branch `audio-pass`) adds 70 original sound effects (102 files with variations, about 1.3 MB) and wires
them in with per-category volumes, distance falloff and crowd rate limits.

**All audio is original.** Every effect is synthesised by `tools/gen_sfx.py` from seeded noise, filters, modal
partials (bells, wood, metal, glass), FM and band-limited saws; no game's audio (TA, BAR, Subnautica or any other) is
sampled, traced or used as a reference recording. Voice lines are Kokoro-82M (Apache-2.0) via `voice.yml`.

## 1. Audit: what had no distinct sound (before) and what plays now

| Event | Before | Now (Sfx key) | Trigger |
|---|---|---|---|
| Bow / crossbow shot | vanilla mob shoot (some units: none) | `shot_bow` x3 | BarFx SHOT K_ARROW (client) |
| Musket / rifle / flak (Brassworks hit-scan) | `GENERIC_EXPLODE` pitched up | `shot_gun` x3 | SHOT with `BarFx.W_GUN` flag |
| Fire bolt / fireball | vanilla blaze/ghast | `shot_fire` x3 | SHOT K_FIREBALL |
| Cannon / mortar / big fireball / TNT lob | none | `shot_heavy` x2 | SHOT K_BIG_FIREBALL, K_TNT |
| Spell bolt | none | `shot_magic` x3 | SHOT K_MAGIC |
| Rocket | firework launch | `shot_rocket` x2 | SHOT K_ROCKET |
| Thrown / potion | none | `shot_thrown` x2 | SHOT K_THROWN, K_POTION |
| Melee hit | victim's hurt grunt only | `hit_melee` x3 / `hit_heavy` x3 | IMPACT K_MELEE / K_HEAVY_MELEE |
| Hit on a building | none | `hit_building` x2 | IMPACT at AT_BUILDING |
| Unit death per faction | vanilla mob death | `death_<faction>` x2, `death_large` x2 (T3) | BarFx DEATH faction + tier |
| Building complete per faction | none (alert chord for own only) | `built_<faction>` | BuildingPlacement.onBuilt (client) |
| Building destroyed per faction | block-break spam | `destroyed_<faction>` | BarFx COLLAPSE, faction in flags bits 1-3 |
| Construction loop | none | `nano_<faction>` x2 granules | BarFx NANO (builder beam life <= 12) |
| Wreck reclaim | anvil land on the last step | `reclaim_tick` x2 while reclaiming, `reclaim_done` | NANO life 22; WreckServerEvents.remove |
| Wall gate | vanilla fence gate | `gate_open` / `gate_close` | WallGate.setOpen |
| Trap trigger | trident / chain / explode | `trap_spikes` / `trap_snare` / `trap_mine` | Trap.spring |
| Possession enter / exit | none | `possess_enter` / `possess_exit` | PossessionServer.claim / release |
| Hero boon | level-up ding only | `hero_boon` + announcer "Your hero has received a boon." | HeroProgression.onBoon; FactionHero client sync |
| Beast signatures | ravager roar, door break, skeleton step, azalea | `beast_charge`, `beast_breach`, `beast_devour`, `beast_embark`, `beast_trumpet` | Gorehorn Breach Charge, its building hit, Ossuary devour, Mossback embark, Oriflamme in combat (every 20 s) |
| Game ender started | wither spawn (local) | `gameender_start` global + announcer line | GameEnderPlacement.announceStart |
| Game ender stage | beacon select | `gameender_stage`, pitch rises per stage | tickGameEnder stage++ |
| Game ender aims | chat only | `gameender_aim` global + announcer line | aimAt |
| Strike lands / intercepted | strike's own effects | `gameender_strike` at target / `gameender_intercept` global + announcer | resolveStrike |
| Start-position phase | chat ping | `startpos_begin` + announcer, `startpos_place`, `startpos_tick` (last 5 s), `startpos_end` | MapClient.onState |
| Match start | calm theme only | `stinger_<faction>` (Music slider, toggle) | PlayerClientEvents.addRTSPlayer |

Still open (not in this pass): per-unit acknowledgement voices; explosions stay vanilla (they already scale with
radius); factory "unit ready" pings; ambient biome/base loops beyond the existing building ambience particles;
victory/defeat keep the existing `victory.ogg` / `defeat.ogg`.

## 2. Sound-design language per faction

Each faction has an instrument family and a scale; its build-complete, destroyed, death, construction granule and
match stinger all reuse it, so a faction sounds like itself everywhere.

| Faction | Palette | Scale / gesture | Construction granule |
|---|---|---|---|
| Sunforged | bells and brass: bright bell partials, saw "trumpets" with opening filters | major fanfare (1-3-5-8) | small anvil tap + glass shimmer |
| Gravebound | bone and whispers: low bell toll, breathy formant noise, bone-click rattles | minor third, slow | bone scrape + whisper |
| Horde | drums and iron: pitched membranes, anvil strikes, low war-horn on a fifth | rhythmic figure, root-fifth | hammer on iron + drum tick |
| Verdant | wood, wind and birds: marimba-like wooden bars, band-passed wind, FM chirps | pentatonic run up | growth creak + leaf rustle |
| Tidewrought | waves and creaks: brown-noise surf swells, ship's bell, stick-slip hull creak | dorian low horn | rope creak + bubbles |
| Stormcallers | chimes and thunder: free-bar glass chimes, crackle, rolling thunder | lydian arpeggio (raised fourth) | electric hum + crackle |
| Brassworks | steam and gears: two-tone steam whistle, ratchet clicks, clanks, hiss | mechanical rhythm | ratchet + steam puff |

Weapons are by class, not faction (a bow is a bow), so a mixed fight still reads at a glance; deaths, buildings and
stingers carry the faction.

## 3. Playback rules (sounds/SfxClient)

* **Categories and volumes:** Units, Buildings, Abilities, Announcer (shares the announcer voice slider), UI (also
  scales the lobby sounds). Mod Settings > Sound... has a slider for each plus the match-fanfare toggle. Vanilla
  sliders still apply (units NEUTRAL, buildings BLOCKS, abilities PLAYERS, stingers MUSIC, the rest MASTER).
* **Distance:** full volume within 10 blocks of the "ear" (middle of the screen in the RTS view, else the camera),
  linear to silence at the category's reach (36-72 blocks, plus half the RTS zoom). The sound is placed a few blocks
  from the camera in its true direction so Minecraft's 16-block attenuation doesn't cut it but panning still works.
  Fog of war hides sounds from hidden places, like the effects.
* **Nothing gets worse with army size:** (1) every sound has a minimum gap between plays; (2) each category may start
  only a few sounds per tick (Units 3); (3) a smoothed "asked per tick" rate per category lowers each sound's volume
  by 1/sqrt of the excess, and a busy category skips far events first. A 200-unit fight plays about as loud as a
  20-unit one, just denser.
* **Network:** battle sounds cost no packets (they ride the BarFx events the client already gets). Discrete server
  sounds are one more BarFx event in the per-tick batch, with a per-(sound, 8-block cell) gap of 4 ticks on the
  server; global moments are one tiny packet to everyone.

## 4. Adding a sound

1. Add a generator and an `add(...)` line in `tools/gen_sfx.py` (category, variations).
2. Add the enum entry in `sounds/Sfx.java` (same key, category, base volume, min gap).
3. Run `python3 tools/gen_sfx.py` (writes the oggs, refreshes the `sfx.*` entries of sounds.json and the subtitles).
4. Call `Sfx.X.play(level, pos)` on the server, or route a client event through `SfxClient`.
   The GameTest `sounds_json_covers_every_registered_event` fails if a key has no entry or file.

New announcer lines: append to `tools/voice_lines.txt`, push (voice.yml commits the oggs to `voice-oggs`), copy the new
files across, add the key to `SoundRegistrar.ANNOUNCER_KEYS`, a sounds.json `announcer.<key>` entry and an
`Alert` in NotificationClientEvents.
