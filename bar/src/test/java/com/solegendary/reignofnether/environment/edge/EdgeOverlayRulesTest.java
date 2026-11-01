package com.solegendary.reignofnether.environment.edge;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.solegendary.reignofnether.environment.edge.EdgeOverlayRules.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Edge overlays (lovish, Oct 30: "build edge overlays", from his reference of a cracked dry-earth path that frays into
 * the grass): where dirt_path / dirt / sand / gravel meet grass, a ragged grass fringe spills over onto the path top so
 * the border stops reading as a hard square tile line from the RTS camera. These are the pure rules - which fringe
 * pieces a block's 8 neighbours ask for, how each is turned, which variant a position draws, and the quad each piece
 * covers. Written before EdgeOverlayRules.
 */
class EdgeOverlayRulesTest {

    private static int count(int[] pieces, int piece) {
        int c = 0;
        for (int p : pieces) if (piece(p) == piece) c++;
        return c;
    }

    private static boolean has(int[] pieces, int piece, int rot) {
        for (int p : pieces) if (piece(p) == piece && rot(p) == rot) return true;
        return false;
    }

    @Test
    void noGrassNoPieces() {
        for (int seed = -8; seed < 8; seed++)
            assertEquals(0, pieces(key(0, 0, 64, seed)).length);
    }

    @Test
    void oneGrassSideIsOneEdgeStripTurnedToThatSide() {
        int[] sides = {N, E, S, W};
        for (int r = 0; r < 4; r++) {
            int[] p = pieces(key(sides[r], 0, 64, 3));
            assertEquals(1, p.length, "side " + r);
            assertEquals(PIECE_EDGE, piece(p[0]));
            assertEquals(r, rot(p[0]), "the strip faces the grass side");
        }
    }

    @Test
    void twoTouchingGrassSidesGetAnOuterCornerBetweenThem() {
        // N + E: corner NE is rotation 1 (NW 0, NE 1, SE 2, SW 3)
        int[] p = pieces(key(N | E, 0, 64, 0));
        assertEquals(2, count(p, PIECE_EDGE));
        assertEquals(1, count(p, PIECE_OUTER));
        assertTrue(has(p, PIECE_OUTER, 1));
        assertTrue(has(pieces(key(S | W, 0, 64, 0)), PIECE_OUTER, 3));
        assertTrue(has(pieces(key(W | N, 0, 64, 0)), PIECE_OUTER, 0));
        assertTrue(has(pieces(key(E | S, 0, 64, 0)), PIECE_OUTER, 2));
        // opposite sides do not meet: no corner
        assertEquals(0, count(pieces(key(N | S, 0, 64, 0)), PIECE_OUTER));
        assertEquals(0, count(pieces(key(E | W, 0, 64, 0)), PIECE_OUTER));
    }

    @Test
    void aDiagonalOnlyGrassNeighbourGetsAnInnerTuft() {
        int[] diag = {NW, NE, SE, SW};
        for (int r = 0; r < 4; r++) {
            int[] p = pieces(key(diag[r], 0, 64, 5));
            assertEquals(1, p.length, "corner " + r);
            assertEquals(PIECE_INNER, piece(p[0]));
            assertEquals(r, rot(p[0]));
        }
    }

    @Test
    void aDiagonalBesideAGrassSideIsAlreadyCoveredByTheStrip() {
        // NE grass with N grass: the north strip reaches that corner - no tuft on top of it
        assertEquals(0, count(pieces(key(N | NE, 0, 64, 0)), PIECE_INNER));
        assertEquals(0, count(pieces(key(E | NE, 0, 64, 0)), PIECE_INNER));
        // ...but the other diagonals still get theirs
        assertTrue(has(pieces(key(N | SE, 0, 64, 0)), PIECE_INNER, 2));
    }

    @Test
    void grassAllRoundIsFourStripsAndFourCorners() {
        int[] p = pieces(key(0xFF, 0, 64, 7));
        assertEquals(4, count(p, PIECE_EDGE));
        assertEquals(4, count(p, PIECE_OUTER));
        assertEquals(0, count(p, PIECE_INNER));
    }

