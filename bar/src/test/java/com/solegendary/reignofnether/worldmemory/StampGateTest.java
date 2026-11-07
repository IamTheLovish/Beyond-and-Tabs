package com.solegendary.reignofnether.worldmemory;

import net.minecraft.world.level.LevelHeightAccessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scorch and craters must stamp on every world a match is played on. The battlefield world starts at y 48
 * (dimension_type/battlefield.json): a load check at y 0 refused every stamp there (eyes scars run 42217669387 on main's
 * jar: "0 chunks marked on the server"), while the GameTest world (y -64) never showed it.
 */
class StampGateTest {

    private static LevelHeightAccessor world(int minY, int height) {
        return new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public int getMinBuildHeight() {
                return minY;
            }
        };
    }

    private static final LevelHeightAccessor BATTLEFIELD = world(48, 272);   // dimension_type/battlefield.json
    private static final LevelHeightAccessor OVERWORLD = world(-64, 384);
    private static final LevelHeightAccessor SKY_ISLAND = world(128, 128);   // no y 0 at all, far above it

    @Test
    void a_loaded_chunk_takes_a_stamp_on_the_battlefield_world() {
        assertTrue(StampGate.columnLoaded(BATTLEFIELD, (cx, cz) -> true, 100, -40),
            "a blast in a loaded chunk of the battlefield world (min y 48) must stamp");
    }

    @Test
    void a_loaded_chunk_takes_a_stamp_whatever_the_height_range() {
        assertTrue(StampGate.columnLoaded(OVERWORLD, (cx, cz) -> true, 0, 0), "overworld (min y -64)");
        assertTrue(StampGate.columnLoaded(SKY_ISLAND, (cx, cz) -> true, -500, 900), "a world from y 128 to 256");
    }

    @Test
    void an_unloaded_chunk_takes_none() {
        for (LevelHeightAccessor w : new LevelHeightAccessor[] { BATTLEFIELD, OVERWORLD, SKY_ISLAND })
            assertFalse(StampGate.columnLoaded(w, (cx, cz) -> false, 8, 8), "an unloaded chunk never takes a stamp");
    }

    @Test
    void it_asks_for_the_chunk_holding_the_column() {
        int[] asked = { Integer.MIN_VALUE, Integer.MIN_VALUE };
        StampGate.columnLoaded(BATTLEFIELD, (cx, cz) -> {
            asked[0] = cx;
            asked[1] = cz;
            return true;
        }, -1, 33);
        assertArrayEquals(new int[] { -1, 2 }, asked, "block (-1, 33) lies in chunk (-1, 2)");
    }
}
