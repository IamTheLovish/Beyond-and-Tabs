# Effects / animation study for Beyond and Tabs

## Read this first
- No third-party code could be cloned or read. This session's egress allows GitHub only for the user's own four repos. git clone, raw.githubusercontent.com, the REST API and add_repo all returned 403 "not enabled" for every third-party repo. I did not try to route around that. /home/claude/study holds only this report.
- So there are no file:line refs into other mods. The techniques below come from general knowledge, are unverified against code, and are written as reimplementation plans.
- Licences came from Modrinth sidebars (WebFetch) and one CurseForge page. I did not read any LICENSE file, so confirm before porting anything.
- Our own repo was fully inspected. Refs below point into bar/src/main/java/com/solegendary/reignofnether/.
- Licence mismatch to settle: /home/claude/beyond-and-tabs/LICENSE is the GPL v2 text, NOTICE.md says "v2 or later", and the brief says GPL-3.0. LGPL-3.0-only code (Lodestone, Veil) can only be ported if the project is GPL-3 or v2-or-later.

## Licence table
| Project | Licence (source) | Verdict |
|---|---|---|
| Mowzie's Mobs | Custom "Mowzies Mobs License" (CurseForge): all rights reserved unless stated, credit required, no competing gameplay, "faithful" changes only | NOT ALLOWED. Skip code. |
| Iron's Spells 'n Spellbooks | ARR (Modrinth) | NOT ALLOWED. Skip. |
| Naturalist | Custom (LICENSE on 1.19 branch, unread) | Unknown. Skip. |
| Friends & Foes | CC BY-NC-ND 4.0 | NOT ALLOWED (NC and ND clash with GPL). |
| Photon (Low-Drag-MC) | Custom (unread) | Unknown. Skip. |
| Tinkers' Construct | MIT | OK to study and port with credit. Could not clone. |
| Lodestone (LodestarMC) | LGPL-3.0-only | OK if we are GPL-3 or v2-or-later. Could not clone. |
| Veil (FoundryMC) | LGPL-3.0-only | Same as Lodestone. Could not clone. |
| AAA Particles (ChloePrime) | MIT | OK. Could not clone. |
| GeckoLib (bonus) | MIT | OK. Could not clone. |
| Effekseer bindings | not checked | Unknown. |

Clone these three first when access exists: Lodestone, AAA Particles, GeckoLib.

## What we already have (so we do not rebuild it)
- barfx is a mature own-pipeline VFX layer:
  - Events are batched per tick into one packet (BarFx.java).
  - The client has a pooled particle list with a hard cap, `busy` throttles and screen/fog culling (BarFxClient.java:75, 199, 965-1045).
  - Three batched render types: solid, translucent, additive (BarFxRenderTypes.java:20-36).
  - Camera shake is a damped sine (BarFxClient.java:843, 869).
  - DomeFx draws rim, shimmer and ripples as geometry.
- Animation is data-driven and strong. HumanBase has monotone cubic Hermite clips, and Gait and Life are pure functions for gait, breath, chains and bumps. Walk and idle quality is already high.
- The gaps are weight, effect textures and a cast model. Details follow.

## VFX
1. Textured soft sprites. Every effect is untextured POSITION_COLOR quads (BarFxRenderTypes.java). Add a textured additive and a textured translucent render type with our own generated atlas (glow, ring, spark, smoke, rune). Reimplement. Biggest quality jump per line of code.
2. Ribbon trails. Today `trails()` (BarFxClient.java:925) drops separate beams and smoke every 1/30 s. Keep a per-projectile ring buffer of recent positions and draw one camera-facing strip with taper, fade and UV scroll. This is the standard Lodestone/Photon idea, so reimplement from our own `streak()` (BarFxClient.java:1117).
3. Cast circle. Layer 2-3 counter-rotating textured rings on the ground (extend `flatRing`, BarFxClient.java:1133) with a rune texture, a rise-up phase and a burst on release. Drive it from a new BarFx event. Reimplement.
4. Shake model. Change the single `shakeAmt` into trauma, with shake = trauma squared, noise-driven offset, a directional impulse for slams and an optional FOV punch. Keep the setting in `shakeSetting()` (BarFxClient.java:860). Reimplement.
5. Shader packs. We draw at AFTER_TRANSLUCENT_BLOCKS (BarFxClientEvents.java:22) with the vanilla POSITION_COLOR shader. The textured types should use vanilla shader names (particle, position_color_tex) so Iris can map them. Test in-game under Iris and Oculus. This is unverified and nobody has tested it yet.

