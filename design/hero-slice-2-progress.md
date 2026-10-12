# hero-slice-2 - progress

Branch `hero-slice-2` off origin/main 30a1b6f. Design: design/hero_mode.md sections 4, 5, 7 (slice 2).

## Done
- hero/HeroKit, HeroKits: seven kits (stats, cost, role, 4 abilities each with per-rank values) and the 28 effects.
- hero/FactionHero (interface over HeroUnit, defaults from HeroCore), HeroCore (state, cast goal, scripted leaps,
  frenzy, summons), FactionHeroAbility (ranks, level gates 1/3/5 and 6/8/10, cast/cooldown/mana, buttons, tooltips),
  HeroFx (enemy/friend queries via UnitGrid, damage/stun/root/knockback/fx, landing spots).
- hero/HeroProgression: growth 8/5/2% (the dip), boons (heal + chat + possessor banner), capture XP (100 on take,
  1/s held, 24 blocks). Hooks in CapturePointServerEvents.setOwner / reward.
- hero/HeroProd, HeroReviveProd, HeroTypes; ProductionItems (14 items, end of list), EntityRegistrar (7 types at the
  end, getEntityType default -> HeroTypes), Factions.registerEntity x7, T1 army labs get hero + revive on abilitySlot8.
- unit/units/heroes/*: 7 bodies (Vindicator, Stray, Brute, Thornbow, Cutlass Raider, Windcaller, Rifle Squad).
- unit/modelling/renderers/HeroRenderers (7, 1.1x scale); ClientModEvents / CommonModEvents registrations.
- hero/HeroBot: bot trains/revives its hero from minute 5 with Tier 2 (BotPlayer one-line hook), spends points, casts.
- Possession: Alt+1-4 ranks up (PossessionServerboundPacket.RANK_UP appended, PossessionServer.rankUp), HUD rank pips,
  mana bar, point hint, boon banner.
- UnitInfoCard: "Hero - <role>" line. HeroUnit.getFallenHeroes now filters by owner (was every player's).
- UnitAction: HERO_ABILITY_1..4 appended.
- tools/gen_hero_skins.py: skins (re-dyed from the body textures), portraits, 28 ability icons; lang (152 keys).
- gametest/HeroGameTests.java: trains at lab (x7), XP from a kill, boon at threshold, ranks, casts + cooldown,
  capture XP, XP curve dip, bot trains mid-game.

## Next / known
- Await CI; fix compile errors from annotations.
- Not verifiable headless: visuals (skins, arcs, HUD banner).
