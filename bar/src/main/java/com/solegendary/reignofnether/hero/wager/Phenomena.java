package com.solegendary.reignofnether.hero.wager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What a granted wager does: one phenomenon per intent and tier, plus the intent's twist ("granted, mostly"), stitched
 * from effects the game already has (weather, hero helpers, summons, reveals). Every number lives in a {@link Spec} so
 * the fairness caps can be checked in one place: no phenomenon deals more than {@link #MAX_DAMAGE} to one target, reaches
 * further than {@link #MAX_RADIUS}, or lasts past {@link #MAX_TICKS}; none damages a building (they only touch units).
 */
public final class Phenomena {
    private Phenomena() { }

    public static final float MAX_DAMAGE = 50;
    public static final float MAX_RADIUS = 6;
    public static final int MAX_TICKS = 90 * 20;

    /** A phenomenon's name (for the book's reply) and its numbers: damage per target in total, radius, duration. */
    public record Spec(String intent, int tier, boolean twist, String name, float damage, float radius, int ticks) { }

    public static List<Spec> specs() {
        return List.of();
    }

    public static Spec spec(String intent, int tier, boolean twist) {
        return null;
    }

    /** Runs the phenomenon and returns its name for the reply. */
    public static String cast(ServerLevel level, Mob hero, String intent, int tier, boolean twist, Vec3 aim, int quantity) {
        return "";
    }
}
