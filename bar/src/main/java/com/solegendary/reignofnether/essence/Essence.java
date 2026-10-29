package com.solegendary.reignofnether.essence;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Essence, the casters' second resource (design/essence_casters.md). STUB: the API the game tests are written against;
 * the rules land in the next commit.
 */
public final class Essence {

    public static final int CAPACITY = 30;
    public static final float METAL_PER_ESSENCE = 25f;
    public static final String CARRIER_TAG = "bt_essence_carrier";
    public static final String NO_ESSENCE_TAG = "bt_no_essence";

    private Essence() { }

    public static int essenceFor(float metal) {
        return 0;
    }

    public static boolean isCarrier(Entity e) {
        return false;
    }

    public static void setCarrier(Entity e, boolean carrier) { }

    public static int get(LivingEntity carrier) {
        return 0;
    }

    public static int room(LivingEntity carrier) {
        return 0;
    }

    public static int add(LivingEntity carrier, int amount) {
        return 0;
    }

    public static boolean trySpend(LivingEntity carrier, int amount) {
        return false;
    }

    public static void markSummoned(Entity e) { }

    public static boolean isSummoned(Entity e) {
        return false;
    }
}
