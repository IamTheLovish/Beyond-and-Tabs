package com.solegendary.reignofnether.orthoview;

import com.solegendary.reignofnether.unit.Relationship;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules behind units under canopies (CanopyRules): the fade's soft circle, the holes it lives in, the occlusion
 * ray that picks which units get a silhouette, and the silhouette colours.
 */
class CanopyRulesTest {

    // the RTS camera's default-ish direction toward the camera: pitched ~60 degrees, looking north-ish
    private static final double DX = 0.0, DY = Math.sin(Math.toRadians(60)), DZ = Math.cos(Math.toRadians(60));

    @Test
    void fadeIsClearInsideSolidOutsideAndSmoothBetween() {
        assertEquals(0f, CanopyRules.fadeAlpha(0));
        assertEquals(0f, CanopyRules.fadeAlpha(CanopyRules.FADE_INNER));
        assertEquals(1f, CanopyRules.fadeAlpha(CanopyRules.FADE_OUTER));
        assertEquals(1f, CanopyRules.fadeAlpha(50));
        float mid = CanopyRules.fadeAlpha((CanopyRules.FADE_INNER + CanopyRules.FADE_OUTER) / 2);
        assertEquals(0.5f, mid, 1e-6);
        float last = 0f;
        for (double d = CanopyRules.FADE_INNER; d <= CanopyRules.FADE_OUTER; d += 0.05) {
            float a = CanopyRules.fadeAlpha(d);
            assertTrue(a >= last, "the fade never gets clearer further out");
            last = a;
        }
    }

    @Test
    void theHoleAlwaysContainsTheWholeSoftCircleWhileTheAnchorStaysWithinTheDrift() {
        // any point the fade would make see-through (alpha < 1) round an anchor that drifted up to HOLE_DRIFT across
        // the view from the hole's centre must be inside the hole, or the fade would show a hard edge
        double[][] drifts = { { CanopyRules.HOLE_DRIFT, 0, 0 }, { -CanopyRules.HOLE_DRIFT * 0.7, 0, 0 } };
        for (double[] drift : drifts) {
            for (double ox = -7; ox <= 7; ox += 0.5)
                for (double t = 0; t <= 30; t += 1.5) {
                    // a point at offset ox across the view, t along it, measured from the drifted anchor
                    double px = drift[0] + ox, py = t * DY, pz = t * DZ;
                    double fromAnchor = Math.abs(ox);
                    if (CanopyRules.fadeAlpha(fromAnchor) >= 1f)
                        continue;
                    assertTrue(CanopyRules.inHole(px, py, pz, DX, DY, DZ, CanopyRules.HOLE_RADIUS),
                            "point " + ox + " across, " + t + " along, anchor drifted " + drift[0]);
                }
        }
    }

    @Test
    void holesReachUpTheViewLineButNotBehindTheGround() {
        assertTrue(CanopyRules.inHole(0, 20 * DY, 20 * DZ, DX, DY, DZ, CanopyRules.HOLE_RADIUS));
        assertFalse(CanopyRules.inHole(0, -3 * DY, -3 * DZ, DX, DY, DZ, CanopyRules.HOLE_RADIUS),
                "a block further from the camera than the anchor hides nothing and stays in the mesh");
        assertFalse(CanopyRules.inHole(CanopyRules.HOLE_RADIUS + 0.5, 5 * DY, 5 * DZ, DX, DY, DZ, CanopyRules.HOLE_RADIUS));
    }

    @Test
    void recentreOnlyForDriftAcrossTheView() {
        // moving straight along the view line (the cursor sliding down a slope toward the camera) changes nothing on screen
        assertFalse(CanopyRules.needsRecentre(0, 2 * DY, 2 * DZ, 0, 0, 0, DX, DY, DZ));
        assertFalse(CanopyRules.needsRecentre(2.9, 0, 0, 0, 0, 0, DX, DY, DZ));
        assertTrue(CanopyRules.needsRecentre(3.1, 0, 0, 0, 0, 0, DX, DY, DZ));
        assertTrue(CanopyRules.needsRecentre(0, 8 * DY, 8 * DZ, 0, 0, 0, DX, DY, DZ), "a big climb moves the hole's span");
    }

