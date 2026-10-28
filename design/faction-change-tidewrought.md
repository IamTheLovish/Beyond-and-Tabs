# Faction change: Tidewrought dropped, a new faction replaces it (lovish, Oct 28 2026, 04:22-04:24 ET)

## Decision
- lovish: "tidewrought needs reworked" -> "make it an alien faction" -> (after discussing how water would work) "I dont
  really understand how were going to incorporate water", "its going to be difficult in minecraft" -> **"lets drop them
  and add a new faction"**.
- Why: Tidewrought is a sea faction in a game played on land. Water barely features in its gameplay today (the
  Mudskipper Sloop is amphibious, maps have little water, labs keep water out of walkable space), so the theme was a
  costume. Making water real gameplay (water maps, docks, swimming advantages) is a big, risky job in Minecraft
  (flowing water, pathfinding, drowning/stuck units, performance) that only pays off on water maps.
- An alien faction was discussed as the replacement direction. lovish rejected merging alien ideas INTO Tidewrought
  (wreck-salvaging deep-sea aliens). The new faction's theme is NOT settled - design it with lovish from references,
  the way the vehicle labs were (see claude/vehicle-labs-lessons-2026-10-28.md: one primary reference, imitate it).

## Not decided yet (ask lovish)
- The new faction's theme (alien was his first instinct; which kind of alien, or something else).
- Its name, roster and roles (reuse Tidewrought's unit roles as a starting skeleton or design fresh).
- When to do it (it is a big lane: units, buildings, bots, tests, balance).
- Whether ref37 (futuristic glass spire on water) and the later "futuristic faction" idea relate to it.

## What dropping Tidewrought touches (for whoever plans it)
- Code: the `tide` packages (units: Cutlass Raider, Reef Guard, Bombard Crew, Tide Priest, Harpooner, Flak Swivel,
  Mudskipper Sloop, Corsair Captain (hero), Kraken Caller, Mortar Galley, Sky Corsair, Harbourmaster, Shellback Turtle,
  Salvager, Leviathan; buildings: Wreck Harbour capitol, Slipway, Drydock, Abyssal Shipyard, economy set, Tide-Pool Pump
  extractor), the faction registry, BotPlayer's kit, TierGate pairings, RaidTuning.isT1Vehicle (Mudskipper Sloop,
  Bombard Crew), GameTests that use tide units, balance tables, lang, icons, design/tidewrought_plan.md.
- Vehicle labs: the Careening Hard is cancelled; the new faction gets its own vehicle lab once its theme exists.
- Keep the seven-faction game playable while the new faction is built (do not delete Tidewrought until its replacement
  can be selected, unless lovish says otherwise).
