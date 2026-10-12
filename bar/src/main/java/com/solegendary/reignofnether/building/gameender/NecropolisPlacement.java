package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.DragonRaiseServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The Necropolis's strike (Necropolis; design/big_units.md 2.4): <b>mass raise</b>. {@link #BASE_RISEN} undead
 * (zombies and skeletons, alternating) claw up in a ring round the target for the Necropolis's owner, plus one for every
 * wreck within {@link #WRECK_RADIUS} blocks of it (the wreck is spent; at most {@link #MAX_WRECK_BONUS}) - "every wreck
 * is an army". The risen carry the Bone Dragon's risen tag (DragonRaiseServerEvents.TAG: no wreck, no free metal) and
 * crumble after {@link #LIFETIME_TICKS} (GameEnderServerEvents sweeps them). No population check: the host is what the
 * player paid an army's worth of metal for.
 */
public class NecropolisPlacement extends GameEnderPlacement {

    public static final int BASE_RISEN = 10;
    public static final int MAX_WRECK_BONUS = 8;
    public static final double WRECK_RADIUS = 16;
    public static final int LIFETIME_TICKS = 45 * 20;
    static final double RING = 5.0;

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
        for (int i = 0; i < total; i++) {
            double a = i * Math.PI * 2 / total;
            int x = (int) Math.floor(pos.getX() + 0.5 + Math.cos(a) * RING);
            int z = (int) Math.floor(pos.getZ() + 0.5 + Math.sin(a) * RING);
            int y = Math.max(pos.getY(), sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1);
            var type = i % 2 == 0 ? EntityRegistrar.ZOMBIE_UNIT.get() : EntityRegistrar.SKELETON_UNIT.get();
            Entity e = UnitServerEvents.spawnMob(type, sl, new BlockPos(x, y, z), ownerName);
            if (e == null)
                continue;
            GameEnderServerEvents.markRisen(e, expire);
            lastRisen.add(e);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + 0.2, e.getZ(), 10, 0.3, 0.1, 0.3, 0.02);
        }
        sl.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, RING, 0.5, RING, 0.02);
        sl.playSound(null, pos, SoundEvents.WITHER_AMBIENT, SoundSource.NEUTRAL, 4f, 0.5f);
        sl.playSound(null, pos, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.NEUTRAL, 3f, 0.6f);
    }

    /** True if {@code e} is a Necropolis risen (same tag as the Bone Dragon's). */
    public static boolean isRisen(Entity e) {
        return DragonRaiseServerEvents.isRisen(e) && e.getPersistentData().contains(GameEnderServerEvents.KEY_EXPIRE);
    }
}