    @Test
    void viewTurnRebuildsPastTwoDegrees() {
        double a = Math.toRadians(1), b = Math.toRadians(3);
        assertFalse(CanopyRules.viewTurned(0, 1, 0, Math.sin(a), Math.cos(a), 0));
        assertTrue(CanopyRules.viewTurned(0, 1, 0, Math.sin(b), Math.cos(b), 0));
    }

    @Test
    void rayFindsALeafAboveAndInFrontButNotBehindOrBesideIt() {
        // a unit at the origin; a canopy block 4 blocks up along the view line toward the camera
        int lx = 0, ly = (int) Math.floor(0.5 + 4 * DY), lz = (int) Math.floor(0.5 + 4 * DZ);
        Set<Long> leaves = new HashSet<>();
        leaves.add(key(lx, ly, lz));
        CanopyRules.Voxels v = (x, y, z) -> leaves.contains(key(x, y, z));
        assertTrue(CanopyRules.rayOccluded(0.5, 0.5, 0.5, DX, DY, DZ, 32, v));
        // the same block, but the camera looks from the other side: not in front of the unit
        assertFalse(CanopyRules.rayOccluded(0.5, 0.5, 0.5, DX, DY, -DZ, 32, v));
        // a unit three blocks to the side
        assertFalse(CanopyRules.rayOccluded(3.5, 0.5, 0.5, DX, DY, DZ, 32, v));
        // the unit's own voxel never counts
        Set<Long> own = new HashSet<>();
        own.add(key(0, 0, 0));
        assertFalse(CanopyRules.rayOccluded(0.5, 0.5, 0.5, DX, DY, DZ, 32, (x, y, z) -> own.contains(key(x, y, z))));
        // a canopy higher than the rise limit is not searched for
        assertFalse(CanopyRules.rayOccluded(0.5, 0.5, 0.5, DX, DY, DZ, 2, v));
    }

    @Test
    void rayStepsThroughEveryVoxelItCrosses() {
        // a diagonal ray must not skip a voxel it only clips at a corner region: walk it and record each visited cell
        Set<Long> visited = new HashSet<>();
        CanopyRules.rayOccluded(0.2, 0.3, 0.7, 0.48, 0.8, 0.36, 10, (x, y, z) -> {
            visited.add(key(x, y, z));
            return false;
        });
        // consecutive cells of a 6-connected walk: the count grows with |dx| + |dy| + |dz| per unit length
        assertTrue(visited.size() >= 15, "visited " + visited.size());
    }

    @Test
    void coloursFollowRelationshipAndStayReadable() {
        assertEquals(CanopyRules.ALLY_RGB, CanopyRules.silhouetteRgb(Relationship.FRIENDLY, 0x123456));
        assertEquals(CanopyRules.ENEMY_RGB, CanopyRules.silhouetteRgb(Relationship.HOSTILE, 0x123456));
        assertEquals(0x55FF55, CanopyRules.silhouetteRgb(Relationship.OWNED, 0x55FF55), "a bright team colour is kept");
        int navy = CanopyRules.silhouetteRgb(Relationship.OWNED, 0x101060);
        float r = ((navy >> 16) & 0xFF) / 255f, g = ((navy >> 8) & 0xFF) / 255f, b = (navy & 0xFF) / 255f;
        assertTrue(0.2126f * r + 0.7152f * g + 0.0722f * b >= 0.44f, "a dark team colour is lifted to read on foliage");
        assertTrue(b > r && b > g, "and keeps its hue");
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFF) | (((long) y & 0xFFFFF) << 20) | (((long) z & 0xFFFFF) << 40);
    }
}
