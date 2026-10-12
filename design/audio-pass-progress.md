# audio-pass progress (lovish save points)

Branch `audio-pass`, based on origin/main 102ba10. Scope: sounds, sounds.json, sound-trigger hooks, audio config, tools/.

## Plan
1. design/audio.md: audit + per-faction sound-design language.
2. tools/gen_sfx.py: deterministic numpy/scipy synthesis -> sounds/sfx/*.ogg (vorbis via ffmpeg).
3. sounds/Sfx (keys, category, variations), SfxClient (category volume, distance attenuation, rate limits);
   BarFx events drive weapon/death/collapse/nano sounds client-side (no new packets); discrete server events
   ride the BarFx queue as an SFX event (batched, range-filtered) or go global for match moments.
4. Client config: sfx_units / sfx_buildings / sfx_abilities / sfx_ui volumes (+ announcer = voice_volume), Sound... page.
5. New announcer lines in tools/voice_lines.txt (voice.yml -> voice-oggs branch -> copied in).
6. GameTest: every registered sound event is in sounds.json and every file it names ships.

## Done
- voice_lines.txt: 5 new lines (game ender started/aimed/intercepted, hero boon, start positions).

## Next
- everything above from step 1.