    @Test
    void variantsStayInRange() {
        for (int mask = 0; mask < 256; mask++) {
            for (int x = -5; x < 5; x++) {
                for (int p : pieces(key(mask, x, 64, 3 * x + 1))) {
                    int v = variant(p);
                    assertTrue(v >= 0 && v < variants(piece(p)), "mask " + mask + " x " + x);
                }
            }
        }
    }

    @Test
    void piecesAreOnePerSlot() {
        // two pieces in one slot would draw at the same lift and z-fight
        for (int mask = 0; mask < 256; mask++) {
            Set<Integer> slots = new HashSet<>();
            for (int p : pieces(key(mask, 0, 64, 1))) assertTrue(slots.add(slot(p)), "mask " + mask);
        }
    }

    @Test
    void liftsAreDistinctAndAHairAboveTheFace() {
        Set<Float> seen = new HashSet<>();
        for (int s = 0; s < SLOTS; s++) {
            float l = lift(s);
            assertTrue(l >= 0.001f && l <= 0.005f, "slot " + s + " lift " + l);
            assertTrue(seen.add(l), "slot " + s + " shares its lift");
        }
    }

    @Test
    void theSameBlockAlwaysGetsTheSameKey() {
        // the chunk mesher rebuilds a section many times (fog, block changes): the fringe must not flicker between
        // variants from one rebuild to the next
        for (int x = -40; x < 40; x++)
            for (int z = -40; z < 40; z++)
                assertEquals(key(0xFF, x, 64, z), key(0xFF, x, 64, z));
    }

    /** The longest run of one variant along a straight grass edge, and asserts every variant shows. */
    private static int longestRun(int side, boolean alongX) {
        int run = 1, longest = 1, prev = -1;
        Set<Integer> used = new HashSet<>();
        for (int c = -100; c < 100; c++) {
            int[] p = pieces(alongX ? key(side, c, 64, 10) : key(side, 10, 64, c));
            assertEquals(1, p.length);
            int v = variant(p[0]);
            used.add(v);
            run = v == prev ? run + 1 : 1;
            longest = Math.max(longest, run);
            prev = v;
        }
        assertEquals(EDGE_VARIANTS, used.size(), "every strip variant shows along a long edge");
        return longest;
    }

    @Test
    void aLongStraightEdgeNeverRepeatsOneStripMoreThanTwice() {
        // a path with grass along one side: no run of 3+ identical strips (a visible repeat from the RTS camera at
        // ~30 px per block), on every side and both axes
        assertTrue(longestRun(N, true) <= 2, "north strips");
        assertTrue(longestRun(S, true) <= 2, "south strips");
        assertTrue(longestRun(E, false) <= 2, "east strips");
        assertTrue(longestRun(W, false) <= 2, "west strips");
    }

    @Test
    void cornerVariantsBothShow() {
        Set<Integer> outer = new HashSet<>(), inner = new HashSet<>();
        for (int x = 0; x < 40; x++) {
            for (int p : pieces(key(N | E, x, 64, 7))) if (piece(p) == PIECE_OUTER) outer.add(variant(p));
            for (int p : pieces(key(SW, x, 64, 7))) if (piece(p) == PIECE_INNER) inner.add(variant(p));
        }
        assertEquals(OUTER_VARIANTS, outer.size());
        assertEquals(INNER_VARIANTS, inner.size());
    }

    @Test
    void theKeyHoldsOnlyWhatIsDrawn() {
        // two blocks that draw the same pieces share one key (and one cached quad list); the key fits its range
        assertEquals(0, key(0, 3, 64, 9));
        assertEquals(key(N | NE | NW, 3, 64, 9), key(N, 3, 64, 9), "diagonals beside a strip draw nothing");
        for (int mask = 0; mask < 256; mask++) {
            int k = key(mask, 5, 64, -2);
            assertTrue(k >= 0 && k < KEYS);
            assertEquals(k, key(keyMask(k), 5, 64, -2), "the key's mask is the drawn mask");
        }
    }

