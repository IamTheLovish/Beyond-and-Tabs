package com.solegendary.reignofnether.barfx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Rune circles (signature stub: the tests first; the system lands in the next commit). */
public final class RuneFx {
    private RuneFx() { }

    public static final int CAST_TICKS = 30;
    public static final int MAX_LIVE = 48;
    public static final float CAST_RADIUS = 1.6f;

    public static boolean cast(LivingEntity caster) { return false; }

    public static boolean start(ServerLevel level, int id, Vec3 at, byte style, float radius, int ticks, boolean follow) { return false; }

    public static void stop(ServerLevel level, int id) { }

    public static boolean isLive(ServerLevel level, int id) { return false; }

    public static int liveCount(ServerLevel level) { return 0; }
}
