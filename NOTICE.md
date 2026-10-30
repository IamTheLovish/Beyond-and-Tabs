# Licensing

Beyond and Tabs is free software: you can redistribute it and/or modify it under the terms of the GNU General
Public License as published by the Free Software Foundation, either version 2 of the License, or (at your option)
any later version. See `LICENSE`.

## Third-party code

Parts of the game logic may be ported from [Beyond All Reason](https://github.com/beyond-all-reason/beyond-all-reason)
(GPL v2). Ported code keeps its original authors' copyright and licence; each ported file says what it was
ported from.

The box-selection rules in `bar/src/main/java/com/solegendary/reignofnether/unit/SmartSelect.java` are Smart Select
rules after aegis (public domain): a port of the rules of Beyond All Reason's `luaui/Widgets/unit_smart_select.lua`.

## Assets

No Beyond All Reason models, textures, animations, sounds, icons or unit pictures are used (those are under
licences that forbid derivative works). All unit skins, building plans and effects in this mod are original.
Totally Accurate Battle Simulator is an inspiration only; no TABS assets are included.

## Announcer voice

The spoken alerts in `bar/src/main/resources/assets/reignofnether/sounds/announcer/` are synthetic speech generated
by [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) (hexgrad), voice `af_heart`, released under the
**Apache License 2.0** (model card `license: apache-2.0`; the generation workflow re-checks this before every run and
refuses to synthesise if it changes). The model card states it was trained only on permissive/non-copyrighted audio
(public domain, Apache/MIT-licensed and synthetic audio), and Apache-2.0 places no restriction on distributing the
generated audio, commercially or otherwise. The voice is one of Kokoro's stock voices; it is not modelled on, cloned
from or meant to imitate any real person or any other game's narrator, and no third-party game audio was used.
Post-processing (pitch, filtering, chorus, reverb, chime) is done with ffmpeg in `.github/workflows/voice.yml`; the
two-tone chime is generated from sine waves. The lines themselves are in `tools/voice_lines.txt`.

## Lobby UI sounds

The clicks, chirps, buzz, chime and faction stingers in `bar/src/main/resources/assets/reignofnether/sounds/ui/` are
**original, procedurally generated** audio, synthesised from sine waves, a few odd harmonics, seeded noise, envelopes
and simple filters by `tools/gen_ui_sounds.py` (numpy + ffmpeg/libvorbis; re-run it to reproduce them). They were made
to evoke the general feel of a late-90s RTS lobby, but no audio from Total Annihilation or any other game or library
was sampled, traced, resynthesised or used as a reference recording; the script reads no input files. They are part
of this project and distributed under its licence.

## Title and loading screen art

The rose window, arch, filigree, candlelight and background in `bar/src/main/resources/assets/reignofnether/textures/gui/splash/`
are **original, procedurally generated** images drawn by `tools/gen_splash_art.py` (numpy + Pillow; no input images,
nothing copied or traced). The lettering is set in two fonts under the **SIL Open Font License 1.1**: Grenze Gotisch
(The Grenze Gotisch Project Authors) and UnifrakturMaguntia (j. 'mach' wust, Peter Wiegel). The font files and their
licences are in `tools/fonts/` and are used only by the generator; they are not packaged in the mod. The OFL places no
restriction on images made with the fonts. Details: `design/splash.md`.

The lobby backdrop in `bar/src/main/resources/assets/reignofnether/textures/gui/lobby/` is likewise **original and
procedurally generated** by `tools/gen_lobby_art.py` (no input images; mood references were only looked at). The gothic
button and heading lettering uses two fonts shipped in the mod under the **SIL Open Font License 1.1**, with their
licences beside them in `bar/src/main/resources/assets/reignofnether/font/`: UnifrakturCook Bold (j. 'mach' wust,
Reserved Font Name UnifrakturCook; Peter Wiegel; unmodified) and Cinzel (The Cinzel Project Authors; a static weight-900
instance of the variable font). Details: `design/lobby-backdrop.md`.

## Block textures: photo scans (CC0)

Most of the realistic block pack (`resourcepacks/BeyondAndTabs-Realistic`, built by `tools/gen_blocks.py`) is
original and procedurally generated. Since Oct 22 2026 (lovish's decision), **stone, dirt, the grass block** (top,
side and side overlay) **and basalt**, with their variants, are built from photo-scanned materials by **ambientCG**
(https://ambientcg.com), released under **CC0 1.0 Universal** (public domain dedication; ambientcg.com/license):

| ambientCG asset | becomes |
|---|---|
| Rock058 | stone |
| Ground106 | dirt, and the soil of the grass block's side |
| Grass004 | the grass block's top, and the blade tones of its side's turf |
| Rock035 | basalt (side and end) |

CC0 asks for no attribution; we credit the source anyway. `tools/scan_prep.py` makes 512 px copies of the colour,
normal and displacement maps (downloaded Oct 22 2026, 1K JPG sets) in `tools/scans/`; `tools/gen_blocks.py` fits
their colour, flattens their large patches and cuts them into the pack's textures and LabPBR maps. No texture from
another game, mod or resource pack is used.

## Reign of Nether

`bar/` is a fork of [Reign of Nether](https://github.com/SoLegendary/reignofnether) by SoLegendary and contributors,
licensed under the GNU GPL v3. The fork (and therefore the combined mod built from it) is distributed under GPL v3.
The Essential partner-mod integration bundled with upstream has been removed. Upstream sound files whose origin is not
documented are kept from upstream as distributed there; they will be replaced before any public release.

## Alex's Mobs and Citadel (external dependencies)

`bar/` depends on [Alex's Mobs](https://modrinth.com/mod/alexs-mobs) by sbom_xela (GNU GPL v3), which in turn runs on
its library [Citadel](https://modrinth.com/mod/citadel) by the same author (GNU LGPL v3). Both are compiled against and
required at runtime (tested with Alex's Mobs 1.22.9 and Citadel 2.6.3 for Forge 1.20.1), but neither is bundled in or
redistributed with this mod: players install them separately. Their code, models, textures and sounds stay under their
authors' copyright and licences.

The Stormcallers' Paper Crane is drawn on Alex's Mobs' shoebill model. Its skin
(`textures/entities/paper_crane_unit.png`, `tools/gen_storm_skins.py`) is our own paint: the script reads only which
pixels of the shoebill texture are part of its UV layout (the alpha outline), so the paint lands on the model, and
fills them with its own paper colours, creases and seal marks. No Alex's Mobs pixel colour is copied.

## Our shader pack (`shaderpacks/BeyondAndTabs`)

Our shader pack for Oculus (not shipped to testers yet; it is meant to replace the old third-party one) is our own: every file is original work, written clean-room from the
public documentation of the OptiFine / Iris shader-pack format, general graphics knowledge and this repository's own
code (its grade and edge smoothing are ported from our `bat_postfx.fsh`). It is not derived from any other shader pack,
and no other pack's source, settings or shader files were read to write it (design/own_shader_pack.md). It is released
under this repository's licence, GNU GPL v2 or later (a copy is in the pack folder, so the built zip carries it).
Oculus (LGPL v3), which loads it, is installed from its official Modrinth file, unmodified.