    @Test
    void anEdgeQuadLiesAlongItsSideWithTheSpritesTopEdgeOnThatSide() {
        for (int r = 0; r < 4; r++) {
            float[] q = quad(PIECE_EDGE, r);
            for (int i = 0; i < 4; i++) {
                float x = q[i * 4], z = q[i * 4 + 1], t = q[i * 4 + 3];
                float d = switch (r) {          // distance from the grass side
                    case 0 -> z;
                    case 1 -> 1 - x;
                    case 2 -> 1 - z;
                    default -> x;
                };
                assertTrue(d >= -1e-6 && d <= REACH + 1e-6, "rot " + r + " vertex " + i + " d " + d);
                // the sprite's t (0 = its top edge, where the fringe is rooted) is the distance from the grass side
                assertEquals(d, t, 1e-6, "rot " + r + " vertex " + i);
            }
        }
    }

    @Test
    void aCornerQuadSitsInItsCorner() {
        float[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};   // NW, NE, SE, SW as (x, z)
        for (int piece : new int[]{PIECE_OUTER, PIECE_INNER}) {
            for (int r = 0; r < 4; r++) {
                float[] q = quad(piece, r);
                boolean touches = false;
                for (int i = 0; i < 4; i++) {
                    float x = q[i * 4], z = q[i * 4 + 1];
                    assertTrue(Math.abs(x - corners[r][0]) <= REACH + 1e-6
                            && Math.abs(z - corners[r][1]) <= REACH + 1e-6, "piece " + piece + " rot " + r);
                    // sprite (0, 0) is its NW corner, rooted in the grass corner
                    if (q[i * 4 + 2] == 0 && q[i * 4 + 3] == 0) {
                        assertEquals(corners[r][0], x, 1e-6);
                        assertEquals(corners[r][1], z, 1e-6);
                        touches = true;
                    }
                }
                assertTrue(touches, "piece " + piece + " rot " + r + " has its sprite corner in the block corner");
            }
        }
    }

    @Test
    void everyQuadWindsLikeVanillasUpFace() {
        // vanilla's up face runs (0,0) (0,1) (1,1) (1,0) in (x, z): a negative shoelace sum. A flipped quad faces down
        // and back-face culling drops it.
        for (int piece = 0; piece < PIECES; piece++) {
            for (int r = 0; r < 4; r++) {
                float[] q = quad(piece, r);
                float sum = 0;
                for (int i = 0; i < 4; i++) {
                    int j = (i + 1) % 4;
                    sum += q[i * 4] * q[j * 4 + 1] - q[j * 4] * q[i * 4 + 1];
                }
                assertTrue(sum < 0, "piece " + piece + " rot " + r + " winds the wrong way: " + sum);
                assertFalse(Math.abs(sum) < 1e-6, "degenerate");
            }
        }
    }

    @Test
    void onALoweredTopThePieceRisesToTheGrassAtTheGrassEdge() {
        // eyes 41049090535 (Oct 30): around dirt_path / farmland (top at 15/16) a straight dark line still ran along
        // the block boundary - the grass block's 1/16 side step - outside the ragged fringe. The fringe now climbs to
        // the grass's height where it touches grass and slopes down to the lowered top inward, covering the step.
        for (int piece = 0; piece < PIECES; piece++) {
            float[] q = quad(piece, 0);
            float[] rise = rise(piece);
            assertEquals(4, rise.length);
            for (int i = 0; i < 4; i++) {
                float s = q[i * 4 + 2], t = q[i * 4 + 3];
                boolean onGrass = switch (piece) {
                    case PIECE_EDGE -> t == 0;                        // the strip's grass side
                    case PIECE_OUTER -> s == 0 || t == 0;             // both grass sides of the corner
                    default -> s == 0 && t == 0;                      // the diagonal: only the corner point
                };
                assertEquals(onGrass ? 1f : 0f, rise[i], 1e-6, "piece " + piece + " vertex " + i);
            }
        }
    }
}
