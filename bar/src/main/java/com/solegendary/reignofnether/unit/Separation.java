package com.solegendary.reignofnether.unit;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps units from melting into one blob (lovish's Oct 19 match: "all the units just merge together"). Vanilla's
 * entity push is ~0.05 blocks a tick per contact and loses to navigation every tick, and a dozen unit classes are not
 * pushable at all, so a crowd walking to one point stacks. Every {@link #INTERVAL} ticks (staggered by entity id) a unit
 * on the ground gets a small velocity away from the units its body overlaps, found through {@link UnitGrid} (no AABB
 * scan), capped at {@link #MAX_PUSH}. Heavier bodies move less: the push is shared by width. A unit that resists
 * knockback completely (the big beasts) holds still and lets the small ones step round it. Server only.
 */
public final class Separation {
    private Separation() { }

    static final int INTERVAL = 4;
    static final double GAP = 0.1;          // wanted clearance between two bodies, blocks
    static final double MAX_PUSH = 0.08;    // blocks per tick added at most per pass
    static final double STRENGTH = 0.06;
    static final double MAX_NEIGHBOUR_HALF_WIDTH = 2.0;

    private static final List<LivingEntity> SCRATCH = new ArrayList<>();

    public static void tick(Mob mob) {
        if ((mob.tickCount + mob.getId()) % INTERVAL != 0)
            return;
        if (!(mob.level() instanceof ServerLevel level) || mob.isPassenger() || mob.isVehicle() || mob.noPhysics
                || !mob.onGround() || !mob.isAlive())
            return;
        if (mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0)
            return;
        if (mob instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && u.getHoldPosition())
            return;   // held units keep their exact spot (nothing walks them back); others step round them
        double w = mob.getBbWidth(), x = mob.getX(), z = mob.getZ(), y = mob.getY();
        UnitGrid.near(level, x, z, w / 2 + MAX_NEIGHBOUR_HALF_WIDTH + GAP, SCRATCH);
        double px = 0, pz = 0;
        for (LivingEntity le : SCRATCH) {
            if (le == mob || !le.isAlive() || le.isPassenger() || le.isVehicle() || Math.abs(le.getY() - y) > 1.5)
                continue;
            double min = (w + le.getBbWidth()) / 2 + GAP;
            double dx = x - le.getX(), dz = z - le.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 >= min * min)
                continue;
            double d = Math.sqrt(d2);
            double overlap = min - d;   // from the true distance: a perfectly stacked pair overlaps the most
            if (d < 1.0e-3) {   // exactly on top of each other: a direction split by id so the pair moves apart
                double a = ((mob.getId() * 0.6180339887) % 1.0) * Math.PI * 2;
                dx = Math.cos(a);
                dz = Math.sin(a);
                d = 1.0;        // (dx, dz) is already a unit vector
            }
            double share = le.getBbWidth() / (w + le.getBbWidth());   // the narrower body takes more of the step
            double s = overlap / min * share;
            px += dx / d * s;
            pz += dz / d * s;
        }
        SCRATCH.clear();
        double len = Math.sqrt(px * px + pz * pz);
        if (len < 1.0e-4)
            return;
        double step = Math.min(MAX_PUSH, STRENGTH * len);
        mob.setDeltaMovement(mob.getDeltaMovement().add(px / len * step, 0, pz / len * step));
        mob.hasImpulse = true;
    }
}
