package com.solegendary.reignofnether.essence;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Essence wisps (design/essence_casters.md). STUB: the API the game tests are written against; the rules land in the
 * next commit.
 */
public class EssenceServerEvents {

    public static final String TAG = "bt_essence_wisp";
    public static final int LIFETIME_TICKS = 45 * 20;
    public static final int MAX_WISPS = 200;
    public static final double GATHER_RANGE = 8.0;

    private static final List<Entity> wisps = new ArrayList<>();

    public static List<Entity> getWisps() {
        return wisps;
    }

    public static boolean isWisp(Entity e) {
        return false;
    }

    public static int valueOf(Entity wisp) {
        return 0;
    }

    public static long bornOf(Entity wisp) {
        return 0;
    }

    public static void backdate(Entity wisp, int ticks) { }

    public static Entity spawnWisp(ServerLevel level, double x, double y, double z, int value) {
        return null;
    }
}
