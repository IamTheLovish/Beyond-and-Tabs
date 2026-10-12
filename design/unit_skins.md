# Unit skins: audit and team colours

Branch `unit-skins` (Oct 12). lovish: "every unit intricate, detailed, complex, individual", in the faction palettes
from claude/design-factions.md. Previews: `design/skins_preview_<faction>.png`. Each tile shows the flat UV with the
team region tinted red, front and back mock-ups in red and in blue, the regenerated portrait, and two 1x mock-ups
(about the size a unit is on screen from the RTS camera).

## How team colour works now

RoN only tinted **outlines and selection circles** with the owner's colour (`PlayerColors.getPlayerDisplayColorHex`);
the units themselves were never tinted. This branch builds on that rather than baking colours into skins:

- A skin `foo_unit.png` may ship a companion **`foo_unit_team.png`** of the same size: grey-scale cloth (values
  160..255 carry the folds) on transparent pixels, covering a tabard, sash, mantle, plume, cape or hood.
- **`TeamColourLayer`** (unit/modelling/layers) draws the parent model again with that texture
  (`renderColoredCutoutModel`), multiplied by the owner's display colour. That is the same colour as the outline, so it
  follows the player-colour / alliance-colour toggle. Same geometry and same depth, so it passes LEQUAL the way vanilla's
  horse markings do, with no z-fighting. It skips invisible units, phasing units (which are drawn translucent) and units
  with no owner. A skin without a `_team.png` costs one cached map lookup.
- Under the team region, the base skin carries the same cloth in the **faction's default colour**, so unowned units
  (survival waves, previews, the inventory) still look designed.
- The layer is wired into `AbstractVillagerUnitRenderer` (every villager-model unit), `PiglinUnitRenderer`, the new
  `ZombieUnitRenderer` / `HuskUnitRenderer` / `SkeletonUnitRenderer` / `WitherSkeletonUnitRenderer`, and the Thornbow,
  Shade Ranger, Tide Priest, Ink Scribe and Moonwell Bearer renderers. Adding it to another renderer is one line:
  `this.addLayer(new TeamColourLayer<>(this));` plus a `_team.png`.
- Cost: one extra model draw per unit that has a team texture. That is the same order as an armour layer, with no
  allocations per frame.

## Generators

- `tools/hd_skinkit.py` is the toolkit. It has 5-tone ramps with cool shadows and warm highlights, and material
  painters that wrap continuously round a box: cloth folds, plate lames with bevels, specular stripes and rivets, mail,
  leather grain and stitching, fur strands, hair locks, bone cracks, bark grain, lacquer gloss, scales and quilting. It
  also has embossed 5x5 faction emblems (sun, skull, tusk, leaf, trident, coin, gear, anchor, moon, star, shell,
  flame), the team layer, portraits built from the finished skin, mock-ups and contact sheets. Everything is
  deterministic: randomness comes from a hash of the unit name and the pixel coordinates.
- `tools/gen_hd_skins.py` has one painter per unit, `python3 tools/gen_hd_skins.py [--only a,b] [--no-preview]`. It
  writes the same texture paths the renderers already use (no reference changes except the new ones listed below),
  plus `_team.png` and `mobheads/<unit>.png`. A portrait keeps the size its file already had (piglins 64x51).
- The older generators `gen_{verdant,tide,storm,brass,t2t3}_skins.py` still paint the same files for the units listed
  here. Run `gen_hd_skins.py` **after** them, or the older, flatter skins come back.

## Audit (Oct 12)

Legend: **HD** = new high-detail skin with a team region (this branch). **gen** = our earlier procedural skin
(flat fills plus noise). **RoN** = Reign of Nether's original artist texture, a placeholder for us. **vanilla** =
Minecraft's texture. **AM** = Alex's Mobs model (older gen skins paint only its UV outline).

