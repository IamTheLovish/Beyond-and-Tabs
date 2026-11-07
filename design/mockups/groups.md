# Kingdom (VILLAGERS) build menu: groups and keys

Source: faction/Factions.java registerBuildings (origin/main) plus Defences.registerWithFactions, Ward Spire, Sun Spire. Keys come from keybinds/Keybindings.java.
Mock-up A proposed key = slot position in that tab (same position, same key in every tab). Mock-up B keeps today's keys.
Tab keys (proposed): Alt+1 Economy, Alt+2 Military, Alt+3 Defence, Alt+4 Tech. Every bare letter is already a build, command or pan key, and bare digits are control groups.

## Economy (13)

| Building | Group | Current key | Proposed key (A) | Mock-up B key |
|---|---|---|---|---|
| Town Centre | Economy | Q | Q | Q |
| Stockpile | Economy | Z | Z | Z |
| Metal Extractor | Economy | ; | E | ; |
| Wind Generator | Economy | J | R | J |
| Energy Converter | Economy | K | T | K |
| Storage Vault | Economy | . | Y | . |
| Solar Collector | Economy | none | U | none |
| Advanced Solar | Economy | none | I | none |
| Geothermal Plant | Economy | none | O | none |
| Town Market | Economy | H | L | H |
| Oak Bridge | Economy | C | F | C |
| Fusion Reactor | Economy | none | V | none |
| Advanced Fusion Reactor | Economy | none | none (13th, click only) | none |

## Military (5)

| Building | Group | Current key | Proposed key (A) | Mock-up B key |
|---|---|---|---|---|
| Barracks | Military | Y | Q | Y |
| Castle | Military | L | Z | L |
| Shrine of Prosperity | Military | F | E | F |
| Iron Golem | Military | , | R | , |
| Lab Annex | Military | none | T | none |

## Defence (16)

| Building | Group | Current key | Proposed key (A) | Mock-up B key |
|---|---|---|---|---|
| Watchtower | Defence | T | Q | T |
| Stone Rampart | Defence | none | Z | none |
| Portcullis Gate | Defence | none | E | none |
| Caltrop Field | Defence | none | R | none |
| Snare Net | Defence | none | T | none |
| Crossbow Turret | Defence | none | Y | none |
| Sunlance Ballista | Defence | none | U | none |
| Great Crossbow Turret | Defence | none | I | none |
| Skyward Ballista | Defence | none | O | none |
| Great Skyward Ballista | Defence | none | L | none |
| Sunburst Spire | Defence | none | F | none |
| Fortified Rampart | Defence | none | C | none |
| Iron Caltrop Field | Defence | none | ; | none |
| Chain Snare Net | Defence | none | J | none |
| Warden's Arbalest | Defence | none | none | none |
| Ward Spire | Defence | none | K | none |

## Tech (6)

| Building | Group | Current key | Proposed key (A) | Mock-up B key |
|---|---|---|---|---|
| Blacksmith | Tech | U | Q | U |
| Witch Hut | Tech | I | Z | I |
| Arcane Tower | Tech | O | E | O |
| Library | Tech | [ | R | [ |
| Sun Spire | Tech | none | T | none |
| Beacon | Tech | none | Y | none |

## Notes

- Why these groups: Economy = income, storage, power, trade, bridges. Military/Production = unit-producing or army-speed buildings (Shrine of Prosperity trains heroes, Iron Golem is a unit-building, Lab Annex speeds the army lab). Defence = Watchtower plus the static defences and the Ward Spire (the game-ender counter). Tech = research and upgrade buildings plus the win-condition buildings (Sun Spire, Beacon).
- Defence has 16 entries: the third row uses ; J K (free in that tab). The Warden's Arbalest (the anti-siege tower, Oct 29) has no key.
- Not shown: Villager House and Wheat Farm (not registered for the Kingdom), and custom buildings (appended by Faction.addCustomBuildings).
- Today the Defence, Ward Spire, Sun Spire, Beacon, Lab Annex, Solar and Geothermal buttons have no key at all.
