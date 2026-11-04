# What Frostpunk and Anno 1800 teach about RTS-camera buildings

Research only, 2026-10-29. Approved by lovish 2026-10-28 23:50. Text sources only (dev blogs, interviews, wikis,
reviews, artists' pages); I could not see images, so every claim below is what a developer or a reviewer wrote.
Quotes are kept short and attributed. The "Rules" and "Progression sketch" sections translate the findings to
Beyond and Tabs: block-built buildings, camera at ~45-55 degrees from the south-west, ~36 screen px per block (so one
block is a clearly legible 36 px square and one 16-px texture pixel is ~2.25 screen px: **shape at block scale always
reads, texture detail barely does**).

Context used from the repo: `design/vehicle_labs.md` (Engine Shed brief, 11 x 13 x 12 footprint), `design/building_upgrades.md`
(levels keep the footprint, grow upward, level readable to the opponent), `design/brassworks_plan.md` section 6 (the
`_brass` block palette), `design/building_detail.md` (Machine Shop / Assembly Works descriptions).

---

## 1. Readability from the RTS camera

**Anno 1800 (Ubisoft Mainz)**

- The 3D artists' stated goal: a building must blend into the city and still let the player identify its purpose
  "in the blink of an eye" (Rolf Bertz). The *basic shape* is decided in the first mock-up step and is meant to be
  the most prominent part of the concept; walls and roof are modelled first, detail later. Colours and textures are
  checked at the bird's-eye zoom "since players spend most time zoomed out", while close-up detail is kept high.
  Budget per grid tile: ~250 polygons and a 256-px texture; bigger buildings get more.
  Source: [DevBlog: Of 3D Architects and Construction Workers](https://anno-union.com/2017/11).
- From the top-down perspective "roofs and large-scale weathering effects become much more prominent" than fine
  detail such as the grout between bricks (David Shelton, Devcom talk "Texturing the Industrial Revolution"). He calls
  every building a hero asset and a character, not a prop. Factories must always show *what* they produce and *how*:
  iron melting pots outside the furnace, soap cooked and stirred under the open sky.
  Source: [Ubisoft News: Create the Unknown](https://news.ubisoft.com/en-us/article/355243/Create-The-Unknown-How-Anno-1800-Put-A-Creative-Spin-on-the-Industrial-Revolution).
- Factories are deliberately "clearly bigger in size than other buildings" (all residences stay 3 x 3), so size itself
  separates industry from housing at a glance.
  Source: [DevBlog: Residential Tiers](https://www.anno-union.com/en/devblog-residential-tiers/).
- What they left out: when designing the skyscraper "panorama effect" they tried icons on buildings and dropped them
  because the icons clumped when zoomed out; they used colour (green / brown, colourblind-safe) instead. Engineer and
  Investor houses already "have their own designs and color palette to be easily distinguishable".
  Source: [DevBlog: The Skyscrapers](https://anno-union.com/tag/anno-1800-en/page/12).
- Night: 1000+ light sources were added specifically so that every building stays identifiable after dark;
  "readability is an important factor".
  Source: [DevBlog: Day & Night](https://devtrackers.gg/anno-1800/p/f5f56fe2-devblog-day-night).
- A reviewer's read of the result: zoom in to just above roof height and you see weddings, children, drunks outside
  pubs; zoomed out, chimneys pouring soot and swaying grain fields carry the mood. Industrial machinery looks
  deliberately out of place among townhouses, so industry and housing never blur.
  Source: [PCGamesN review](https://www.pcgamesn.com/anno-1800/review).

**Frostpunk (11 bit studios)**

- The art lead says the main reference was industrial buildings "around the world", with Victorian traits only in
  some building features and window shapes. Buildings were made to bend or squeeze to fit the circular grid (custom
  engine tricks). Source: [PC Gamer interview](https://www.pcgamer.com/frostpunk-developers-on-hope-misery-and-the-ultimately-terrifying-book-of-laws).
- The concept artists' explicit problem statement: strategy-game buildings "lose a sense of scale and look like doll
  houses"; their fix was to pack each building with detail (icicles, rust smears, a small figure for scale). The shipped
  workshops ended up narrower than the concepts so they fit between neighbours.
  Sources: [Jakub Kowalczyk, Frostpunk building concepts](https://jakubkowalczyk.artstation.com/projects/VEeGb),
  [PC Gamer concept-art feature](https://www.pcgamer.com/heres-some-chilly-frostpunk-concept-art).
- What survives zoom-out in practice (player review): the generator is a "clearly steampunk edifice" whose structural
  gaps cast shadows; the workshop is the one early building that looks outlandish; shelters read as tents, then wooden
  walls with metal reinforcement; the concentric rings are only apparent when zoomed out; lanterns hang on buildings
  and are carried by people, and everything casts a shadow. Source: [GameSpot user review](https://www.gamespot.com/frostpunk/user-reviews/2200-12796874/).
- Frostpunk 2 moved the camera out: whole neighbourhoods instead of citizens, hex districts instead of buildings, and
  "small map icons" mark where each building type belongs. A reviewer notes white snow dominates and buildings are
  designed to stand out against it. Source: [Galaxus review](https://www.galaxus.ch/en/page/frostpunk-2-im-test-aufbaustrategie-mit-endzeitstimmung-34770).
  Another found the district city "more zoned and less personal" than the original's hand-placed buildings.
  Source: [VideoGamer review](https://www.videogamer.com/reviews/frostpunk-2-review).

**Takeaway.** Both studios decide the *primary mass* first, let the *roof* and the *largest weathering gradients* carry
the identity, put the *function on the outside* (what it makes, visibly), and fight the "doll house" look with scale
cues (a person, a cart, a crane) rather than with fine surface detail. Icons on buildings were tried and rejected.

---

## 2. Faction / set cohesion

**Anno 1800: one tier = one palette + one material family, every building type in that tier obeys it**

- "The right palette of color and material also defines look and feeling of a tier"; each tier is a snapshot of a
  social class. Production buildings in a tier match the residential style of that tier.
  Sources: [DevBlog: Residential Tiers](https://www.anno-union.com/en/devblog-residential-tiers/),
  [DevBlog: A farmer's life](https://anno-union.com/tag/anno-1800-en/page/30).
- Farmers: country houses of wooden boards with small individual decals, meant to feel self-built, "a personal touch";
  Workers: crowded flats in large apartment blocks, suburban energy; Artisans: exotic, eccentric touches and the first
  glass windows ("a reward for your eyes"); Engineers: "clean, not too playful", modern, merging into blocks;
  Investors: a clean, nearly marble-like look with few accents, **green rooftops that contrast with the surroundings**
  and red highlights for luxury.
  Sources: [page 30 digest](https://anno-union.com/tag/anno-1800-en/page/30), [page 29 digest](https://anno-union.com/tag/anno-1800-en/page/29),
  [DevBlog: Engineers](https://anno-union.com/?p=9848), [DevBlog: Investors / Electricity](https://www.anno-union.com/category/devblog-en/page/11/).
- Skins show how far cohesion stretches: cosmetic packs swap residences to brick, "industrial brick stone",
  colourful or lit-sign variants without changing footprint or silhouette, and the team praises that ornaments from
  different packs "fit perfectly together". Sources: [Vibrant Cities Pack](https://store.epicgames.com/p/anno-1800--vibrant-cities-pack),
  [Industrial Zone community spotlight](https://anno-union.com/category/anno-1800-en/page/8).
- The Steampunk Pack's own definition of what reads as steampunk: metallic bronze and gold, celestial blue on glass
  and ground tiles, visible moving cogs and wheels, bubbling steam, machinery that is "highly advanced, but quite
  unnecessary". Source: [DevBlog: The Steampunk Pack](https://devtrackers.gg/anno-1800/p/8216d4ca-devblog-the-steampunk-pack-cdlc).

**Frostpunk: cohesion by weather and palette**

- "Being snowy is, right next to being steampunk-ish, Frostpunk's core visual identity" (Bartosz Sobolewski). Royal
  and steel blues dominate; colours are cold and washed out; grime on buildings. The base game had **no rooftop models
  at all** because snow covered every roof; the autumn prequel had to build red roofs that stand out against green.
  Source: [PlayStation Blog: The Last Autumn](https://blog.playstation.com/?p=350772).
- New structures (docks) "had to match the game's art style and visual structure"; the radial city "grew to be
  immediately recognizable and iconic" (Przemysław Marszał).
  Sources: same PlayStation post; [Epic Games Store feature](https://store.epicgames.com/news/how-the-everlasting-winter-became-your-fiercest-enemy-in-frostpunk).
- Frostpunk 2 keeps the look by subject matter rather than snow cover: "grim retro-futuristic Victorian buildings",
  frozen surfaces and wear on every texture, flickering lights. Sources: [GamesRadar review](https://gamesradar.com/games/city-builder/frostpunk-2-review),
  [RetroStyle Games analysis](https://retrostylegames.com/blog/frostpunk-game-design/).

**Takeaway.** Cohesion is a *shared palette and a shared weathering layer*; distinctness is *mass + signature*. Anno
makes the roof colour a tier flag (Investor green); Frostpunk makes the roof a uniform snow cap and lets lights and
steam do the talking.

---

## 3. Tier / upgrade progression

**Anno 1800: same footprint, new materials, new palette, new light**

- All five Old World residence tiers are 3 x 3; the footprint never changes, so the upgrade is read entirely from
  the model, palette and material. Source: [DevBlog: Residential Tiers](https://www.anno-union.com/en/devblog-residential-tiers/).
- The upgrade *costs* are the materials you then see: Workers->Artisans add bricks and steel beams; Artisans->
  Engineers add windows (glass); Engineers->Investors add reinforced concrete.
  Sources: [Artisan Residence](https://anno1800.fandom.com/wiki/Artisan_Residence), [Engineer Residence](https://anno1800.fandom.com/wiki/Engineer_Residence).
- Skyscrapers (The High Life DLC) are the only upward growth: modules stack, Engineer towers to 3 levels, Investor to
  5, each module in several random variants, and "you cannot add bigger modules on top of smaller ones" (towers
  taper). Source: [DevBlog: The Skyscrapers](https://anno-union.com/tag/anno-1800-en/page/12).
- Light tells the tier at night: electric light is bluish, candles and fireplaces warmer and yellower, so "a heavily
  industrialized metropolis will look and feel notably different from a rural hamlet". Source: [DevBlog: Day & Night](https://devtrackers.gg/anno-1800/p/f5f56fe2-devblog-day-night).
- Infrastructure upgrades are shown on the ground and on vehicles: electric poles along streets mark the powered
  network; boosted factories swap horse carts for steam-powered vehicles; trains visibly haul oil to power plants.
  Source: [DevBlog: Electricity](https://www.anno-union.com/category/devblog-en/page/11/).
- City-level result: "as residences upgrade through tiers, the city visibly changes", wealthy districts form while the
  poor are pushed to the edges. Source: [PCGamesN review](https://www.pcgamesn.com/anno-1800/review).

**Frostpunk: upgrades are heat, and heat is drawn on the world**

- Shelter path Tent -> Bunkhouse -> House: each step is more insulation (tents only work inside the heat zone,
  bunkhouses are weather resistant). Reviewer description of the visual step: tents, then wooden walls with metal
  reinforcement. Sources: [Gamepressure buildings](https://www.gamepressure.com/frostpunk/buildings/z6a12b),
  [GameSpot user review](https://www.gamespot.com/frostpunk/user-reviews/2200-12796874/).
- Steam Hub: a smaller version of the generator tower, built on a street, four steam levels (+1 to +4 heat, 100-400 %
  coal). Heaters add +1 to +3 heat to industrial, food and medical buildings per tech level. Generator: power, range and
  efficiency upgrades plus Overdrive (+2 heat levels). Production tiers: Sawmill -> Steam Sawmill; Coal Mine -> Steam
  Coal Mine -> Advanced Coal Mine. Sources: [Steam Hub wiki](https://frostpunk.gamepedia.com/Steam_Hub?oldid=3375),
  [Gamepressure technologies](https://www.gamepressure.com/frostpunk/technologies/zea19c).
- The upgrade is *visible as weather*: snow recedes from buildings as they warm and builds up as they cool; near the
  generator snow becomes slush and mud, far out buildings are coated in ice; the thermal overlay runs purple -> blue ->
  green -> orange -> red. Sources: [GameSpot user review](https://www.gamespot.com/frostpunk/user-reviews/2200-12796874/),
  [Vice](https://www.vice.com/en/article/the-industrial-apocalypse-of-frostpunk-is-more-truth-than-fiction),
  [Gamepressure heat](https://www.gamepressure.com/frostpunk/heat/z8a12d).
- Frostpunk 2: a district doubles by adding three hexes, each expansion adds a building slot; "upgrades change
  buildings' appearance and function". Sources: [Gamepressure FP2 building](https://www.gamepressure.com/frostpunk-2/building/ze115f1),
  [Galaxus review](https://www.galaxus.ch/en/page/frostpunk-2-im-test-aufbaustrategie-mit-endzeitstimmung-34770).

**Takeaway for our 4 tiers.** Anno's ladder is *timber -> brick -> steel -> glass -> concrete* with a palette shift and
warmer-to-cooler light; height only comes at the very top and always tapers. Frostpunk's ladder is *more steam, more
heat, more light, less snow*. Neither changes the footprint.

---

## 4. Living buildings

**Anno 1800**

- Visual feedback exists because it is "faster to grasp than reading menus" and makes the world believable. A factory
  in its producing state has busy workers, running machines, smoking chimneys, moving steel kettles and sparks; idle,
  the smoke and fire are reduced or removed. Feedback actors (baker, farmhands, innkeeper, marching band, cats and
  dogs) walk spline paths; timing and position are randomised so identical buildings never animate in lockstep.
  Source: [DevBlog: Visual Feedback](https://anno-union.com/author/bb_volty/page/12).
- A stopped smelter (no smoke, still machines) is a *status indicator*: it tells the player to look for a shortage or a
  strike. Source: [GameStar on the "Wuselfaktor"](https://www.gamestar.de/artikel/anno-1800-so-kommt-der-wuselfaktor-ins-spiel,3325506.html).
- Smoke colour carries pollution: small producers emit a little white smoke, industrial districts black clouds;
  "fumes from your nearby industry or city ruins will be an important indicator".
  Source: [DevBlog: Beauty Building / Islands and decorations](https://anno-union.com/tag/anno-1800-en/page/29).
- Night life changes: fewer residents on the streets, children removed from the schoolyard, more musicians in the pub.
  Source: [DevBlog: Day & Night](https://devtrackers.gg/anno-1800/p/f5f56fe2-devblog-day-night).
- Carts and warehouse queues are visible in the world. Source: [DevBlog: Pushing carts (page digest)](https://anno-union.com/author/bb_volty/page/12).
- Steampunk ornaments are defined by moving cogs and wheels and bubbling steam. Source: [Steampunk Pack](https://devtrackers.gg/anno-1800/p/8216d4ca-devblog-the-steampunk-pack-cdlc).

**Frostpunk**

- Snow piles on roofs and streets; the paths citizens walk to the sawmills and mines are visibly trodden; tiny fires
  burn among the tents; windows flicker with candlelight; chimney smoke drifts with the wind; in a cold snap the screen
  edges freeze. Source: [Kotaku review](https://kotaku.com/new-city-building-game-frostpunk-is-great-for-those-of-1825499161).
- Snow parts as people walk and refills behind them; automatons leave holes; streets stay clear because steam pipes
  melt them; lanterns on buildings and in hands, every light casts a shadow.
  Source: [GameSpot user review](https://www.gamespot.com/frostpunk/user-reviews/2200-12796874/).
- The generator's first start-up is an event: "Steam billows into the sky"; glowing orange towers stand against dark
  frozen streets. Source: [PowerUp Gaming review](https://powerup-gaming.com/?p=26447).
- Frostpunk 2: flickering lights, snow gathering on rooftops, chimney smoke; pollution visibly drifts from industrial
  onto housing districts; during a whiteout outside links go dark and only emergency lights remain.
  Sources: [RetroStyle Games](https://retrostylegames.com/blog/frostpunk-game-design/), [VideoGamer](https://www.videogamer.com/reviews/frostpunk-2-review),
  [Neowin](https://www.neowin.net/reviews/frostpunk-2-review-a-grim-masterpiece-set-at-the-end-of-the-world/).

**Takeaway.** Animation is *state*, not decoration: smoke on = producing, smoke off = problem; light on = alive and
warm; snow/soot gradients = how long and how hot. Randomised timing stops a row of identical labs looking mechanical.

---

## 5. Materials and weathering at RTS distance

- Anno's material set: "real materials like bricks, plaster, worn metals, and roof shingles"; factory smoke turned the
  red brick grey and black over time (both as period research and as the look). Fine grout is invisible from the
  camera; roof materials and *large-scale* weathering are what you see. Procedural materials (Substance) let them paint
  dozens of materials per building. Source: [Ubisoft News](https://news.ubisoft.com/en-us/article/355243/Create-The-Unknown-How-Anno-1800-Put-A-Creative-Spin-on-the-Industrial-Revolution).
- Research points called out for 1800: burned brick colour, period roof colours, how shiny and how worn each metal is;
  cracks and bumps live in the normal map. Source: [DevBlog: Of 3D Architects](https://anno-union.com/2017/11).
- Investor roofs are green (verdigris / copper) specifically to *contrast*; red accents mark luxury.
  Source: [DevBlog: Investors](https://www.anno-union.com/category/devblog-en/page/11/).
- Frostpunk: washed-out steel blues, grime, ice shards in the air, frozen screen edges; textures show frozen surfaces
  and gradual wear; concept detail = icicles and rust smears. Value contrast is the whole image: warm lit windows and
  orange glowing towers against blue-white snow and dark streets. Sources: [PlayStation Blog](https://blog.playstation.com/?p=350772),
  [RetroStyle Games](https://retrostylegames.com/blog/frostpunk-game-design/), [PC Gamer concept art](https://www.pcgamer.com/heres-some-chilly-frostpunk-concept-art),
  [PowerUp Gaming](https://powerup-gaming.com/?p=26447), [Kotaku](https://kotaku.com/new-city-building-game-frostpunk-is-great-for-those-of-1825499161).
- Frostpunk's thermal overlay (purple -> red) is a *second* value map the player can toggle; the world itself encodes
  the same information with slush vs ice. Source: [Gamepressure heat](https://www.gamepressure.com/frostpunk/heat/z8a12d), [Vice](https://www.vice.com/en/article/the-industrial-apocalypse-of-frostpunk-is-more-truth-than-fiction).

**Takeaway.** At distance a material is a *value and a hue*, and weathering is a *gradient across the whole facade or
roof* (soot climbing a stack, snow on top faces, verdigris on copper), never a per-tile detail.

---

## 6. The ground and layout

**Frostpunk 1: a city that is one diagram**

- A crater with the generator at the centre; streets in circles; buildings expand outward in concentric rings (22 build
  rings, most buildings span several; a tent spans two); heat zones are radial; steam hubs sit *on* streets and make
  satellite warm zones; buildings bend to fit the arcs. The circular layout was an early visual idea and became the
  game's identity. Sources: [Xbox Wire: radial design](https://news.xbox.com/en-us/?p=125332), [TheGamer layout guide](https://www.thegamer.com/frostpunk-city-layout-guide/),
  [TechRaptor](https://techraptor.net/content/how-11-bit-studios-created-society-survival-simulator-frostpunk), [PC Gamer interview](https://www.pcgamer.com/frostpunk-developers-on-hope-misery-and-the-ultimately-terrifying-book-of-laws).
- Players lay roads first "to mark out the city's shape"; good layouts are compact wards or clusters growing outward so
  heater and hub needs are legible; "holes of unbuilt area" are the common failure. Source: [TheGamer](https://www.thegamer.com/frostpunk-city-layout-guide/).
- Coal thumpers pound coal piles out of the ground around them; automatons tower over most buildings and plug into
  the generator to recharge: the ground props *are* the economy. Source: [TV Tropes](https://tvtropes.org/pmwiki/pmwiki.php/Videogame/Frostpunk).

**Frostpunk 2: districts, adjacency, drift**

- Hex tiles; a district is 6 hexes, expands by 3; hubs are 1 hex; adjacency (3 shared tiles) lowers heat demand;
  housing next to industry takes pollution; frostbreakers clear 8 hexes at a time; a district overlay shows zones.
  Snake-shaped district chains are the efficient layout. Sources: [Gamepressure FP2 building](https://www.gamepressure.com/frostpunk-2/building/ze115f1),
  [Aftermath impressions](https://aftermath.site/frostpunk-2-impressions/), [Neowin](https://www.neowin.net/reviews/frostpunk-2-review-a-grim-masterpiece-set-at-the-end-of-the-world/).

**Anno 1800: districts around public buildings, order grows with tier**

- Cities are "often built around a center of public buildings"; early settlements look natural and unstructured, higher
  tiers bring order. Source: [DevBlog: Residential Tiers](https://www.anno-union.com/en/devblog-residential-tiers/).
- Small ornaments and decals fill gaps between production buildings; ornament-to-population ratio matters.
  Source: [page 29 digest](https://anno-union.com/tag/anno-1800-en/page/29).
- Electric poles on streets, rails from oil harbour to power plant, carts and queues at warehouses: logistics drawn on
  the ground. Source: [DevBlog: Electricity](https://www.anno-union.com/category/devblog-en/page/11/).
- Dev commentary on the best community industrial zones: clear zoning (one function per district), ornaments placed to
  connect the layout, canals to frame a building, sunset lighting, busy background details (stalls, construction).
  Source: [Industrial Zone community spotlight](https://anno-union.com/category/anno-1800-en/page/8).
- Player-made plazas and tree-lined roads "look far better than the default grid". Source: [PCGamesN](https://www.pcgamesn.com/anno-1800/review).

**Takeaway.** A base reads as a city when roads are the skeleton, each district has one function and one focal
building, and the logistics (rails, poles, carts, coal piles) are drawn on the ground between buildings.

---

## Rules for Beyond and Tabs buildings

Each rule is testable on our existing contact-sheet tooling (`tools/` structure contact sheets, SW camera, 36 px/block)
and tied to the finding that motivates it.

1. **Primary mass first.** Block every building as at most two primitives (e.g. long gable + tower) before any
   detail; the 2-primitive silhouette must differ from every other building of the same faction and tier when both are
   shown as 25 %-scale grey thumbnails. (Bertz: basic shape is the most prominent part; Frostpunk buildings bend to the
   grid, their mass still reads.)
2. **Roof carries the identity.** On the SW contact sheet the roof covers 40-80 % of the building's pixels, so the
   roof must contain (a) the faction roof material, (b) one roof-shape feature (vent strip, clerestory, dome, sawtooth)
   and (c) one roof-mounted signature. A building whose roof is a plain slab fails review. (Shelton: roofs and large
   weathering dominate top-down; Frostpunk had no roofs because snow was the roof.)
3. **One signature element >= 3 blocks, unique in the faction set, on the camera side.** Chimney stack, gantry crane,
   clock face, dome, flywheel. Test: cover everything but the signature on the thumbnail; a tester names the building.
   (Anno: factories must show what and how; Frostpunk: the workshop is the one building that "looks outlandish".)
4. **Show the product in the yard.** Every production building has its output or raw input visible outside within the
   footprint: rails + a part-built chassis for the Engine Shed, coal stage, ingot stacks, crates. Minimum 2 x 2 blocks of
   product props. (Anno: melting pots outside the furnace, soap under the open sky; Frostpunk: coal piles round the thumper.)
5. **Tier = one new material + one palette shift, footprint fixed.** T1 brick + slate + timber; T2 adds riveted iron
   (deepslate tiles / chains / iron bars); T3 adds glass and green copper (waxed oxidised cut copper); T4 adds polished
   stone / white trim (quartz or polished diorite bands) and more copper. No tier introduces two materials. (Anno:
   timber -> brick -> steel -> glass -> concrete; "palette of color and material defines a tier".)
6. **Grow upward and taper.** Height steps per tier within the fixed 11 x 13 footprint: T1 <= 9 blocks, T2 <= 12,
   T3 <= 15, T4 <= 19, and every tier's added mass sits on or inside the tier below (never a wider module on a narrower
   one). (Anno skyscrapers: no bigger module on a smaller one; `building_upgrades.md` already fixes footprints.)
7. **Light colour tells the tier.** T1-T2 windows and lamps warm (torches, lanterns, shroomlight orange); T3-T4 add cool
   white (sea lanterns / end rods) for "electric". At night the camera must still name the building from its lights
   alone. (Anno Day & Night: bluish electric vs yellow candle; 1000 lights so every building stays identifiable.)
8. **Smoke is state.** Particles only while producing or upgrading; idle emits none; the Heat mechanic drives colour
   and density (light grey normal, black and heavy when venting). Rule holds for every faction's labs. (Anno Visual
   Feedback: idle removes smoke; GameStar: a stopped smelter is a status indicator; Beauty Building: white vs black.)
9. **Value ladder.** Roof dark (slate / deepslate), walls mid (brick), accents light-and-saturated (brass, copper green,
   lit glass). Test: a desaturated thumbnail must show three distinct value steps; if roof and wall merge, swap one block.
   (Frostpunk: lit windows and orange towers against blue snow; Anno Investors: green roofs for contrast.)
10. **Weathering is a gradient, not a tile.** Soot climbs 2-4 blocks up every stack and under eaves (blackstone ->
    deepslate -> brick), rust/verdigris streaks run 2+ blocks under metal, snow caps only top faces in cold biomes via
    `BiomePalette`. No single-block "dirty" tiles. (Shelton: grout invisible, large-scale weathering prominent; Frostpunk:
    snow recedes with heat.)
11. **No sub-block detail on faces the camera cannot see, no icons on buildings.** Detail budget goes to the south and
    west faces and the roof; the north and east faces get palette only. Level is shown by shape plus the strategic-zoom
    pips, never by a sign on the model. (Bertz: check at bird's-eye; Anno dropped building icons because they clumped.)
12. **One moving part from T2 up.** Each lab T2+ has at least one animated element >= 2 blocks (gantry crane trolley,
    flywheel, beam engine, turntable, water-wheel). (Anno: moving steel kettles; Steampunk Pack: visible cogs and wheels.)
13. **Scale cue in every frame.** At least one human-scale prop (1-block lamp post, 1 x 2 door, cart, bench) sits on the
    camera side so the building does not read as a doll house. (11 bit's stated fix for strategy buildings.)
14. **Faction set test.** All Brassworks buildings on one sheet share >= 3 of: red brick walls, grey slate roof, green
    copper spire or trim, brass band, smokestack; and no two buildings share the same roof-shape + signature pair.
    (Anno: tier palette shared across types; Frostpunk: snow + steam shared, generator / workshop / hub distinct.)
15. **Ground tells the base.** Roads are the skeleton; rails, pipes or poles link labs to the capitol; yards are inside
    the footprint, never spilling onto walkable paths. A base screenshot with buildings hidden must still show where
    industry, housing and the core are. (Frostpunk: roads first, hubs on streets; Anno: poles, rails, clear zoning.)

---

## Brassworks T1 -> T4 progression sketch: the Engine Shed (vehicle lab)

> **Reconciled with lovish's decisions (Itachi 2, Oct 29):** the study's "footprint fixed" (Anno's rule) does NOT apply
> here - lovish decided (Oct 28) buildings grow bigger AND more intricate per tier, T1 production buildings get an
> in-place upgrade that grows their footprint (blocked if there is no room), 4 models per building, T4 capitol 59 x 59.
> The T1 Engine Shed plot is 17 x 25, h <= 26 (not 11 x 13 x 12). Keep the per-tier "one new material + one light step
> + one signature growth" logic; scale the footprints by design/building_tiers.md.

Footprint fixed at 11 x 13 x 12 (`vehicle_labs.md`); the arched 5 x 8 door faces the camera; rails run through the bay;
the smoke-vent strip over the bay stays open so the spawn column is clear. Each tier keeps everything below it and
adds one material, one height step, one signature growth and one light change (rules 5-7, 12).

| | T1 Engine Shed | T2 | T3 | T4 |
|---|---|---|---|---|
| Mass | one long brick gable shed, slate roof, open vent strip | + a riveted iron water tank on a deepslate frame at the back | + a glazed copper clerestory along the ridge (green copper + orange glass) | + a square engine tower at the back corner with a green copper pyramid roof and lightning rod |
| Height (max) | 9 | 12 | 15 | 19 |
| New material | red brick, grey slate (deepslate tiles), spruce/oak timber trusses | riveted iron: iron bars, chains, deepslate-tile bands, anvils | glass (orange stained + plain) and oxidised cut copper | polished quartz/diorite trim bands, waxed copper everywhere brass was |
| Signature | one tall brick stack with a brass band, 3 blocks proud of the ridge | travelling gantry crane across the bay: chain + hook over the door | second stack; steam valves on the camera eave; a round brass gauge/clock face on the gable | beam engine visible through the open tower side: flywheel + rocking beam (animated), twin stacks with furnace-orange bands |
| Yard (product) | rails out of the door, a part-built Mortar Crawler chassis on them, coal stage, 2 crates | coal stage grows, a cart on the rails, a second rail siding | an oil/water pipe run from tank to shed, lamp posts | a brass boiler drum alongside, pressure-release vent that puffs when Heat vents |
| Light | warm: lanterns at the door, 2 orange windows | + lanterns along the eave | + white sea-lantern floodlights under the clerestory, glass glows at night | + white lights in the tower, furnace glow (shroomlight) under the roof vent |
| Smoke / motion | light grey from the stack while producing | gantry trolley slides, chain sways | twin stacks, valve hiss particles | flywheel turns; black smoke when venting Heat |
| Weathering | soot 2 blocks up the stack, under the eave | rust streaks under the tank | verdigris streaks under the copper ridge | all of the above plus soot 4 blocks up both stacks |

Reads vs the Machine Shop (rule 14): the Shop is an L round a yard with a turntable and sawtooth roof; the Shed is one
straight ridge with a clerestory and a corner tower, no L, no turntable. Reads vs the Foundry Hall capitol: the Hall
has the brass dome; the Shed never gets a dome.

---

## Screenshots to ask lovish for

Text cannot tell us pixel values. These shots, each at the game's default camera unless stated, would let us measure
roof share, value steps and what survives zoom-out:

1. **Anno 1800, one 3 x 3 plot through all five tiers** (Farmer, Worker, Artisan, Engineer, Investor) side by side,
   default zoom, daytime; then the **same five at midnight** (light colour per tier).
2. **Anno 1800, Furnace + Steelworks + Brick Factory block**, default zoom and at maximum zoom-out, producing and then
   idle (smoke/fire on vs off).
3. **Anno 1800, a mixed district looked at from as high as the camera allows** (roof colours and value per tier).
4. **Anno 1800, an Engineer skyscraper at 1 and 3 modules and an Investor at 1, 3 and 5**, same angle (taper and module
   growth).
5. **Frostpunk 1, the whole city zoomed fully out at night** (ring, lights, generator glow) and **the generator at
   closest zoom with a steam hub beside it**.
6. **Frostpunk 1, Tent / Bunkhouse / House side by side; Steam Hub at level 1 and level 4; Coal Mine / Steam Coal Mine /
   Advanced Coal Mine** (what an upgrade changes).
7. **Frostpunk 1, a Workshop and a Factory at default zoom** (the "outlandish" building and the icicle/rust detail).
8. **Frostpunk: The Last Autumn, any building with its red roof** next to the same building snow-covered in the base game.
9. **Frostpunk 2, one Industrial district at default zoom and at maximum zoom-out**, with a Housing district beside it
   during pollution drift; and the same area during a whiteout (emergency lights only).

---

## Sources read (all above) and sources blocked

Blocked by the fetch permission prompt (not read; worth a manual look): `frostpunk.fandom.com/wiki/Bunkhouse`,
`frostpunk.fandom.com/wiki/Generator`, `frostpunk.gamepedia.com/House`, `frostpunk.gamepedia.com/Coal_Thumper`,
`anno1800.fandom.com/wiki/Farmer_Residence`, `anno-union.com/tag/anno-1800-en/page/28`,
`gamepressure.com/.../frostpunk-2-review-an-example-that-risk-pays-off`, the Epic Games Frostpunk 2 interview, the
GameStar "Dächer der Stadt" test. The Frostpunk and Frostpunk 2 digital artbooks exist (Steam/Epic) but are images, so
nothing from them is cited here.
