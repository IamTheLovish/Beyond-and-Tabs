# T1 siege - one lobber per faction (our "Rocko")

lovish, Oct 28: buildings are too tanky; he wants an easier way to break defences. Two answers: (a) normal units knock
buildings down 2x faster (a separate change), (b) **a new T1 siege unit per faction**, built from the faction's T1
production building, whose job is breaking defences. His artillery rule from the same night: **its sight stays below
its gun's reach**, so it needs spotters.

Status: **approved by lovish (Oct 29)** - the Rocko model (range 15, sight 12), walls and gates at the defence rate,
the HLT as a separate PR. Being built on `claude/siege-t1`. Art: placeholders until Itachi 1 draws the units.

## lovish's decisions (Oct 28-29)

- **Full damage on defences, half on other buildings** - approved.
- **Walls should be strong** (Oct 29): no x3 against walls and gates any more; they take the plain defence rate.
- **1 s set-up** after moving - approved.
- **Bots** use the lobber as every faction's T1 siege answer - yes.
- **Bombard Crew, Rocket Battery and Mortar Crawler stay as they are**, the anti-army artillery - yes.
- **The seven names are placeholders** - yes (art and final names come with Itachi 1's pass).
- **The model** (via Abigail): *"rocket bots from beyond all reason are useful at sniping defenses but struggle towards
  fast moving units. There should be a defense like the heavy laser tower that stops lobbers or rocket bots. the siege
  unit should only be good against light laser towers or first stage tier 1 defense and be moderately good against
  something like a beamer which is right below the hlt. Beamer is good but loses to a lot of rockos."* He also said yes
  to "keep range 10, pairs beat a raid tower" on another question, so both were written out below.
- **Range 15, Rocko style** (Oct 29) - picked over range 10. Sight 12.
- **Walls and gates at the defence rate**, 54 per shell like towers (Oct 29).
- **The HLT as specced, as a separate PR after the lobber** (Oct 29); its seven names stay placeholders too.
- **The three faction T2 defences at half rate, falling to 2-3 lobbers: fine as is** (Oct 29).

## The defence ladder, mapped onto BAR

Every faction has the same shared defence set (`building/buildings/defence/`, `design/defences.md`: one shape and
one price per job, faction looks only) and a few faction-only buildings. Numbers from `DefenceTower.Kind`,
`DefenceCosts` and the placements:

| BAR tier | Ours | HP | Range | Damage | Cost (M / E) | Gate |
|---|---|---|---|---|---|---|
| **LLT** (first-stage T1) | anti-raid tower | 260 | 14 | 7/s (x1.5 vs units of 70 HP or less), ground only | 110 / 150 | capitol |
| **Beamer** (the step below the HLT) | enhanced anti-raid tower | 600 | 16 | 18/s (x1.5 vs light), ground only | 260 / 400 | a T1 lab |
| **HLT** (the anti-siege tower) | **missing - proposed below** | | | | | |
| (not on the ladder) | Sky Watch / Enhanced Sky Watch | 280 / 550 | 20 / 22 | air only: harmless to a lobber | 120 / 160, 260 / 380 | capitol / T1 lab |
| (not on the ladder) | anti-T3 emplacement | 700 | 16 | 25 every 3 s (x3 vs experimentals) | 275 / 260 | T3 lab |
| walls | wall / fortified wall / gate | 400 / 1100 / 320 | - | - | 15 / 0, 40 / 20, 40 / 20 | capitol / T1 lab / capitol |

| Faction | LLT | Beamer | Faction-only defences | HLT |
|---|---|---|---|---|
| Sunforged | Crossbow Turret | Great Crossbow Turret | Watchtower (T1 garrison, 240 HP: shoots only with units inside) | none |
| Gravebound | Soulfire Spire | Great Soulfire Spire | Dark Watchtower (T1 garrison, 240 HP) | none |
| Horde | Spear Tower | Great Spear Tower | Bastion (T1 garrison, 320 HP) | none |
| Verdant Court | Thorn Sentinel | Elder Thorn Sentinel | Storm Oak (T2: 360 HP, 25 every 6 s at 22, prefers groups) | none |
| Tidewrought | Swivel Gun Post | Twin Swivel Gun Post | Lighthouse Battery (T2: 300 HP, 6 every 2 s at 20, a beam that lights units up at 30) | none |
| Stormcallers | Spirit Lantern | Great Spirit Lantern | - | none |
| Brassworks | Gatling Post | Heavy Gatling Post | Rail Battery (T2: 320 HP, 30 to every unit on a line every ~6.5 s at 30) | none |

**No faction has an HLT.** The three T2 defences are anti-army area weapons with low single-target damage; the
garrison towers are as strong as whoever stands in them (an empty one is a free kill).

## The lobber - shared base (all seven, one table)

| Stat | Value | Why |
|---|---|---|
| Cost | **95 M / 130 E**, 24 s, 2 pop (config food 70 / wood 50 / ore 40) | the Vindicator's price; the T1 artillery is 120-125 / 200-210, 34 s, 3 pop |
| Health / armour | **80 HP**, armour 2 | above the towers' "light" line (70), so it takes their plain rate |
| Speed | 0.23 | slower than infantry (0.25-0.26), quicker than the Bombard Crew (0.22) |
| Range / minimum | **15** (Rocko model; range 10 was the alternative) / 3 | see "The choice" |
| Sight | **12** | always below its gun (lovish's rule); through `Vision.artillery` once the vision PR merges (unscaled, a Spyglass caps one block under the gun) |
| Fire rate | one shell every **2.5 s**; **1 s set-up** after moving | |
| The shell | a **high lob, 2 s in the air whatever the range, aimed where the target stands** (no lead; the Bombard Crew leads 60%) | BAR's Rocko weakness: a building or a standing unit is hit, anything that keeps walking is blocks away when it lands |
| vs units | 4 per shell, splash 1.5 (half at the rim), **cannot hit air** | ~1.6 dps on a target that stands still, nothing on one that moves |
| vs defences | `BUILDING_DAMAGE_BONUS` +3.5, applied to the shell: **54 health per shell** | the shared defence set's towers and traps, the anti-T3, the garrison towers |
| **Walls and gates** | **54 per shell, the defence rate - no x3** (lovish, Oct 29: "walls should be strong"). It still auto-targets them | the lobber is exempt from WallSegment's x3 for building-bonus units; heavies and T3s keep theirs |
| Other buildings | **half: 27 per shell** | labs, economy, capitols, the three faction-only T2 defences |
| Behaviour | lobs over walls and ridges, backs off a target inside 3 blocks (`IndirectFire`); has to stop and commit (`FieldArtillery`) | same family as the existing artillery |

### Walls, in seconds

From the first shell (1 s set-up, 2 s flight, a shell every 2.5 s); walls do not shoot back, so range does not matter.

| Target | 1 lobber | 2 | 4 | At half rate instead (27 per shell): 1 / 2 / 4 |
|---|---|---|---|---|
| Wall segment (400) | **20.5 s** | 10.5 s | 5.5 s | 38 / 20.5 / 10.5 s |
| Fortified wall (1100) | **53 s** | 28 s | 15.5 s | 103 / 53 / 28 s |
| Gate (320) | **15.5 s** | 8 s | 5.5 s | 30.5 / 15.5 / 8 s |

Was 8 s / 18 s / 5.5 s for one lobber with the x3. For scale: an ordinary infantryman that is ordered onto a wall does
about 1.5 health/s (3 after the 2x change): 2-4 minutes for a wall. lovish picked the defence rate (left columns).

## The choice: range 15 (Rocko model, picked) or range 10 (pairs win)

Simulated tick by tick (`siege_sim.py`, in the coordinators' notes folder): the tower shoots the nearest lobber, one
at a time, from the moment the lobbers enter its reach; the lobbers walk in to their own range at ~2.5 blocks/s, set up
for 1 s and fire every 2.5 s; a shell in the air lands even if its lobber has died. Times run from the first tower
shot (or from "in position" when nothing shoots back). W = the lobbers win, L = the tower wins; the number is lobbers
lost. Real fights run a little quicker (a building falls at half its blocks).

| Target | Range 15 (Rocko), sight 12 | Range 10, sight 9 |
|---|---|---|
| **LLT**, a spotter on it | 1 lobber: W0 in 13 s (it outranges 15 > 14, no return fire); 2: W0 in 8 s | (a spotter changes nothing: it has to walk into the 14) |
| **LLT**, no spotter | 1: L (it walks to its sight, 12, inside the tower's reach); 2: W0 in 9 s | 1: **L** (12 s); **2: W0 in 9.6 s**; 3: W0 in 7 s |
| **Beamer** (600, 16) | 1-2: L; **3: W3 (an even trade: 285 M for a 260 M tower)**; 4: W2; 6+: W1 | 1-3: L; **4: W2**; 6: W2; 8+: W1 |
| **HLT** (proposed: 1000, 20) | 1-6: L (six die without killing it); 8: W4; 10: W3 | 1-6: L; 8: W5; 10: W4 |
| Anti-T3 emplacement | 1-2: L; 3: W1; 6+: W0 | 1-2: L; 3: W1; 8+: W0 |
| Walls / gates | as above (20.5 / 15.5 s alone) | the same |
| T1 lab (150, half rate) | 16 s alone, 8 s for 2 | the same |
| Storm Oak / Lighthouse / Rail Battery (half rate) | fall to 2-3 lobbers (1-2: L, 3+: W0-1) | the same or one more lobber |

- **Range 15** is the BAR Rocko: it **snipes an LLT for free when something spots it**, trades evenly with a Beamer
  at three and wins at four, is stopped by the HLT, and outranges the infantry that would defend the tower. Still
  two thirds of the long artillery's reach (22-24).
- **Range 10** is the short lobber lovish first asked for: **a pair beats a raid tower, one alone loses**; it is a bit
  weaker against the Beamer (four to win) and the HLT; spotters matter less (it has to walk in anyway), and every line
  unit with 12+ range outranges it.
- Both keep the dodgeable lob and the weakness to fast raiders (3-block dead zone, 1.6 dps on a standing target).

### Compared with the existing artillery

| | Lab | Cost | Build | HP | Range | Sight | vs units | Building health per shot | Building HP/s |
|---|---|---|---|---|---|---|---|---|---|
| Bombard Crew | Slipway, T1 | 125 / 200 | 34 s, 3 pop | 28 | 22 | 20 | 9 splash 2.5 every 4 s, leads 60% | 27 | 6.8 |
| Mortar Crawler | Machine Shop, T1 | 120 / 210 | 34 s, 3 pop | 32 | 22 | 20 | 14 splash every 5 s | 42 | 8.4 |
| Rocket Battery | Lotus Academy, T1 | 120 / 210 | 34 s, 3 pop | 30 | 24 | 20 | 4 x 5 splash every 6 s | up to 60 | up to 10 |
| **Lobber** | every T1 lab | **95 / 130** | **24 s, 2 pop** | **80** | **15** | **12** | 4 splash 1.5 every 2.5 s, no lead | **54 on defences and walls, 27 else** | **21.6 on defences** |

## Separate step: the HLT - an anti-siege tower for every faction

Its own PR, after the lobber's (lovish, Oct 29). One shared
tuning like the other towers (a new `DefenceTower.Kind.SIEGE_GUARD`), each faction its own look:

| Stat | Value | Why |
|---|---|---|
| HP | **1000** | six lobbers die without bringing it down |
| Range | **20** | outranges the lobber (either range) and every T1 line unit, but **not** the long artillery (22-24): as in BAR, artillery is the HLT's answer |
| Damage | **45 every 1.5 s** (30 dps), single target, ground only, no light bonus | two shots kill a lobber; it overkills raiders, so per metal the Beamer stays the better raid answer |
| Cost / gate | **450 M / 650 E**, a T1 lab (BAR's HLT is a T1 building), build-time x1.2 | about 1.7x the Beamer |
| Footprint | 3x7x3 | taller than the Beamer (3x6x3): readable as the heavy one |

Placeholder names: Sunforged Warden's Arbalest, Gravebound Bonecleaver Spire, Horde Skullsplitter Tower, Verdant
Ironbark Sentinel, Tidewrought Long Nine Post, Stormcallers Dragon-Eye Lantern, Brassworks Long Tom Post. Bot: raises
one between home and the first enemy lobbers it sees (as it does the Sky Watch for flyers). Tests: six lobbers lose to
one, ten win; a Bombard Crew outranges it.

## Per faction - the lobber (placeholder names)

Every placeholder: the **illager body** (the body the Bombard Crew, Mortar Crawler and Rocket Battery use - all three
are `Pillager` subclasses on `AbstractVillagerUnitRenderer`), a flat placeholder skin in the faction's palette made by
a small generator script (our own pixels only), the Bombard Crew's attack animation, and `BombardShell` with the
faction's BarFx tint. The table is the starting brief for Itachi 1.

| Faction (T1 lab) | Name | Theme | Look | Shell | Placeholder |
|---|---|---|---|---|---|
| Sunforged (Barracks) | Sunfire Mangonel | a two-man crew with a small torsion catapult | white-and-gold tabards, a gilded throwing arm with a sun-disc counterweight on a two-wheel frame | a pitch pot bursting in golden flame | illager body, white/gold skin |
| Gravebound (Graveyard) | Skull Hurler | a hunched ghoul with a long bone sling | grey-green flesh, ragged shroud, a sinew sling and a basket of skulls on its back | a skull trailing soul fire | illager body, bone-grey/green skin |
| Horde (Military Portal) | Boulder Chucker | a war-painted brute with a staff sling | red-and-ochre war paint, hide wraps, a sack of magma-cracked stones | a glowing basalt boulder | illager body, brown/red skin |
| Verdant Court (Grove) | Burl Slinger | a young treant flinging heavy burls with a whip-branch arm | sapling-bark body, mossy crown, one long branch arm | a knotted burl bursting in spores and bark chips | illager body, bark/moss skin |
| Tidewrought (Slipway) | Powder Monkey | a deckhand lobbing fused powder kegs (the Bombard Crew's short-range cousin) | striped deck shirt, bandana, keg harness, a lit fuse in its teeth | a small keg, black-powder burst | the Bombard Crew's skin |
| Stormcallers (Lotus Academy) | Thunderstone Monk | a monk who hurls a lightning-charged temple stone on a sling of wind | indigo robes, jade beads, a carved stone floating over open hands | a jade stone striking with a crackle of lightning | illager body, indigo/jade skin |
| Brassworks (Machine Shop) | Steam Lobber | a squat walking boiler with a short steam-catapult arm | riveted brass tank, stubby legs, a pressure gauge and a smoking spout | riveted iron shot, steam puff | the Mortar Crawler's skin |

## Code plan

**Lobber PR** (claude/siege-t1):
- `unit/units/shared/SiegeLobberUnit` (abstract): extends `Pillager`, implements `Unit, AttackerUnit,
  RangedAttackerUnit, FieldArtillery` (so `IndirectFire`). Every number in one `SiegeTuning` table (like
  `DefenceTower.Kind`); seven thin subclasses add the faction, tint and renderer; `instanceof SiegeLobberUnit` serves
  the bot and the tests.
- Shell: `BombardShell` gains the shooter's `getBuildingDamageMultiplier()` (the existing artillery has bonus 0:
  unchanged), and for the lobber a splash radius, a fixed flight time and the full/half rule (full on the shared
  defence set and the garrison towers, half on everything else).
- `WallSegment.damageMultFrom`: 1 for a lobber (walls take the defence rate), while `drawsAggroFrom` still lets it
  pick a wall as a target.
- Each T1 lab's production list gets its item; costs in `ReignOfNetherCommonConfigs.UnitCosts` / `ResourceCosts`;
  lang, HUD icon, a hotkey (`no_two_buttons_on_a_command_card_share_a_hotkey` guards it).
- Bot: `BotRoles.siegeFor` / `siegeIsT2` give every faction the lobber at its T1 lab; `isSiege` and
  `RaidTuning.isT1Vehicle` include it.
- Tests first (red, then green), per faction: trainable from its T1 lab; one spotted lobber kills a raid tower and takes no
  damage within a CI-slack
  limit; one Beamer beats two lobbers and four beat it; one lobber needs at least ~15 s for a wall segment (and
  breaks it inside a limit); little to units (15 s on a standing Vindicator is at most ~30 damage); **a walking unit
  is not hit**; sight < range, with a Spyglass, before and after the vision change. A JUnit test for the
  `SiegeTuning` arithmetic. Draft PR, flake-stress on 8 runners with `test:` prefixes.

**HLT PR** (separate): the new tower kind, seven looks, structures and icons through the defence generators, the bot
rule, its tests.

## Answered (lovish, Oct 29)

1. Range 15 (Rocko model), sight 12.
2. Walls and gates at the defence rate: 54 per shell (one lobber ~20 s on a wall, ~16 s on a gate).
3. The HLT as specced, a separate PR after the lobber.
4. Storm Oak, Lighthouse Battery and Rail Battery at half rate: fine as is.
