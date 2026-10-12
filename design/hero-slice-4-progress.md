# hero-slice-4 - progress

Branch `hero-slice-4` off origin/main 0882349. Spec: design/hero_mode.md section 4c (slice 4) - written first.

## Done
- Design: design/hero_mode.md 4c (Glory, shop points, who buys, 24 items, sell-back, the dip, privacy, UI, packets,
  weak-hero ability changes, capture points, slice 3 gaps analysed).

## Next
- Server: HeroItem/HeroItems/HeroRegistrar (items + effects), HeroGlory (sources), HeroShop (rules, buy/sell/use,
  auras), packets, privacy (ItemClientboundPacket, UnitSyncBatcher), revive/death Glory, Arch-Engineer item use.
- Client: shop panel (RTS card button + panel, first-person screen on B), possession HUD (Glory, item slots 5-0).
- Weak heroes (HeroKits + HeroBot ESCAPE), HeroShopGameTests incl. its own hero duel batch.
- Icons: tools/gen_hero_items.py; lang.

## Known failures
- none yet (nothing pushed to CI).

## Proposals
- (filled in as found)