## Animation
1. Hit-frame event track and hit-stop. Clips have only pose keys and no events (HumanBase.Clip, HumanBase.java:48). Add (time, event) pairs per clip that fire damage, shake, a flash and a sound on the strike key. Add a 2-4 tick client hit-stop that freezes attacker and victim poses on heavy hits. Reimplement. This is the largest source of "weight" and it is missing.
2. Phased abilities. Give each attack a startup, active and recovery phase table. Server damage lands on the active frame and the client clip uses the same table, so the wind-up is readable and the hit is in sync. Extend tools/anim_lab.py output.
3. Per-key easing. Hermite only has overshoot-free tangents. Add a per-key flag for anticipation (back-out ease), hold and snap-hit. GeckoLib (MIT) has this feature set. Reimplement.
4. Follow-through springs. `Life.chain` is a phase-lag sine (Life.java). Add a critically damped spring driven by the body's velocity for tails, cloaks and weapon heads, so they overlap the motion.
5. Lunge and landing. Add a displacement curve applied to the entity during an attack clip (the lunge). Hook `Gait.impact` (Gait.java) to `BarFx.shake` and a dust puff for heavy units, so big feet shake the ground.

## Projectiles
1. Homing. `AbstractMagicProjectile.tick` normalises velocity each tick. Add a turn-rate-clamped steer toward a target. Server-authoritative; vanilla position interpolation is enough, so no client prediction is needed. Reimplement.
2. Route impacts through BarFx. Magic and molten-bomb impacts still use `ParticleUtil.addParticleExplosion` or vanilla particles (AbstractMagicProjectile.onHitEntity, MoltenBombProjectile.detonate). Send `BarFx.hit(K_MAGIC, ...)` instead so they get faction tint, budgeting and shake rules.
3. Glow renderer. `MagicProjectileRenderer.render` draws a shulker-bullet mesh with a 0.15 alpha shell. Replace it with a fullbright additive billboard core, a halo and the ribbon trail (VFX item 2). Reimplement.
4. Trails by kind. The `trails()` switch can pick a ribbon style per `BarFx.K_*`. Faction tint already flows in.

## Systems
1. VFX preset registry. A JSON id (for example "frost_nova") maps to a list of layers (ring, flash, sparks, shake, sound). Abilities reference the id. This gives artists tuning without Java edits. Reimplement.
2. Cast model on Ability: cast time, phases, cost, schools and tags. Ability.java:~60 `inferAutoRule` guesses the rule from class-name text ("Heal", "Summon"). Replace that with explicit tags/schools, which also enables school synergies. design/abilities.json already exists as a base.
3. Part-based kits. hero/HeroKit.java could take a Tinkers-style model (parts with material stats and trait hooks, all JSON). Tinkers' Construct is MIT, but the design is tied to its registries, so learn the shape and reimplement.

## Build order (biggest visible jump first)
1. Hit-frame events, hit-stop and the trauma shake. Small code, large feel.
2. Textured sprite atlas and render types, plus ribbon trails. Magic and fire projectiles look transformed at once.
3. Projectile impacts through BarFx, plus the glow renderer.
4. Cast-circle sigil with the phased ability model.
5. Per-key easing, spring follow-through and lunge root motion.
6. VFX preset registry and the Ability tags. Iris/Oculus test pass alongside step 2.
7. Re-run this study with real clones once GitHub access for third-party repos is granted (Lodestone, AAA Particles, GeckoLib first).
