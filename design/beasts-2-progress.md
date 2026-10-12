# beasts-2 - progress

Spec: design/big_units.md (round 2 of big-units; sections 1.5-1.7 and 2.5-2.9 "as built"). Base: origin/main 102ba10.

## Done
- Framework hooks in `building/gameender/GameEnderPlacement`: payForStage, chargeRate, onStageComplete, aftermath +
  tickAftermath, enemyBuildingsNear / enemyUnitsNear, payUpfront. Sun Spire / Necropolis unchanged in behaviour.
- Game enders (each with its Placement, structure via tools/gen_structures.py in assets/ and data/, cost, lang,
  faction registration, T2-builder gate through GameEnder): Doom Forge (Horde; meteor + burning crater; Stoke: 350
  metal per stage), World Seed (Verdant; Heartroot + grove; kill the Heartroot), Maelstrom Engine (Tidewrought;
  whirlpool; flood ring of 3 tidepools paces the charge - TidepoolServerEvents.raiseForBuilding), Heaven's Bell
  (Stormcallers; 15 s storm, units first; 400 energy per stage), Earthcracker (Brassworks; fault line along the
  bearing; vents 12 s after each stage, raids double).
- T2 beasts: Shellback Siege-Turtle (vanilla Turtle x2.4, Moor ability: fort/pool/deck battery), Jade Qilin (vanilla
  Horse x1.5: walking Mana Well via BigUnitServerEvents.inQilinWell <- ManaServerEvents.insideWell, Mana Spring),
  Steam Hauler (vanilla Sniffer x1.25, a Machine: Field Foundry area repair + Drop Turret, Heat). Renderers with block
  layers, prods, lab slots (Drydock 5, Observatory 9, Assembly Works 8), bot kits, placeholder portraits (tinted).
- bot/BotGameEnders: every faction's game ender; turtle Moor and hauler Drop Turret in fights.
- tools/unit_stats.py beast rows; DuelGameTests beast rows; BrassworksGameTests Assembly Works count 7 -> 8.
- GameTests (gametest/BigUnitGameTests.java, appended): registration/pricing/bots, turtle, qilin, hauler, and one per
  new game ender (its faction rule + Ward Spire intercept + the strike landing).

## Next
- Nothing required. Playtest: the render layers, the turtle's anchor feel, game-ender pacing with real economies.

## Known failures / notes
- CI green on 9de4d76 (all BigUnitGameTests incl. the eight new ones). Earlier runs failed on my own test bugs
  (hit-cooldowns, cactus damage cancelled for units, a heightmap that found the arena ceiling) - fixed.
- Touched other agents' tests where this branch changed what they count or where they ran: BrassworksGameTests
  (Assembly Works trains 8 units now), HeroGameTests.each_faction_hero_trains_at_its_lab (labs were spaced 40 blocks
  apart up to 280 blocks east - unloaded chunks once the test layout shifted; now all on the test's own arena),
  DuelGameTests (beast rows).
- Render layers (fort, jade scale, boiler) were placed from vanilla model geometry, not seen in game.
- Round-1 Necropolis spawns its risen by heightmap too (roofs / arena ceilings); left as is (not this branch's scope).
