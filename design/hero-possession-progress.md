# hero-possession - progress

Branch `hero-possession` off origin/main 9b69b0a. Design: design/hero_mode.md.

## Done (slice 1) - CI green at b3f908f (build + bar build + all GameTests)
- design/hero_mode.md written (roles, possession, objectives, XP/boons, power dip, 7 faction heroes, balance, slices).
- hero/HeroRoles.java: Hero-role players -> team commanders; lending (/rts-possession allow|deny|release).
- hero/PossessionServer.java: claim/release/mayPossess, input/attack/ability validation, drive() for the mixin,
  order diversion -> objectives, ping objectives, death/logout release, +10% damage, 0.85x attack cooldown.
- hero/PossessionServerboundPacket + PossessionClientboundPacket (registered at the end of PacketHandler).
- mixin/PossessionMobMixin: cancels Mob.serverAiStep for possessed mobs (one map lookup per mob, empty-map early-out).
- hero/PossessionClientEvents: camera entity, input, HUD (UNIT FEED, HP/XP/abilities, crosshair, radar,
  objective banner + particle waypoint), ally name tag, pause-screen "Return to command", V/F5 view toggle.
- OrthoviewClientEvents: isEnabled() false while possessing; Backspace/F12 -> PossessionClientEvents.onToggleKey.
- UnitActionServerboundPacket.accept -> divertOrders; MapMarkerServerboundPacket -> onPing.
- Lobby: Seat.heroRole, Action.SET_ROLE (appended), Role button, start rules, Hero seats get no base.
- GameTests (end of SkirmishGameTests): possession_claims_a_unit_and_drives_it, possession_refuses_an_enemy_unit,
  possession_drops_input_from_a_non_possessor, possession_ends_when_the_unit_dies, hero_role_seat_gets_no_base.

## Next (slice 2, see hero_mode.md section 7)
- In-game feel check by lovish (camera, movement delay, ranged aim, abilities that need special targeting).
- Capture-point XP, boon banners, growth curve change, team fog for Hero-role players, client prediction.

## Known issues
- Not verified in a real client (CI is headless): HUD layout, camera, mouse look, third person.
- stress_8v8_server_systems_stay_in_budget failed once at 8.09 ms vs 8.0 ms (CI timing noise; passed on the next runs).
