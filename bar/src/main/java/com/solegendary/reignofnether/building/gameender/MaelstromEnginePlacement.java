package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Maelstrom Engine's life (MaelstromEngine; design/big_units.md 2.7).
 * <ul>
 *   <li><b>Flood ring</b>: once built it floods {@link #FLOOD_POOLS} tidepools in a ring round itself
 *   ({@link #flood}; TidepoolServerEvents.raiseForBuilding: its own cap, never pushing out the owner's other pools) and
 *   charges at (live, undrained pools / {@link #FLOOD_POOLS}) of the normal rate ({@link #chargeRate}). Draining a pool
 *   - any constructor's reclaim order on it, the Tides counterplay that already exists - slows it; all three drained
 *   stops it. A drained pool comes back only at the next re-flood ({@link #REFLOOD_TICKS}), so a raider who keeps
 *   draining keeps it slow. Fire evaporates pools too.</li>
 *   <li><b>Whirlpool</b> ({@link #strike} then the aftermath, {@link #WHIRL_TICKS}): every enemy unit within
 *   {@link #RADIUS} blocks of the target is dragged toward the centre and swirled round it, and once a second takes
 *   {@link #UNIT_DPS}; enemy buildings there take {@link #BUILDING_DPS} a second (flooded). Units can still fight their
 *   way out against the pull; buildings can't.</li>
 * </ul>
 */
public class MaelstromEnginePlacement extends GameEnderPlacement {

    public static final int FLOOD_POOLS = 3;
    public static final int FLOOD_RADIUS = 1;
    public static final int FLOOD_LIFETIME_TICKS = 10 * 60 * 20;
    public static final int REFLOOD_TICKS = 15 * 20;
    public static final int RADIUS = 9;
    public static final int WHIRL_TICKS = 8 * 20;
    public static final float UNIT_DPS = 10f;
    public static final float BUILDING_DPS = 45f;
    static final double PULL = 0.22, SWIRL = 0.18;

    /** The flood ring (null where a raise was refused or not yet tried). Public for the game test. */
    public final TidepoolServerEvents.Pool[] floodPools = new TidepoolServerEvents.Pool[FLOOD_POOLS];
    /** What the last whirlpool second did (game test). */
    public int lastUnitsHurt = 0, lastBuildingsHit = 0;

    private int reflood = 0;
    @Nullable private BlockPos whirlPos = null;
    private final List<LivingEntity> scratch = new ArrayList<>(), hits = new ArrayList<>();

    public MaelstromEnginePlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                                    ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return ParticleTypes.BUBBLE_POP;
    }

    /** Live pools of the ring that are not draining. */
    public int livePools() {
        int n = 0;
        for (TidepoolServerEvents.Pool p : floodPools)
            if (p != null && p.isActive() && !p.isDraining())
                n++;
        return n;
    }

    /** Re-raises every missing or dried pool of the ring. Public for the game test. */
    public void flood(ServerLevel sl) {
        double half = Math.max(maxCorner.getX() - minCorner.getX(), maxCorner.getZ() - minCorner.getZ()) * 0.5;
        double ring = half + 2.5;
        for (int i = 0; i < FLOOD_POOLS; i++) {
            TidepoolServerEvents.Pool p = floodPools[i];
            if (p != null && p.isActive() && !p.isDraining())
                continue;
            double a = Math.PI * 2 * i / FLOOD_POOLS + Math.PI / 6;
            BlockPos at = new BlockPos((int) Math.floor(centrePos.getX() + 0.5 + Math.cos(a) * ring), minCorner.getY(),
                (int) Math.floor(centrePos.getZ() + 0.5 + Math.sin(a) * ring));
            floodPools[i] = TidepoolServerEvents.raiseForBuilding(sl, ownerName, originPos, at, FLOOD_RADIUS, FLOOD_LIFETIME_TICKS);
        }
    }

    /** Public for the game test: live, undrained pools / FLOOD_POOLS (re-flooding first when it is time). */
    @Override
    public float chargeRate(ServerLevel sl) {
        if (--reflood <= 0) {
            reflood = REFLOOD_TICKS;
            flood(sl);
        }
        return (float) livePools() / FLOOD_POOLS;
    }

    /** The ring dries with the engine (its pools would otherwise stand for their ten-minute lifetime). */
    @Override
    public void destroy(ServerLevel serverLevel) {
        for (int i = 0; i < FLOOD_POOLS; i++) {
            if (floodPools[i] != null)
                TidepoolServerEvents.drain(floodPools[i]);
            floodPools[i] = null;
        }
        super.destroy(serverLevel);
    }

    @Override
    protected void strike(ServerLevel sl, BlockPos pos, String victim) {
        whirlPos = pos;
        aftermath = WHIRL_TICKS;
        sl.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 80, RADIUS * 0.5, 0.5, RADIUS * 0.5, 0.2);
        sl.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.NEUTRAL, 3f, 0.5f);
        sl.playSound(null, pos, SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.NEUTRAL, 4f, 0.6f);
    }

    /** The whirlpool: a pull every 4 ticks, the damage once a second. Public for the game test. */
    @Override
    public void tickAftermath(ServerLevel sl) {
        if (whirlPos == null)
            return;
        double cx = whirlPos.getX() + 0.5, cz = whirlPos.getZ() + 0.5;
        if (aftermath % 4 == 0) {
            for (LivingEntity le : enemyUnitsNear(sl, cx, whirlPos.getY(), cz, RADIUS, scratch, hits)) {
                double dx = cx - le.getX(), dz = cz - le.getZ(), d = Math.max(0.6, Math.sqrt(dx * dx + dz * dz));
                // inward plus a turn of the gyre: the Gravity Monk's pull with a swirl
                le.push(dx / d * PULL + dz / d * SWIRL, 0.02, dz / d * PULL - dx / d * SWIRL);
                le.hurtMarked = true;
            }
            hits.clear();
            for (int i = 0; i < 6; i++) {
                double a = (aftermath * 0.3) + i * Math.PI / 3, r = RADIUS * (0.3 + 0.12 * i);
                sl.sendParticles(ParticleTypes.SPLASH, cx + Math.cos(a) * r, whirlPos.getY() + 1, cz + Math.sin(a) * r, 3, 0.2, 0.1, 0.2, 0.05);
            }
        }
        if (aftermath % 20 == 0)
            whirl(sl);
        if (aftermath == 0)
            whirlPos = null;
    }

    /** One second of the whirlpool's damage. Public for the game test. */
    public void whirl(ServerLevel sl) {
        if (whirlPos == null)
            return;
        double cx = whirlPos.getX() + 0.5, cz = whirlPos.getZ() + 0.5;
        lastUnitsHurt = 0;
        for (LivingEntity le : enemyUnitsNear(sl, cx, whirlPos.getY(), cz, RADIUS, scratch, hits)) {
            le.hurt(sl.damageSources().drown(), UNIT_DPS);
            lastUnitsHurt++;
        }
        hits.clear();
        List<BuildingPlacement> targets = enemyBuildingsNear(whirlPos, RADIUS);
        for (BuildingPlacement bp : targets)
            bp.destroyRandomBlocks(BUILDING_DPS);
        lastBuildingsHit = targets.size();
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, cx, whirlPos.getY() + 0.5, cz, 20, RADIUS * 0.4, 0.2, RADIUS * 0.4, 0.1);
    }
}
