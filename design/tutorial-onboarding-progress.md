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
- CI green on c1b1395 (build + bar with all GameTests, the 8 onboarding tests included). Nothing planned; possible
  later: arrows on the command card's buttons (needs a HUD hook), a sparring bot that fights back at the last step
- after Quit, the Rival keeps its passive brain only until the world reloads; then it wakes as a normal bot

Known failures / unverified
- no client can run here: panel layout, title row split, particles, Resume flow are reasoned, not seen
- full scenario start (battlefield + passive bot together) is not game-tested (would form a battlefield in the test grid)
