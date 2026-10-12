package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
 * The Earthcracker's life (Earthcracker; design/big_units.md 2.9).
 * <ul>
 *   <li><b>The drill runs hot</b>: after each stage below the last it vents for {@link #VENT_TICKS} - no charge
 *   ({@link #chargeRate} 0), and raid damage counts {@link #VENT_RAID_MULT}x toward the knock-back
 *   ({@link #onRaided}). Every vent is announced to every player: the Brassworks' Heat as a scheduled raid window.</li>
 *   <li><b>Fault line</b> ({@link #strike} then the aftermath): a fault {@link #FAULT_LENGTH} blocks long and
 *   {@link #HALF_WIDTH} x 2 wide opens through the target along the drill's bearing (engine to target), racing from end
 *   to end over {@link #RUPTURE_TICKS}. Every enemy building it crosses takes {@link #BUILDING_DAMAGE}, every enemy
 *   unit it passes under {@link #UNIT_DAMAGE} and is thrown up - once each. A line, not a disc: a capitol on it is
 *   wrecked, a building ten blocks to the side is untouched, and the line is drawn for the 5 s telegraph so units can
 *   step off it.</li>
 * </ul>
 */
public class EarthcrackerPlacement extends GameEnderPlacement {

    public static final int VENT_TICKS = 12 * 20;
    public static final float VENT_RAID_MULT = 2f;
    public static final int FAULT_LENGTH = 32;
    public static final double HALF_WIDTH = 1.5;
    public static final int RUPTURE_TICKS = 3 * 20;
    public static final float BUILDING_DAMAGE = 450f;
    public static final float UNIT_DAMAGE = 110f;

    /** Ticks left of the current vent (0 = drilling). Public for the game test. */
    public int vent = 0;
    /** What the last fault hit (game test). */
    public int lastUnitsHit = 0, lastBuildingsHit = 0;

    @Nullable private BlockPos faultCentre = null;
    private double dirX = 1, dirZ = 0;
    private final IntOpenHashSet unitsHit = new IntOpenHashSet();
    private final List<BuildingPlacement> faultBuildings = new ArrayList<>();
    private final List<Double> faultAlong = new ArrayList<>();
    private final List<LivingEntity> scratch = new ArrayList<>(), hits = new ArrayList<>();

    public EarthcrackerPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                                 ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return ParticleTypes.CAMPFIRE_COSY_SMOKE;
    }

    @Override
    protected void onStageComplete(ServerLevel sl, int completedStage) {
        vent = VENT_TICKS;
        PlayerServerEvents.sendMessageToAllPlayers("server.reignofnether.earthcracker_venting", false,
            ownerName, displayName(), VENT_TICKS / 20);
        sl.playSound(null, centrePos, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 3f, 0.5f);
    }

    @Override
    protected float chargeRate(ServerLevel sl) {
        if (vent > 0) {
            vent--;
            if (vent % 10 == 0)
                sl.sendParticles(ParticleTypes.CLOUD, centrePos.getX() + 0.5, maxCorner.getY() + 1, centrePos.getZ() + 0.5, 6, 0.8, 0.4, 0.8, 0.05);
            return 0f;
        }
        return 1f;
    }

    /** A venting drill is exposed: raid damage counts double toward the knock-back. */
    @Override
    public void onRaided(int damage) {
        super.onRaided(vent > 0 ? Math.round(damage * VENT_RAID_MULT) : damage);
    }

    /** The telegraph draws the fault line itself, not just a column: units on it can step aside. */
    @Override
    public void aimAt(ServerLevel sl, BlockPos pos, String victim) {
        setBearing(pos);
        vent = 0;
        super.aimAt(sl, pos, victim);
    }

    @Override
    void drawColumn(ServerLevel sl) {
        super.drawColumn(sl);
        if (strikePos != null)
            drawFault(sl, strikePos, FAULT_LENGTH * 0.5);
    }

    void setBearing(BlockPos pos) {
        double dx = pos.getX() - centrePos.getX(), dz = pos.getZ() - centrePos.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.5) {
            dirX = 1;
            dirZ = 0;
        } else {
            dirX = dx / d;
            dirZ = dz / d;
        }
    }

    @Override
    protected void strike(ServerLevel sl, BlockPos pos, String victim) {
        setBearing(pos);
        faultCentre = pos;
        unitsHit.clear();
        faultBuildings.clear();
        faultAlong.clear();
        lastUnitsHit = 0;
        lastBuildingsHit = 0;
        double half = FAULT_LENGTH * 0.5;
        for (BuildingPlacement bp : pool != null ? pool : BuildingServerEvents.getBuildings()) {
            if (bp == this || bp.isDestroyedServerside || !isEnemy(bp.ownerName))
                continue;
            double rx = bp.centrePos.getX() - pos.getX(), rz = bp.centrePos.getZ() - pos.getZ();
            double along = rx * dirX + rz * dirZ, across = Math.abs(rx * -dirZ + rz * dirX);
            double ext = Math.max(bp.maxCorner.getX() - bp.minCorner.getX(), bp.maxCorner.getZ() - bp.minCorner.getZ()) * 0.5;
            if (Math.abs(along) <= half + ext && across <= HALF_WIDTH + ext) {
                faultBuildings.add(bp);
                faultAlong.add(along);
            }
        }
        aftermath = RUPTURE_TICKS;
        sl.playSound(null, pos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.NEUTRAL, 4f, 0.5f);
        sl.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 4f, 0.5f);
    }

    /** The crack races from one end to the other; whatever the front passes is hit once. Public for the game test. */
    @Override
    public void tickAftermath(ServerLevel sl) {
        if (faultCentre == null)
            return;
        double half = FAULT_LENGTH * 0.5;
        double front = -half + FAULT_LENGTH * (double) (RUPTURE_TICKS - aftermath) / RUPTURE_TICKS;
        double fx = faultCentre.getX() + 0.5 + dirX * front, fz = faultCentre.getZ() + 0.5 + dirZ * front;
        double reach = HALF_WIDTH + 1.5;
        for (LivingEntity le : enemyUnitsNear(sl, fx, faultCentre.getY(), fz, reach + 1, scratch, hits)) {
            if (unitsHit.contains(le.getId()))
                continue;
            double rx = le.getX() - (faultCentre.getX() + 0.5), rz = le.getZ() - (faultCentre.getZ() + 0.5);
            if (Math.abs(rx * -dirZ + rz * dirX) > HALF_WIDTH + le.getBbWidth() * 0.5)
                continue;
            unitsHit.add(le.getId());
            le.hurt(sl.damageSources().generic(), UNIT_DAMAGE);
            le.push(0, 0.8, 0);
            le.hurtMarked = true;
            lastUnitsHit++;
        }
        hits.clear();
        for (int i = faultBuildings.size() - 1; i >= 0; i--) {
            if (faultAlong.get(i) > front)
                continue;
            BuildingPlacement bp = faultBuildings.remove(i);
            faultAlong.remove(i);
            if (!bp.isDestroyedServerside)
                bp.destroyRandomBlocks(BUILDING_DAMAGE);
            lastBuildingsHit++;
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, fx, faultCentre.getY() + 0.5, fz, 1, 0.5, 0.2, 0.5, 0);
        sl.sendParticles(ParticleTypes.LAVA, fx, faultCentre.getY() + 0.3, fz, 2, HALF_WIDTH * 0.5, 0.1, HALF_WIDTH * 0.5, 0);
        if (aftermath == 0) {
            // the front's last step may round short of the far end: everything left on the line is hit now
            for (BuildingPlacement bp : faultBuildings) {
                if (!bp.isDestroyedServerside)
                    bp.destroyRandomBlocks(BUILDING_DAMAGE);
                lastBuildingsHit++;
            }
            faultBuildings.clear();
            faultAlong.clear();
            faultCentre = null;
        }
    }

    /** The telegraph: a dotted crack along the bearing through the target. */
    void drawFault(ServerLevel sl, BlockPos pos, double half) {
        for (double d = -half; d <= half; d += 2)
            sl.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5 + dirX * d, pos.getY() + 0.3, pos.getZ() + 0.5 + dirZ * d, 1, 0.2, 0, 0.2, 0);
    }
}
