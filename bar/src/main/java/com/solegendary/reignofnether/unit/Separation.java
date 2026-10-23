package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

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
    private static final List<LivingEntity> PUSH_SCRATCH = new ArrayList<>();

    /**
     * A unit's vanilla push ({@code LivingEntity.pushEntities}, every tick) finds the bodies it overlaps through
     * {@link UnitGrid} instead of a world entity query: the Oct 22 server profile had ~104 such queries per pillager
     * per 10 s window. Same bodies, same cramming rule, same push; only other units are looked up (a unit
     * is no longer nudged by an animal or item it overlaps). Returns false when vanilla should run (not a server unit).
     */
    public static boolean pushOverlapping(LivingEntity self) {
        if (!(self instanceof com.solegendary.reignofnether.unit.interfaces.Unit) || !(self.level() instanceof ServerLevel level))
            return false;
        if (self.hasEffect(MobEffectRegistrar.PHASING.get()))
            return true;   // a phasing unit pushes nothing (LivingEntityMixin.doPush)
        int sys = Sys.begin(Sys.PUSH_SCAN);
        try {
            AABB box = self.getBoundingBox();
            UnitGrid.inBox(level, box.minX, box.minZ, box.maxX, box.maxZ, PUSH_SCRATCH);
            Predicate<Entity> pushable = EntitySelector.pushableBy(self);
            int n = 0;
            for (int i = 0; i < PUSH_SCRATCH.size(); i++) {
                LivingEntity le = PUSH_SCRATCH.get(i);
                if (le == self || !le.getBoundingBox().intersects(box) || !pushable.test(le))
                    continue;
                PUSH_SCRATCH.set(n++, le);
            }
            if (n > 0) {
                int max = level.getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
                if (max > 0 && n > max - 1 && self.getRandom().nextInt(4) == 0) {
                    int crammed = 0;
                    for (int i = 0; i < n; i++)
                        if (!PUSH_SCRATCH.get(i).isPassenger())
                            crammed++;
                    if (crammed > max - 1)
                        self.hurt(self.damageSources().cramming(), 6.0F);
                }
                for (int i = 0; i < n; i++)
                    PUSH_SCRATCH.get(i).push(self);   // what doPush does (it is protected)
            }
            PUSH_SCRATCH.clear();
        } finally {
            Sys.end(sys);
        }
        return true;
    }

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
        if (!safeToStep(level, mob, px / len, pz / len))
            return;   // never shoved into lava, fire or off a ledge: it stays overlapped instead
        mob.setDeltaMovement(mob.getDeltaMovement().add(px / len * step, 0, pz / len * step));
        mob.hasImpulse = true;
    }

    /**
     * The push does not go through the pathfinder, which is what keeps units out of lava and off cliffs - so it checks
     * the ground it would move onto. A bot's three starting workers spawn stacked; the soak's Gravebound commander was
     * pushed apart from them into lava 10 blocks from its start and died 16 s in (Oct 21, main 223a4bb).
     * Unsafe: lava or fire at the feet or below them, a damaging floor (magma, campfire, cactus), or a drop of two
     * blocks or more.
     */
    static boolean safeToStep(ServerLevel level, Mob mob, double dirX, double dirZ) {
        double reach = mob.getBbWidth() / 2 + 0.5;
        net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(
            mob.getX() + dirX * reach, mob.getY() + 0.1, mob.getZ() + dirZ * reach);
        if (hazard(level, feet) || hazard(level, feet.below()))
            return false;
        // a drop: nothing solid under the feet for two blocks
        return !(level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
            && level.getBlockState(feet.below(2)).getCollisionShape(level, feet.below(2)).isEmpty());
    }

    private static boolean hazard(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.getFluidState().is(net.minecraft.tags.FluidTags.LAVA))
            return true;
        var block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.BaseFireBlock
            || block instanceof net.minecraft.world.level.block.MagmaBlock
            || block instanceof net.minecraft.world.level.block.CampfireBlock
            || block instanceof net.minecraft.world.level.block.CactusBlock;   // (walkable magma is safe by design)
    }
}
