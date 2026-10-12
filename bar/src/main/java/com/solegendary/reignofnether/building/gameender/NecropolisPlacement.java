package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.DragonRaiseServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.UnitUnstick;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Necropolis's strike (Necropolis; design/big_units.md 2.4): <b>mass raise</b>. {@link #BASE_RISEN} undead
 * (zombies and skeletons, alternating) claw up in a ring round the target for the Necropolis's owner, plus one for every
 * wreck within {@link #WRECK_RADIUS} blocks of it (the wreck is spent; at most {@link #MAX_WRECK_BONUS}) - "every wreck
 * is an army". The risen carry the Bone Dragon's risen tag (DragonRaiseServerEvents.TAG: no wreck, no free metal) and
 * crumble after {@link #LIFETIME_TICKS} (GameEnderServerEvents sweeps them). No population check: the host is what the
 * player paid an army's worth of metal for.
 * <p>
 * Where they rise ({@link #riseCell}): on the ground round the target building, never on it. The first version took
 * each ring point's height from the heightmap, which round a capitol is its roof (and in a walled yard the wall top):
 * the host stood on the victim's roof, out of the fight. Now the ring hugs the target's footprint and every risen gets
 * the nearest cell a unit can stand in, searched from the target's ground level, outside every building, with room
 * for its body and no lava or fire in or beside it, that it can walk out of.
 */
public class NecropolisPlacement extends GameEnderPlacement {

    public static final int BASE_RISEN = 10;
    public static final int MAX_WRECK_BONUS = 8;
    public static final double WRECK_RADIUS = 16;
    public static final int LIFETIME_TICKS = 45 * 20;
    /** The ring's least radius round the target's centre (a small building's ring, or one whose building is gone). */
    static final double RING = 5.0;
    /** How far out from the target building's walls the ring runs: at its gates, not on its roof. */
    public static final double WALL_MARGIN = 2.0;
    /** Columns searched round a ring point for ground, then the wider fallback (UnitUnstick's rescue radius). */
    static final int RISE_SEARCH = 4, RISE_SEARCH_WIDE = 10;
    /** The ground search window round the target's ground level: further down than up (a dip, a ditch at the walls). */
    static final int RISE_DOWN = 8, RISE_UP = 3;

    /** The risen of the last strike (game test). */
    public final List<Entity> lastRisen = new ArrayList<>();

    public NecropolisPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                               ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return ParticleTypes.SOUL_FIRE_FLAME;
    }

    @Override
    protected void strike(ServerLevel sl, BlockPos pos, String victim) {
        lastRisen.clear();
        // the battlefield's dead first: every wreck near the target is eaten for one more risen
        int bonus = 0;
        double r2 = WRECK_RADIUS * WRECK_RADIUS;
        for (Entity w : new ArrayList<>(WreckServerEvents.getWrecks())) {
            if (bonus >= MAX_WRECK_BONUS)
                break;
            if (w.isRemoved() || w.level() != sl)
                continue;
            double dx = w.getX() - (pos.getX() + 0.5), dz = w.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz > r2)
                continue;
            sl.sendParticles(ParticleTypes.SOUL, w.getX(), w.getY() + 0.3, w.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
            WreckServerEvents.drain(sl, w, WreckServerEvents.metalOf(w) + 1);
            bonus++;
        }
        int total = BASE_RISEN + bonus;
        long expire = sl.getGameTime() + LIFETIME_TICKS;
        BuildingPlacement target = buildingAt(pos);
        // the level a unit stands at beside the target: on top of its foundation layers (the ground its walls rise
        // from) - not its centre (mid-height) and never its roof. A building destroyed during the telegraph leaves
        // only its centre: the first air-over-ground step near it (the World Seed's ground rule)
        int groundY = target != null ? target.minCorner.getY() + target.getBuilding().foundationYLayers
            : com.solegendary.reignofnether.mana.Blink.groundY(sl, pos.getX(), pos.getZ(), pos.getY());
        Set<Long> taken = new HashSet<>();
        int missed = 0;
        for (int i = 0; i < total; i++) {
            var type = i % 2 == 0 ? EntityRegistrar.ZOMBIE_UNIT.get() : EntityRegistrar.SKELETON_UNIT.get();
            double a = i * Math.PI * 2 / total;
            double[] p = ringPoint(pos, target, a);
            BlockPos cell = riseCell(sl, (int) Math.floor(p[0]), groundY, (int) Math.floor(p[1]),
                type.getWidth(), type.getHeight(), taken);
            if (cell == null) {
                missed++;
                continue;
            }
            taken.add(cell.asLong());
            // spawnMob stands the unit on the block above the one it is given
            Entity e = UnitServerEvents.spawnMob(type, sl, cell.below(), ownerName);
            if (e == null) {
                missed++;
                continue;
            }
            GameEnderServerEvents.markRisen(e, expire);
            lastRisen.add(e);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + 0.2, e.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
        }
        if (missed > 0)
            ReignOfNether.LOGGER.warn("Necropolis of {}: {} of {} risen found no ground to stand on near {} ({})",
                ownerName, missed, total, pos.toShortString(), target != null ? target.getBuilding().name : "its building is gone");
        double ring = target != null ? Math.max(RING, halfSpan(target) + WALL_MARGIN) : RING;
        sl.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, groundY + 0.5, pos.getZ() + 0.5, 40, ring, 0.5, ring, 0.02);
        sl.playSound(null, pos, SoundEvents.WITHER_AMBIENT, SoundSource.NEUTRAL, 4f, 0.5f);
        sl.playSound(null, pos, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.NEUTRAL, 3f, 0.6f);
    }

    /**
     * The ring point on bearing {@code a} (x, z): {@link #WALL_MARGIN} blocks out from the target building's footprint
     * (the ray from the footprint's centre leaves the rectangle grown by the margin), and never nearer the centre than
     * {@link #RING}. A capitol is ringed at its walls rather than covered; with no building, a circle of RING.
     * Public for the game test.
     */
    public static double[] ringPoint(BlockPos pos, @Nullable BuildingPlacement target, double a) {
        double cos = Math.cos(a), sin = Math.sin(a);
        if (target == null)
            return new double[] { pos.getX() + 0.5 + cos * RING, pos.getZ() + 0.5 + sin * RING };
        // blocks minX..maxX cover [minX, maxX + 1)
        double cx = (target.minCorner.getX() + target.maxCorner.getX() + 1) / 2.0;
        double cz = (target.minCorner.getZ() + target.maxCorner.getZ() + 1) / 2.0;
        double hx = (target.maxCorner.getX() + 1 - target.minCorner.getX()) / 2.0 + WALL_MARGIN;
        double hz = (target.maxCorner.getZ() + 1 - target.minCorner.getZ()) / 2.0 + WALL_MARGIN;
        double t = Math.min(Math.abs(cos) < 1e-6 ? Double.MAX_VALUE : hx / Math.abs(cos),
                            Math.abs(sin) < 1e-6 ? Double.MAX_VALUE : hz / Math.abs(sin));
        double r = Math.max(t, RING);
        return new double[] { cx + cos * r, cz + sin * r };
    }

    /** Half the target's larger footprint side (the telegraph's spread). */
    static double halfSpan(BuildingPlacement b) {
        return Math.max(b.maxCorner.getX() + 1 - b.minCorner.getX(), b.maxCorner.getZ() + 1 - b.minCorner.getZ()) / 2.0;
    }

    /**
     * The cell a risen stands up in near column (x, z): the nearest one (UnitUnstick.findStandPos: rings out from the
     * point, going down cheaper than up) searched from {@code groundY} - the target's ground level, never the
     * heightmap - that a unit can stand in (land or a tidepool: not water, lava or fire), that fits a
     * {@code width} x {@code height} body with no lava or fire in or beside it (UnitUnstick.bodyFits), that is not in or
     * on any building ({@link #onBuilding}: no roofs, wall tops or courtyards), that no other risen took, and that a
     * unit can walk out of (UnitUnstick.isPocketed, last: the costly one). Null when nothing within
     * {@link #RISE_SEARCH_WIDE} qualifies. Once per risen per strike: a few hundred block reads each at worst.
     * <p>
     * Why not ProductionPlacement.safeSpawnPoint: it is built for a lab's door - it takes any standable cell where it
     * is asked (a roof is one) and checks lava only for wide bodies; the raise needs the footprint rule on every cell.
     */
    @Nullable
    BlockPos riseCell(ServerLevel sl, int x, int groundY, int z, float width, float height, Set<Long> taken) {
        BlockPos from = new BlockPos(x, groundY, z);
        java.util.function.Predicate<BlockPos> ok = c -> !taken.contains(c.asLong()) && !onBuilding(c)
            && !UnitUnstick.isPocketed(sl, c);
        BlockPos cell = UnitUnstick.findStandPos(sl, from, RISE_SEARCH, RISE_DOWN, RISE_UP, width, height, ok);
        if (cell == null)   // cramped ground round this point (water, a wall of buildings): look further out once
            cell = UnitUnstick.findStandPos(sl, from, RISE_SEARCH_WIDE, RISE_DOWN, RISE_UP, width, height, ok);
        return cell;
    }

    /** True if {@code e} is a Necropolis risen (same tag as the Bone Dragon's). */
    public static boolean isRisen(Entity e) {
        return DragonRaiseServerEvents.isRisen(e) && e.getPersistentData().contains(GameEnderServerEvents.KEY_EXPIRE);
    }
}
