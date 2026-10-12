# big-units - progress

Spec: design/big_units.md. Base: origin/main 30a1b6f.

## Done
- design/big_units.md: 7 T2 beasts + 7 game enders designed, counters, balance lens.
- Game-ender framework `building/gameender/`: GameEnder (building base: T3 lab + Tier 3 + T2 builder gate),
  GameEnderPlacement (announce on first tick + enemy alert pings, 3 charge stages, raid knock-back per 10% HP, auto
  target nearest enemy capitol, 5 s telegraph, Ward Spire interception, recharge), WardSpire/WardSpirePlacement (every
  faction, one interception per 90 s, range 40), GameEnderServerEvents (risen expiry).
- Sun Spire (Sunforged) and Necropolis (Gravebound) + structures (sun_spire / necropolis / ward_spire .nbt via
  tools/gen_structures.py), costs, lang, faction registration, T2Workers.needsT2Builder.
- T2 beasts: Oriflamme Elephant (AM elephant, aura - BigUnitServerEvents), Gorehorn Rhino (AM rhino, Breach Charge
  ability), Ossuary Behemoth (zoglin subclass, eats wrecks for stacks), Mossback Bison (AM bison, Embark/Eject carrier,
  riders half damage + heal). Renderers with block-piece layers (BlockPieces). Prods, entities, attributes, renderers,
  lab slots, faction rosters, lang, placeholder portraits (copies of existing mobheads).
- bot/BotGameEnders (wards vs enemy game enders, hard bots build their own when floating, beast abilities in fights),
  hooked into BotPlayer; beasts in the bot T2 kits.
- tools/unit_stats.py roster rows (role 'beast', exempt from medians); duel rows "Beast" (each vs equal-metal T2 mix).
- GameTests in gametest/BigUnitGameTests.java (game ender stages/announce/raids, Sun Spire + ward interception,
  Necropolis raise, each beast's signature, bot rules, lab/pricing).

## Next
- Watch CI; fix any GameTest failures.
- Design-only: Doom Forge, World Seed, Maelstrom Engine, Heaven's Bell, Earthcracker; Shellback Turtle, Jade Qilin,
  Steam Hauler.
- Real skins/portraits for the beasts (unit-skins pass); layer positions were placed from AM model geometry, not seen
  in game.

## Known failures
- none known yet (first full CI with the beasts pending)
