#!/usr/bin/env python3
"""tools/ladder_matrix.py's tests: the bot ladder's tiers come from `enum Difficulty` in BotPlayer.java.

The nightly bot ladder failed four days running (Nov 2-5 2026): the enum had grown a trailing comment, a `;` and two
methods (`label()`, `easyLike()`), and the plan step read everything up to the first `}` as tier names, so its jobs were
named `passive; // the order is the lobby cycle ...` and `public string label() { return name().charat(0) + ...`.
These tests feed the real enum file and want the exact ladder.
"""
import itertools
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ladder_matrix as L  # noqa: E402

LADDER_TIERS = ["easy", "medium", "hard"]


def expected_full(ts):
    """Every tier pair (higher, lower) x every faction pairing, worked out here independently of ladder_matrix."""
    out = set()
    for i, lo in enumerate(ts):
        for hi in ts[i:]:
            for fa in L.FACTIONS:
                for fb in L.FACTIONS:
                    if hi == lo and L.FACTIONS.index(fb) < L.FACTIONS.index(fa):
                        continue   # at equal tiers (a, b) and (b, a) are one match
                    out.add(f"{hi}:{fa}|{lo}:{fb}")
    return out


class LadderTiersTest(unittest.TestCase):

    def test_the_real_enum_gives_the_ladder_tiers_weakest_first(self):
        self.assertEqual(L.tiers(), LADDER_TIERS)

    def test_quick_is_the_exact_three_matches(self):
        self.assertEqual([m["spec"] for m in L.build("quick", L.tiers(), 0)],
                         ["medium:villagers|medium:villagers", "hard:villagers|medium:monsters", "medium:monsters|easy:villagers"])

    def test_full_is_every_tier_pair_and_faction_pairing(self):
        ms = L.build("full", L.tiers(), 0)
        specs = [m["spec"] for m in ms]
        self.assertEqual(len(specs), len(set(specs)))
        self.assertEqual(set(specs), expected_full(LADDER_TIERS))
        self.assertEqual(len(specs), 231)

    def test_every_nightly_match_names_real_tiers_and_factions(self):
        ts = L.tiers()
        for day in (739922, 739923, 739924, 739925):   # Nov 2-5 2026 (date.toordinal()), the four failed nights
            ms = L.build("nightly", ts, day)
            self.assertEqual(len(ms), L.TARGET)
            for m in ms:
                (ta, fa), (tb, fb) = (side.split(":") for side in m["spec"].split("|"))
                self.assertIn(ta, ts, m)
                self.assertIn(tb, ts, m)
                self.assertIn(fa, L.FACTIONS, m)
                self.assertIn(fb, L.FACTIONS, m)
                self.assertRegex(m["id"], r"^[a-z_]+-[a-z_]+_v_[a-z_]+-[a-z_]+$")

    def test_comments_constructor_arguments_and_methods_are_not_tiers(self):
        src = """
        public class BotPlayer {
            public enum Difficulty {
                /* the weakest */ EASY(1.0), // a comment, with a comma
                MEDIUM(1.5),
                HARD(2.0) {   // a constant with a body
                    @Override public boolean easyLike() { return false; }
                },
                PASSIVE(0.0);   // the order is the lobby cycle (index 0..3, in packets)

                private final double pace;
                Difficulty(double pace) { this.pace = pace; }
                public String label() { return name().charAt(0) + name().substring(1).toLowerCase(); }
                public boolean easyLike() { return this == EASY || this == PASSIVE; }
            }
            enum Other { A, B }
        }
        """
        with tempfile.NamedTemporaryFile("w", suffix=".java", delete=False, encoding="utf-8") as f:
            f.write(src)
        old = L.BOT_PLAYER
        try:
            L.BOT_PLAYER = f.name
            self.assertEqual(L.tiers(), LADDER_TIERS)
        finally:
            L.BOT_PLAYER = old
            os.unlink(f.name)

    def test_a_new_tier_grows_the_ladder_by_itself(self):
        with tempfile.NamedTemporaryFile("w", suffix=".java", delete=False, encoding="utf-8") as f:
            f.write("enum Difficulty { EASY, MEDIUM, HARD, BRUTAL, PASSIVE; public String label() { return \"\"; } }")
        old = L.BOT_PLAYER
        try:
            L.BOT_PLAYER = f.name
            ts = L.tiers()
            self.assertEqual(ts, ["easy", "medium", "hard", "brutal"])
            self.assertEqual(len(list(itertools.combinations_with_replacement(ts, 2))), len(L.tier_pairs(ts)))
        finally:
            L.BOT_PLAYER = old
            os.unlink(f.name)


if __name__ == "__main__":
    unittest.main()
