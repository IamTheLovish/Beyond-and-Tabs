# hero-possession - progress

Branch `hero-possession` off origin/main 9b69b0a. Design: design/hero_mode.md.

## Done (slice 1, first full pass - awaiting CI)
- design/hero_mode.md written.
- hero/HeroRoles.java: Hero-role players -> team commanders; lending (/rts-possession allow|deny).
- hero/PossessionServer.java: claim/release/mayPossess, input/attack/ability validation, drive() for the mixin,
  order diversion -> objectives, ping objectives, death/logout release, +10% damage, 0.85x attack cooldown.
- hero/PossessionServerboundPacket + PossessionClientboundPacket (registered at the end of PacketHandler).
- mixin/PossessionMobMixin: cancels Mob.serverAiStep for possessed mobs.
- hero/PossessionClientEvents: camera entity, input, HUD (UNIT FEED, HP/XP/abilities, crosshair, radar,
  objective banner + particle waypoint), ally name tag, pause-screen "Return to command".
- OrthoviewClientEvents: isEnabled() false while possessing; Backspace/F12 -> PossessionClientEvents.onToggleKey.
- UnitActionServerboundPacket.accept -> divertOrders; MapMarkerServerboundPacket -> onPing.
- Lobby: Seat.heroRole, Action.SET_ROLE (appended), Role button, start rules, Hero seats get no base.
- GameTests (end of SkirmishGameTests): possession_claims_a_unit_and_drives_it, possession_refuses_an_enemy_unit,
  possession_drops_input_from_a_non_possessor, possession_ends_when_the_unit_dies, hero_role_seat_gets_no_base.

## Next
- CI green; then in-game check by lovish (camera feel, movement speed, ranged attacks).

## Known failures
- (fill in from CI)
