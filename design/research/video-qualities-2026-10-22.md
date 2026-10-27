# Video Qualities Catalogue - "Beyond and Tabs"
Sources (timestamps = recording clock mm:ss, +/-2 s; the YouTube player clock jumps because the recorder seeked, so ignore it):
V1 17-03-54: Magic Spells Ascension trailer 0:00-0:50, then Top-10 #10 Biomes O' Plenty ~0:52-0:58, #9 Mowzie's Mobs 1:00-1:51.
V2 17-05-52: Mowzie's end 0:00-0:23, #8 Cave Dweller 0:24-0:34, #7 Bloop 0:35-0:53, #6 Eternal Caves 0:54-1:50.
V3 17-07-49: Eternal Caves end 0:00-0:03, #5 Adventure Craft 0:04-1:00 (+ unlabelled add-on 1:00-1:10), #4 Eternal End 1:11-1:45.
V4 4c65d3cb (fullscreen): Eternal End 0:00-0:33, #3 Better Structures 0:34-0:59, #2 Naturalist 1:00-1:15, #1 Tinkers' Construct 1:16-1:35.
V5 Shorts "Usefull Magic Mod": rune rings, sigils, beams 0:00-0:26, rest unrelated shorts.
Originals only: take the principle, never the asset. Footage is stylised Minecraft; realism must come from our own lighting, weight and materials.


## TOP 10 HIGHEST-IMPACT
1. Anticipation > hold > snap > recovery on heavy attackers (A1).
2. Layered spell VFX: floor rune decal + body shape + sparks + flash, each with a different lifetime (E1).
3. Charge-up telegraph before a beam/ability, three readable beats (C1).
4. Bosses built from independently moving parts (floating segments, orbiting halo, drifting limbs) (A4, A6).
5. One silhouette and one lit trait per class/faction (M1).
6. Biome identity = one dominant hue + one accent + its own particle set + fog colour (W1).
7. Dynamic weather as a whole-screen grade swap (W4).
8. Ornate-frame skill-tree UI: node path, big selected node, side detail card, key hints (U1).
9. Ground-rune telegraph decals and pillar-of-light beams visible from afar (E3, C2).
10. Hit-react ragdoll that stays down 1-2 s, plus mob packs cycling attacks (A7).

---

