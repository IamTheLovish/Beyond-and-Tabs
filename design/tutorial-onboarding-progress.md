# tutorial-onboarding progress

Spec and decisions: design/onboarding.md.

Done
- onboarding/ package: OnboardingStep, OnboardingSession, OnboardingSaveData, OnboardingServer, PassiveBot, packets,
  OnboardingClient (title hook, launch/resume, client-step detection, panel, highlights), FirstBattleScreen,
  ControlsReferenceScreen, FactionInfoCard, OnboardingTips, OnboardingUi
- hooks: UnitActionServerboundPacket (order), FactionPicker (card), MatchMenuScreen (Controls), client config (3 keys),
  HudSettingsScreen (tips box), PacketHandler, Server/ClientEventRegistrar
- lang: ~190 onboarding.reignofnether.* keys inserted after the faction taglines (tools: scratch add_lang.py, idempotent)
- gametest/OnboardingGameTests (8 tests)

- gametest/HeroGameTests.each_faction_hero_trains_at_its_lab: falls back to the unit list when the box query misses a
  spawned hero (its labs sit up to ~280 blocks along the grid; adding tests moved the grid and two chunks were no
  longer entity-loaded - the test failed on this branch only because of the layout shift)

Next
- CI green; then nothing planned. Possible: button highlight arrows on the command card (needs a HUD hook)

Known failures / unverified
- no client can run here: panel layout, title row split, particles, Resume flow are reasoned, not seen
- full scenario start (battlefield + passive bot together) is not game-tested (would form a battlefield in the test grid)
