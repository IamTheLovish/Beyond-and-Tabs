# hero-slice-3 progress

Branch `hero-slice-3`, based on origin/main 102ba10. Spec: design/hero_mode.md "Slice 3".

## Done (code written)
- Team fog for Hero-role players: `FogOfWarServerEvents.fogAppliesTo` / `teamOwnersFor` (server), client
  `FogOfWarClientEvents.refreshLocalIsRTSPlayer` + `PossessionClientEvents.isTeamOwner`; ROLE packet carries the
  commanders; HeroRoles changes drop the fog cache; relog re-sends ROLE (`PossessionServer.onPlayerJoin`).
- Movement prediction: `PossessionPredictionClient` + client mixin `PossessionPredictionMixin`
  (Entity.isControlledByLocalInstance), serverbound MOVE claims, `PossessionServer.applyClaim` validation, SYNC
  corrections / suspend during ability AI windows and leap arcs.
- Combat feel + polish: `PossessionHudClient` (crosshair, hit markers, damage numbers + config
  `hero_damage_numbers` + Esc-menu toggle, cast feedback via CAST packet, cooldown-ready cue, compass + objective
  arrow, radar terrain). Server: HIT (LivingDamageEvent), CAST (`castCheck`).
- CR-07 (client prints the hero start message with the bound key), CR-17 (`HeroRoles.revoke`, clearAll sends
  role off).
- GameTests in gametest/HeroGameTests.java: hero_role_fog_equals_the_teams,
  possession_corrects_an_impossible_client_position, hero_role_off_is_sent_on_reset.

- CI green on 8ae484c (build + bar job with GameTests). design/hero_mode.md (section 4b, slices, known gaps) and
  design/code_review.md (CR-06/07/17 statuses) updated.

## Next
- Nothing required for slice 3. Later: hero animations/sounds, building-target aim (hero_mode.md "Later").

## Known failures / unverified
- Not verified in a real client (CI is headless): prediction feel, HUD layout, damage numbers.
