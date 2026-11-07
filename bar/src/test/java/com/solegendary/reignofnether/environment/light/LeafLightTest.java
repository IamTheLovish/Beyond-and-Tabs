package com.solegendary.reignofnether.environment.light;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Crown lighting (lovish, Nov 6: "Crowns too dark: fix lighting"): the porous leaf shade, in smooth lighting's own
 * corner rule (vanilla's AmbientOcclusionFace, Embeddium's AoFaceData: the mean of four neighbours' shades).
 * The in-game band - crowns 0.75-0.88 of the open lawn's luma in the RTS still, both looks - is measured on the eyes leaf
 * sweep by tools/crown_measure.py; these pin the rule that gets it there.
 */
class LeafLightTest {

    /** As LeafLightGameTests: a leaf face inside a crown keeps at least half its light; a crown's step still shows. */
    static final float LEAF_SHADE_MIN = 0.5F, LEAF_SHADE_MAX = 0.9F;

    @AfterEach
    void shippedShade() {
        LeafLight.setEyesShade(Float.NaN);
        LeafLight.setEyesFaceLift(Float.NaN);
    }

    @Test
    void solid_and_clear_blocks_keep_vanillas_shade() {
        assertEquals(0.2F, LeafLight.shadeOf(false, true));
        assertEquals(1.0F, LeafLight.shadeOf(false, false));
        assertEquals(LeafLight.SOLID_SHADE, 0.2F);
    }

    @Test
    void a_leaf_face_inside_a_crown_keeps_at_least_half_its_light() {
        // a face between leaves - seen through a crown's holes and at its steps: all four samples are leaf blocks.
        // Vanilla's 0.2 left these at a fifth of the light: the near-black crowns
        float s = LeafLight.shadeOf(true, true);
        float inside = LeafLight.cornerAo(s, s, s, s, true);
        assertTrue(inside >= LEAF_SHADE_MIN, "a leaf face inside a crown at " + inside + " of its light");
    }

    @Test
    void a_crown_keeps_its_relief() {
        // an open top face beside a rising leaf wall (a crown's step): air in front and on one edge, leaves on the
        // other edge and the corner. With no leaf occlusion at all (1.0) the steps vanish and the canopy stops reading
        // as a mass; it must stay at least 5% under an open top face
        float s = LeafLight.shadeOf(true, true);
        float step = LeafLight.cornerAo(1F, s, 1F, s, true);
        assertTrue(s <= LEAF_SHADE_MAX, "leaf shade " + s);
        assertTrue(step <= 0.95F, "a crown step's corner at " + step + " of an open top face");
    }

    @Test
    void without_a_shader_a_crowns_sides_are_lit_as_foliage_but_keep_their_form() {
        // vanilla shades a leaf cube like stone: east / west faces 0.6, north / south 0.8 of the top. A face of leaves is
        // leaves turned every way: its sides come up toward the top, but stay visibly under it - the eyes sweep
        // (42206522370) showed a full lift (every face as the top) melts a crown's steps into one flat blob
        float lift = LeafLight.faceLift();
        float ew = LeafLight.leafFaceShade(4, lift), ns = LeafLight.leafFaceShade(2, lift);
        assertEquals(1.0F, LeafLight.leafFaceShade(1, lift));
        assertTrue(ew >= 0.7F && ew <= 0.9F, "east / west leaf faces at " + ew + " of the top");
        assertTrue(ns >= 0.85F && ns <= 0.95F, "north / south leaf faces at " + ns + " of the top");
        assertTrue(LeafLight.leafFaceShade(0, lift) <= 0.8F, "a crown's underside stays the darkest face");
        assertTrue(ew < ns, "the sides keep vanilla's order");
    }

    @Test
    void a_leaf_quads_colour_is_scaled_with_its_alpha_kept() {
        // LeafFaces carries the face shade in the vertex colour (ABGR), which the renderers multiply in
        int[] v = new int[32];
        for (int i = 0; i < 4; i++)
            v[i * 8 + LeafFaces.COLOUR] = 0xFFFFFFFF;
        int[] out = LeafFaces.tinted(v, 0.8F);
        for (int i = 0; i < 4; i++)
            assertEquals(0xFFCCCCCC, out[i * 8 + LeafFaces.COLOUR]);
        assertEquals(0xFFFFFFFF, v[LeafFaces.COLOUR], "the model's own quad is never changed");
    }

    @Test
    void the_shipped_shade_is_the_one_both_looks_get() {
        // the block state asks leafShade(); the shader pack reads that vertex AO (lit.fsh separateAo): one value
        assertEquals(LeafLight.LEAF_SHADE, LeafLight.leafShade());
        assertEquals(LeafLight.LEAF_SHADE, LeafLight.shadeOf(true, true));
    }

    @Test
    void the_eyes_sweep_sets_a_shade_and_gives_it_back() {
        LeafLight.setEyesShade(0.35F);
        assertEquals(0.35F, LeafLight.shadeOf(true, true));
        assertEquals(0.2F, LeafLight.shadeOf(false, true));   // never a solid block's
        LeafLight.setEyesShade(Float.NaN);
        assertEquals(LeafLight.LEAF_SHADE, LeafLight.leafShade());
    }
}
