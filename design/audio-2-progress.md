# audio-2 progress (lovish save points)

Branch `audio-2`, from origin/main 0882349. Lane: sounds/, barfx/ hooks, tools/gen_sfx.py, sounds.json (sfx entries),
sfx oggs, Sound settings additions. Design: design/audio.md section 5 (audio pass 2).

## Done
- tools/gen_sfx.py: 66 new original sounds (95 files): explosions small/medium/large/huge (commander blast), T3 death
  boom, collapse layers (crack, rubble), a game-ender strike layer per faction, unit replies (select / move / attack
  per faction, 2 variations), unit-ready pings (normal + T3 per faction), 10 ambient beds (meadow, forest, snow,
  desert, swamp, sea, nether, soulfire, end, chorus) and 7 faction base hums. Loops are seamless (loopify), 22.05 kHz
  q1; sfx folder 1.25 MB -> 2.71 MB (+1.46 MB; budget check lowered to 3.3 MB). Byte-identical re-runs checked.

## Next
- Sfx.java entries + categories (Replies, Ambience), SfxClient (explosions, collapse layers, T3 boom, replies API),
  UnitReplies (select/order/ready, owner only), AmbienceClient (beds, hums), config + Sound page, hooks, vanilla
  explosion replacement, AudioTwoGameTests, design/audio.md.

## Known failures / unverified
- Nothing pushed through CI yet.

## Proposals (outside this lane)
- (filled in as found)
