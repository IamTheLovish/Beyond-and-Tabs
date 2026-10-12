# audio-pass progress (lovish save points)

Branch `audio-pass`, based on origin/main 102ba10. Scope: sounds, sounds.json, sound-trigger hooks, audio config, tools/.
Design + audit: design/audio.md.

## Done
- tools/gen_sfx.py: 70 original sounds (102 oggs with variations, ~1.3 MB), deterministic (seeded, bitexact vorbis);
  refreshes the sfx.* entries of sounds.json and the subtitles in en_us.json as a text edit (no reformat churn).
- tools/voice_lines.txt: 5 new announcer lines; oggs taken from the voice-oggs branch (only the 5 new files).
- sounds/Sfx.java (keys, category, volume, gap; server play/playGlobal via a BarFx SFX event + per-cell gap),
  sounds/SfxClient.java (BarFx events -> weapon/hit/death/collapse/nano/reclaim sounds; category volumes; distance
  falloff from the screen-centre ear with a panning stand-in point; per-sound gap, per-category tick budget, crowd
  volume scaling).
- BarFx: SFX event type, SHOT weapon flag (W_GUN), COLLAPSE faction in flags bits 1-3.
- Hooks: building built (client) / destroyed, wall gate, traps, wreck reclaim, possession enter/exit, hero boon (+voice),
  Gorehorn charge/breach, Ossuary devour, Mossback embark, Oriflamme trumpet, game ender start/stage/aim/strike/intercept
  (+voice), start-position phase (MapClient, +voice), match-start faction stinger, Brassworks hit-scan guns.
- Config: sfx_units/buildings/abilities/ui volumes + music_stingers; Sound... page in Mod Settings; LobbySounds scaled by UI.
- gametest/AudioGameTests.java: registered events <-> sounds.json <-> shipped files; Sfx server gap; SFX codec.

## Next / known
- CI result for the latest push (see final report).
- Not verified by ear in game (no client here): levels/mix balance may want a tune pass in a playtest.