### Sunforged (white plate, gold, sun banners)
| unit | model | texture | status | notes |
|---|---|---|---|---|
| Halberdier (vindicator) | villager | vindicator_unit | **HD** | white plate, team tabard + sallet plume |
| Crossbow Levy (pillager) | villager | pillager_unit | **HD** | gambeson + breastplate, team sash |
| Dome Cleric (evoker) | villager | evoker_unit | **HD** | vestments, team stole + mitre lappets |
| Royal Architect | villager | royal_architect_unit | **HD** | team mantle |
| Master Mason (villager) / Squire Runner (militia) | villager | villager_unit | RoN | **covered**: VillagerUnitProfessionLayer draws vanilla villager type + profession textures over the whole body, so a new base skin would barely show. Needs our own type/profession overlays (follow-up) |
| Field Medic (witch) | witch | vanilla witch | vanilla | follow-up |
| Tabard Paladin (iron golem) | golem | vanilla iron golem | vanilla | follow-up (golem layout exists in gen_t2t3_skins) |
| Royal Guard, Enchanter, Windcaller | own models | RoN | RoN | heroes / hero slice, left alone |
| Sun Colossus | golem | gen (t2t3) | gen | big unit, left to big-units |

### Gravebound (black, bone, soul-blue)
| unit | model | texture | status | notes |
|---|---|---|---|---|
| Gravedigger (zombie villager) | villager | zombie_villager_unit | **HD** | team shroud mantle |
| Embalmer | villager | embalmer_unit | **HD** | team stole edges |
| Ghoul (zombie) | zombie | zombie_unit (new) | **HD** | was vanilla zombie; new ZombieUnitRenderer |
| Bone Legionary (husk) | zombie | husk_unit (new) | **HD** | was vanilla husk; new HuskUnitRenderer |
| Grave Archer (skeleton) | skeleton | skeleton_unit (new) | **HD** | was vanilla; new SkeletonUnitRenderer |
| Frost Archer (stray), Drowned Thrall | vanilla | vanilla | vanilla | both have vanilla overlay layers (stray clothing, drowned outer layer) that would need replacing too; follow-up |
| creeper, spider, poison spider, silverfish, zoglin, warden, phantom, bat | vanilla | vanilla | vanilla | non-humanoid; follow-up |
| Bogged | skeleton | RoN bogged.png | RoN | follow-up |
| Wraith, Necromancer, Wretched Wraith | own models | RoN | RoN | heroes, left alone |
| Bone Dragon | own | gen (t2t3) | gen | big unit |

### Ironhide Horde (rust-red iron, bone plate, furs)
| unit | model | texture | status | notes |
|---|---|---|---|---|
| Clan Builder (grunt) | piglin | grunt_unit (new) | **HD** | was vanilla piglin; team sash over fur vest |
| Shieldbearer (brute) | piglin | brute_unit (new) | **HD** | was vanilla brute; team tabard |
| Spear Thrower (headhunter) | piglin | headhunter_unit (new) | **HD** | was vanilla brute; team sash + arm bands |
| Berserker (wither skeleton) | skeleton | wither_skeleton_unit (new) | **HD** | was vanilla; team war paint + waist sash |
| Bonewright | piglin | gen (t2t3) | gen | already ours; no team region yet |
| zombified piglin, hoglin, strider, blaze, ghast, magma cube, ravager | vanilla | vanilla | vanilla | follow-up |
| Marauder, Piglin Merchant, Wildfire, Armoured Hoglin | own models | RoN | RoN | heroes, left alone |
| War Mammoth | own | gen | gen | big unit |

### Verdant Court (green / silver, living wood, moss, lanterns)
| unit | model | status | notes |
|---|---|---|---|
| Seedshaper | villager | **HD** | team hood (whole top reads as owner) |
| Elder Druid | villager | **HD** | team stole; moss creeping up the hem; antler circlet |
| Bloom Priestess | villager | **HD** | team veil under a flower ring |
| Leafblade | villager | **HD** | team scarf-mask + tails |
| Hive Keeper | villager | **HD** | team hat band + sleeve bands |
| Thornbow | skeleton | **HD** | team hood |
| Shade Ranger | skeleton | **HD** | team mantle down the back |
| Moonwell Bearer | witch | **HD** | team cowl |
| Elder / Sentinel Treant, World Tree Walker | golem | RoN-era textures, no generator | follow-up |
| Stag Lancer, Owl Watcher, Fox Courier, Wisp Choir | AM / vanilla | vanilla/AM | follow-up |

