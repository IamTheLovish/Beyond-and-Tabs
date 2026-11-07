#!/usr/bin/env python3
"""Team-coloured light (the Light Grid, design/ai_faction.md, PR 379: "glossy black bodies, thin glowing light lines
on every edge" in "one or two team light colours").

A palette colour that is both `emissive` and on `--team` is a TEAM LIGHT: tools/dsl_to_game.py paints it into a
`team_glow` image (the body's <texture>_team_glow.png, drawn full-bright and multiplied by the owner's colour in game,
GeneratedBodies.teamGlow), and NOT into the plain `glow` image (it would be drawn white over the team colour) nor into
the team cloth mask (light is not a dyed cloth). A body with no such colour gets no team_glow image, so every body
exported before this change exports exactly as it did.

    python3 -m unittest tools/test_team_glow.py
"""
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import numpy as np  # noqa: E402

import dsl_to_game  # noqa: E402
import render_concept  # noqa: E402

MODEL = {
    "name": "Team light probe", "faction": "test", "role": "probe",
    "palette": {"carbon": "#16181c", "tabard": "#3a6fd1", "line_glow": "#d8f4ff", "lens_glow": "#ffffff"},
    "emissive": ["line_glow", "lens_glow"],
    "bones": [
        {"name": "body", "pivot": [0, 8, 0], "cubes": [
            {"from": [-4, 4, -4], "size": [8, 6, 8], "color": "carbon"},
            {"from": [-4, 10, -4], "size": [8, 1, 8], "color": "tabard"},
            {"from": [-4.5, 6, -4.5], "size": [9, 1, 9], "color": "line_glow"},
            {"from": [-1, 6, -5], "size": [2, 2, 1], "color": "lens_glow"},
        ]},
    ],
}


def build(model, team):
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "probe.json")
        with open(path, "w") as fh:
            json.dump(model, fh)
        m = render_concept.load_model(path)
        # the generated Java names its source relative to the repo (a temp dir may be on another drive)
        m["_path"] = os.path.join(dsl_to_game.ROOT, "design", "models", "probe.json")
        return dsl_to_game.build(m, "ProbeModel", team, "probe_unit_layer", 4)


def lit(img):
    a = np.asarray(img.convert("RGBA")).astype(int)
    return (a[..., 3] > 0) & (a[..., :3].max(axis=2) > 8)


class TeamGlow(unittest.TestCase):

    def test_a_team_light_goes_to_its_own_image(self):
        _java, imgs, _info = build(MODEL, ["tabard", "line_glow"])
        self.assertIn("team_glow", imgs, "a colour both emissive and on --team must make a team_glow image")
        tg, glow, team = lit(imgs["team_glow"]), lit(imgs["glow"]), lit(imgs["team"])
        self.assertGreater(int(tg.sum()), 0, "the team light's texels are painted into team_glow")
        self.assertFalse(bool((tg & glow).any()), "a team light is never also painted into the plain glow")
        self.assertFalse(bool((tg & team).any()), "a team light is light, not team cloth: not in the team mask")
        self.assertGreater(int(glow.sum()), 0, "the white lens stays in the plain glow")
        self.assertGreater(int(team.sum()), 0, "the team cloth stays in the team mask")

    def test_the_team_light_is_bright_and_grey_so_the_owner_colour_tints_it_cleanly(self):
        _java, imgs, _info = build(MODEL, ["tabard", "line_glow"])
        a = np.asarray(imgs["team_glow"].convert("RGBA")).astype(int)
        px = a[lit(imgs["team_glow"])][:, :3]
        self.assertGreaterEqual(int(px.min()), 150, "a team light is drawn bright (the owner's colour x ~1)")
        self.assertLessEqual(int((px.max(axis=1) - px.min(axis=1)).max()), 6, "and grey, so it takes the owner's hue")

    def test_a_body_without_a_team_light_has_no_team_glow(self):
        _java, imgs, _info = build(MODEL, ["tabard"])
        self.assertTrue("team_glow" not in imgs or not lit(imgs["team_glow"]).any(),
                        "no team light colour: nothing in team_glow")
        self.assertGreater(int(lit(imgs["glow"]).sum()), 0, "line_glow alone is a plain glow then")


if __name__ == "__main__":
    unittest.main()