## 1. ANIMATION
**A1. Wroughtnaut wind-up (Mowzie's).** V1 1:02-1:20. The iron golem rears back, whole torso tilts, horned head leads, arms rise overhead, a held beat, then slams. At 4 fps the hold is ~3 frames (0.75 s) before a 1-2 frame strike. The player counters with a low dive-roll, sword trailing, then a head-first tumble when hit (1:14-1:18).
*Why:* weight = long wind-up + fast strike (slow-in, fast-out); the held pose is the readable telegraph.
*BAT:* every heavy unit (siege, giant, hero ult) gets 0.4-0.7 s wind-up with torso counter-lean, 2-3 tick hold, 2-tick strike. Scale timings by unit mass.
**A2. Face close-ups carry intent.** V1 1:04-1:08, 1:15: helmet front with emissive red eye slits, cut in for 1-2 s between wide shots.
*BAT:* heroes get a 2-pixel emissive eye/visor strip that brightens on cast; use in ult cinematics.
**A3. Secondary motion on the hero.** V1 1:16-1:19, V2 0:07-0:10, V4 0:36-0:46. Cape, hair and arm-band overshoot and settle ~2 frames after the body stops; head lags torso; elbows lead hands.
*BAT:* two follow-through bones per unit (cloak, banner or tabard). Spring physics inside 40 blocks, baked shader sway beyond (hundreds of units).
**A4. Ancient Serpent of floating stone segments.** V2 1:13-1:17 (caption "Summon the Ancient Serpent"). Carved golden-glyph blocks hover and orbit a crowned head, then burst together in cyan-green energy.
*Why:* disconnected rigid parts make a long creature cheap and the gaps read as magic.
*BAT:* worm/dragon/colossus bosses of rigid segments with chain-lag (each segment trails the previous by N ticks); glyph emissive maps.
**A5. Winged mounts and flyers.** V3 0:14-0:19 armoured dragon mount (orange membranes, cyan edge trim), V3 1:02-1:09 golden, blue and skeletal dragons with rider, V4 0:06-0:10 Void Dragon. Wing cycle = down-stroke, long glide hold, slow up-stroke; body bobs opposite; tail lags.
*BAT:* air units use a 3-key flap with glide hold; rider cape mirrors flap one tick late. Wing membranes with a lighter edge for silhouette.
**A6. Idle with three unsynchronised rhythms.** V1 1:23-1:30 Barako on throne: body breath, spinning sun-halo, orbiting spikes at unrelated periods. V2 0:07-0:12 Sculptor floats: knees bend, arms drift independently, head sways.
*BAT:* idle = breath + one ornament loop at a different period; random phase per unit so squads never pulse together.
**A7. Hit-react and swarm.** V4 0:49-0:58 (Better Structures piglin brawl): 3-4 piglins lunge in a pack and keep cycling swipes; the player is thrown, lies limp ~2 s; the sword-arm close-up at 0:52.
*BAT:* three hit-react variants (flinch, stagger, launch); downed hero lies in a pose before fade; attackers stagger their attack start so packs don't sync.
**A8. Mouths with depth.** V1 1:36-1:42 Frostmaw: teeth, gums, tongue, blue pupils ringed yellow; V4 1:08-1:10 elephant tusks and brow. Mouth is inner geometry, not a flat texture.
*BAT:* monster/hero heads: separate jaw box that opens 20-35 deg during roars; inner mouth mesh with darker, wet-looking texture.
**A10. Exaggerated "cartoon" posing.** V4 0:36-0:46 squash on landing, stretch on lunge, shouted mouth shapes.
*BAT:* only for ability casts and deaths (10-15 % overshoot then settle); keep walks realistic to match hyper-real direction.


## 2. EFFECTS / VFX
**E1. Layered cast stack.** V1 0:03-0:14. A single cast: magenta rune ring decal rotates on the floor under the book, glyph ring around the caster, white star sparkles at the hand, a cyan crescent shape for the hit, bloom flash on impact, sparkles drifting ~1 s.
*Why:* each layer has its own life (0.1 s flash, 0.4 s shape, 1 s sparks), giving attack, body and tail.
*BAT:* every ability spec = 4 layers (ground decal, body mesh/billboard, particles, flash) and 3 lifetimes. Under load cull sparks first, then decals, never the body.
**E2. Colour-coded schools.** V1 0:15-0:34 and 0:25-0:34: yellow radiant, magenta arcane, blue frost, orange fire, violet void; rune circle, glow and hotbar icons match.
*BAT:* 10 factions: 1 hue + 1 accent for all FX, plus a faction rune alphabet for decals.
**E3. Ritual and pillar of light.** V3 0:04-0:14. Fire pit and smoke, then a white light shaft rises, purple/pink glyph sparks, ember bursts and smoke puffs; the camera holds still so the effect reads.
*BAT:* capitol upgrades, hero revives and rally points raise a vertical shaft visible across the map, faction-tinted; embers + smoke at base.
**E4. Rune rings and helix (Shorts).** V5 0:00-0:26. White wire rings of different radius spin on one axis (0:00-0:03); orange pentagram plus rising ring stack (0:04-0:08, 0:19-0:24); pink helix tornado (0:12-0:14); white beam with a sparkle halo (0:25-0:30). All thin emissive lines on near-black.
*BAT:* a library of ring/helix emitters (line particles, additive blend) for buffs, auras, summons; adjacent rings counter-rotate.
**E5. Voxel explosions.** V4 0:17-0:19: orange/yellow cubes expand then dissolve, grey debris cubes of varied size, white flash. V3 0:48-0:51: fire tornado, then ground-fire ring with a yellow rune circle on grass.
*BAT:* siege impact = 1-frame flash + 6-10 debris chunks that spin and bounce + ground dust ring + scorch decal 20 s.
**E6. Chain lightning.** V3 0:49: violet arc jumps target to target, void orb in front, spell name caption.
*BAT:* jagged line re-seeded every tick, spark at each hit, thickness decays per jump.
**E7. Contact FX.** V1 1:12 dust puff on the dive, V2 0:11-0:15 debris cubes drop and bounce around the arena, V2 0:19 sand flecks on landing.
*BAT:* every heavy footfall/landing spawns 3-5 tinted pixels, only within 30 blocks.
**E9. Sparse ambient glow.** V2 1:05-1:08 and 1:22-1:25 glow bugs and fireflies in 2-3 sizes, drifting slowly; V2 0:56-1:00 embers.
*BAT:* 1-2 ambient particles per 10 blocks, biome-specific (ash, pollen, spores).
**E10. Glow budget.** V2 0:28-0:33 Cave Dweller: only eyes and teeth are lit; V4 0:36-0:48 teal soul lanterns on dark red Nether brick.
*BAT:* dark-fantasy factions spend glow on eyes, blade edges, braziers; rest stays unlit so glow pops.


## 3. PROJECTILES
**P1. Beam with muzzle flash.** V2 0:02-0:04: round flash at the hand, thick white-yellow core, orange gradient edge, lens streaks; target flinches in an ice burst.
*BAT:* bolts get hand flare + bright core + soft glow + 3-frame fade trail.
**P2. Orb + orbiting ring.** V1 0:29-0:34 void orb with chain ring; V1 0:08 rotating runic disc pulled in front of the caster.
*BAT:* projectile = core + ring + trail; ring spin rate shows speed.

## 4. COMBAT FEEL
**C1. Three-beat charge-up.** V2 0:00-0:04: arm pull-back (3 frames), yellow sphere grows (2), beam releases (4), enemy flinch with ice burst. Readable even when the shot is only 1 s.
*BAT:* every hero ability shows wind-up on the model + on-ground shape; wind-up 0.3-0.8 s.
**C2. Ground telegraph decals.** V1 0:08-0:14, V3 0:49: circular rune decal under the target before detonation.
*BAT:* League-style AoE indicators drawn as faction rune decals, ring tightens as timer expires; enemy red, ally cyan.
**C3. Screen punch only on big hits.** V1 0:06 radial white flash, V4 0:18 flash frame, V3 0:49 orange tint.
*BAT:* 2-tick flash + 3-6 px shake scaled to damage; none on basic attacks.
**C4. Mana and charge HUD.** V1 0:03-0:12: thin blue MANA bar, "Charge 3/3" pips above the hotbar, spell name pops over the hotbar on select ("Radiant Spellsword").

## 5. WORLD & ATMOSPHERE
**W1. One hue per area (Eternal Caves).** V2 0:54-1:10: amber crystals + lava, teal jungle cavern with bulbs, desert ochre cave with cacti, pink/violet amethyst vault, grey ash; each with different particles.
*BAT:* generated regions blend 3-4 palettes; faction territory tints fog and grass.
**W2. Light shafts and sun haze.** V2 1:10-1:11 god-rays; V3 0:40-0:45 and V4 0:28-0:31 sunset haze, huge soft sun disc over ruin silhouettes; V4 0:26 low sun warming a jungle temple.
*BAT:* fog colour matched to sky; warm key / cool shadow split; layers of haze separate foreground ruins from horizon.
**W3. End biomes.** V3 1:15-1:35 and V4 0:00-0:12: floating islands with hanging ice crystals, pink fungal spires, cyan hanging vines, golden grass shelves, black void with a purple sun; "Shadow Fold" near-black with sparse white dots.
*BAT:* generator places 5-6 landmark silhouettes per biome (spires, hanging roots, arches, floating shelves).
**W4. Dynamic Weather.** V2 1:09-1:12: sandstorm yellow fog, underwater blue wash, blizzard white noise with snow, lava heat orange; the player becomes a dark figure.
*BAT:* weather per region (sand, ash, rain, snow) driving fog colour, particle density, audio and a gameplay modifier (vision radius).
**W5. Terrain blending.** V2 1:24-1:25 biome edges dissolve; V1 0:55-0:58 orange meadow with dusk sky.
*BAT:* noise-blended borders, no hard biome cuts.
**W6. Complementary grading.** V1 0:15-0:20 red-orange autumn + teal water; V1 0:08 dusk purple; V2 0:28-0:30 desaturated near-black corridors.
*BAT:* a LUT per map theme with lifted blacks and warm highlights.


## 6. STRUCTURES / BUILDINGS
**S1. Culture through roofline and banners.** V2 0:15-0:22 white-and-red temple complex with tiered curved roofs (Sculptor arena); V3 1:20 desert village with red war banners; V2 1:26-1:29 dwarven blue-steel halls with lanterns and anvils.
*Why:* roof silhouette + two accent colours identify a culture at RTS zoom.
*BAT:* each of 10 capitols gets a unique roofline and banner colour; tiers add trim and height, not new footprints.
**S2. Ruins as landmarks.** V3 0:34-0:38 moss-eaten stone towers, 0:35 white wizard tower with maroon spires; V4 0:28-0:31 sunset jungle temple.
*BAT:* 2-3 neutral landmarks per map for navigation and camps.
**S3. Interior dressing.** V1 0:15-0:22 crucible hall: bookshelves, cobwebs, hanging lanterns, dummies; V2 1:26-1:30 carved-glyph temple lit teal.
*BAT:* capitol interiors seen at max zoom: three light sources, props that tell the faction's job.
**S4. Upgrade chains.** V4 1:20-1:33 Tinkers: stencil table > melter > smeltery grows from small to large; V3 0:52 watchtower with archers visible.
*BAT:* upgrades add height and detail to the same footprint (outpost > watchtower > keep).


## 7. CREATURES & MOB DESIGN
**M1. Silhouette + lit trait.** V1 0:17-0:30 Crucible line-up: pointed-hat blue wizard, hooded green rogue, heavy knight with white cross visor, horned fire mage with lava crown, frost mage; V4 0:17-0:23 Friends & Foes: purple end-golem, orange insect-mech, white void spindly one.
*BAT:* shape code: round = tank, triangle/hat = caster, tall-thin = ranger; one lit trait per unit (visor, gem, lantern).
**M2. Armour as tiering.** V2 1:26-1:29 dwarven armour with glowing blue ornaments; V3 0:23-0:25 Giant Boar armour; V4 1:25 tools.
*BAT:* unit tiers add emissive trim, horns, plates rather than recolours.
**M4. Horror by restraint.** V2 0:24-0:34 Cave Dweller: gaunt, bright teeth, shown in near-total darkness, red "XP" glitch icon (0:27).
*BAT:* dark-fantasy faction revealed by motion and eyes, not full lighting.
**M5. Variants on one body.** V1 1:23-1:25 three Barakoa birds with different crowns; V4 0:36-0:38 sand golems in a row.
*BAT:* 3 head/weapon variants per unit type; offset steps in formations to avoid clone look in hundreds.


## 8. SYSTEMS
**Y1. Classes > paths > fusion.** V1 0:15-0:34 five Crucibles (starting classes), V1 0:20-0:35 ascendant skill trees (Pyromancy shown), V1 0:25-0:35 fusion spells combining two schools.
*BAT:* hero path chosen ~level 6, second path fusion at ~16 unlocking a new ultimate.
**Y2. Legendary items with tooltip.** V1 0:36-0:41 chest of glowing weapons; V2 1:41-1:49 "Verdite Sword" tooltip (+2600 durability, +6 gemstone slots, effect line).
*BAT:* name, source tag, one-line effect, two stats, gem slots.
**Y3. Smithing/crafting chain.** V2 0:54-1:15 "Equipment Upgrades" with a dwarf smith; V4 1:18-1:33 stencil table > pattern > melter > smeltery > armours ("Fully customisable").
*BAT:* forge building upgrades unit tiers; each stage visible on the model.
**Y4. Loot discovery.** V2 1:32-1:36 "Loot Structures", "Unlock Treasures" sparkle burst; V3 0:39 chest GUI; V4 0:55 chest mimic.
*BAT:* neutral camps drop loot with a sparkle burst and name pop-up.

## 9. UI / UX
**U1. Skill tree.** V1 0:23-0:26: gold-and-violet ornate frame on black, nodes on a horizontal path, selected node larger, right detail card with icon/description/cost, hint row "Navigate / Select / Exit"; five school icons on a hotbar-like strip (0:25).
*BAT:* hero tree as 3 horizontal branches; selected node glows; side card with numbers; keyboard hints along the bottom.
**U2. Slim HUD.** V1 0:07 mana bar thin above hotbar; V3 0:17 pack card ("AdventureCraft Add-On / Activate").
*BAT:* <=3 persistent HUD elements, rest contextual.
**U3. Tier-coloured tooltips.** V2 1:41. Name in tier colour, add-on source in sub-line, effect in grey, stats in blue.

## 10. PRESENTATION / TRAILER TRICKS
**T1. Lower-third feature banner.** V1 0:08-0:41: gold-framed banner ("NEW SPELLS", "NEW CLASSES", "FUSION SPELLS", "LEGENDARY ITEMS"), one phrase per 2-3 s shot.
*BAT:* one banner per faction/feature in the trailer.
**T2. Wide / close / wide rhythm.** V1 1:02-1:22, V4 0:36-0:58: 1-2 s shots start mid-motion; macro cut on the strike.
*BAT:* ability reveal = 1 s wide, 0.5 s eyes, 1 s strike.
**T3. Void turntable.** V2 0:06-0:14 Sculptor on black, camera orbits and zooms; V4 0:19-0:32 monochrome pass shows shapes before colour.
*BAT:* turntable on black + a grayscale silhouette pass for every unit sheet.

## Cautions
- No realistic textures in these videos; use them for motion, VFX structure, palette logic and UI, not for surface realism.
- Cartoon exaggeration (V4 Better Structures) conflicts with hyper-realism; limit to abilities.
- Many effects rely on bloom/shaders (V3 "Vibrant Visuals Compatible"); plan an in-mod bloom approximation.