### Tidewrought (teal + brass, kelp, coral)
| unit | model | status | notes |
|---|---|---|---|
| Shipwright, Harbourmaster, Cutlass Raider, Reef Guard, Bombard Crew, Harpooner, Flak Swivel, Kraken Caller | villager | **HD** | team kerchief / cap band + sash / feather / tabard / cap band / scarf / cords / hood |
| Tide Priest | witch | **HD** | team mitre |
| Sky Corsair, Leviathan, Mudskipper Sloop, Mortar Galley, Gull Spotter | own / AM | gen / AM | Leviathan belongs to brass-t3 / big units; ships follow-up |

### Stormcallers (red lacquer, jade, gold)
| unit | model | status | notes |
|---|---|---|---|
| Geomancer, Ley Master, Blade Dancer, Jade Guard, Fire Adept, Frost Adept, Wind Monk, Gravity Monk, Mirror Mage, Seer, Rocket Battery | villager | **HD** | team cap cord / stole / sash + ribbon / tabard / cuffs / cuffs / shoulder sash / shoulder sash / hood / lappets / crew cap |
| Ink Scribe | witch | **HD** | team cap crown |
| Paper Crane, Thunder / Celestial Dragon, Phoenix, Lantern Fortress, Earthshaker | AM / golem | gen | big units / AM outlines; follow-up |

### Brassworks (brass, soot black, furnace orange)
| unit | model | status | notes |
|---|---|---|---|
| Tinker, Engineer Captain, Rifle Squad (+ Deployed Turret), Sapper, Flak Cart, Shield Projector, Mortar Crawler, Tesla Walker | villager | **HD** | team neckerchief / sash + cap band / cap crown / arm band / mantle / mantle / plume / sash |
| Iron Warden, Steam Runner, Gyro Scout, Gyrocopter, Steam Juggernaut, Drill Burrower, Repair Crawler, Clockwork Spider | golem / strider / parrot / ravager / silverfish / spider | gen | machines; a team layer needs per-model team textures (follow-up) |

## Readability at RTS zoom: findings

1. **Team colour was invisible on units themselves.** Only the outline and selection circle showed it, and the outline
   only appears while selected or hovered. Fixed for the 50 HD units by TeamColourLayer. Each one puts its team region
   on a surface the top-down camera sees: shoulders, hoods, plumes, hat crowns, tabards front and back.
2. **Livery armour hides skins on Sunforged, Gravebound and Horde units** (UnitDressServerEvents: workers get a
   chest piece, melee get chest, legs and helm, ranged get chest and head). `VillagerUnitModel` also hides the robe
   (`jacket`) whenever a chestplate or leggings are worn. So for *owned* units of those three factions, most of the new
   skin, and the team region on the jacket, sits under dyed leather or Epic Knights armour. The Verdant, Tidewrought,
   Stormcallers and Brassworks already dress only the commander for this reason. **Recommendation for lovish:** give
   the three core factions the same "commander + boots only" livery, since TeamColourLayer now does the livery's job of
   showing whose a unit is. This branch did not change it: it touches server-side dress logic, and
   `SkirmishGameTests.liveries_give_zero_armour` asserts that a vindicator gets a chest piece.
3. **Villager workers and militia** are painted over by the vanilla villager type and profession overlays. Their base
   skin barely matters until those overlays are ours.
4. **Value contrast between team cloth and body.** Sunforged white plate with a mid-tone team tabard reads in every
   player colour. Very dark player colours (black, gray) on the Gravebound's black cloth read weakly. The bone trim
   next to their team regions helps, but a light trim line on each team region would help further (follow-up).
5. **Witch-model hats** (Tide Priest, Ink Scribe, Moonwell Bearer) are the tallest silhouettes in their factions.
   Making the crown the team colour makes them the easiest units to find by owner.
6. Non-humanoid vanilla units (creeper, spider, hoglin, blaze, ghast...) still carry vanilla textures and no team
   colour. They need per-model painters. That is the next batch for a follow-up.
