package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The Heaven's Bell's life (HeavensBell; design/big_units.md 2.8).
 * <ul>
 *   <li><b>The toll costs energy</b> ({@link #payForStage}): every stage takes {@link #STAGE_ENERGY} energy up front;
 *   while the owner can't pay - an energy-stalled Stormcaller, whose winds were raided - the bell waits. The build price
 *   is 1200 energy under the Sun Spire's to make room for the three tolls.</li>
 *   <li><b>Heaven's storm</b> ({@link #strike} then the aftermath, {@link #STORM_TICKS}): a bolt every
 *   {@link #BOLT_EVERY} ticks within {@link #RADIUS} blocks of the target - on a random enemy unit there
 *   ({@link #UNIT_DAMAGE}), or, with no unit left to hit, on the nearest enemy building ({@link #BUILDING_DAMAGE}).
 *   Units first, so spreading out or walking away saves an army but leaves the base to the sky; a base with its army
 *   standing in it shields its buildings with their bodies. The bolts are visual-only lightning (no fire).</li>
 * </ul>
 */
public class HeavensBellPlacement extends GameEnderPlacement {

    public static final int STAGE_ENERGY = 400;
    public static final int RADIUS = 10;
    public static final int STORM_TICKS = 15 * 20;
    public static final int BOLT_EVERY = 10;
    public static final float UNIT_DAMAGE = 22f;
    public static final float BUILDING_DAMAGE = 60f;

    /** Bolts of the last storm on units / buildings (game test). */
    public int boltsOnUnits = 0, boltsOnBuildings = 0;

    private boolean toldBroke = false;
    @Nullable private BlockPos stormPos = null;
    private final List<LivingEntity> scratch = new ArrayList<>(), hits = new ArrayList<>();

    public HeavensBellPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                                ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return ParticleTypes.ELECTRIC_SPARK;
    }

    @Override
    protected boolean payForStage(ServerLevel sl, int stageIndex) {
        if (payUpfront(ownerName, 0, STAGE_ENERGY)) {
            toldBroke = false;
            sl.playSound(null, centrePos, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 3f, 0.5f);
            return true;
        }
        if (!toldBroke) {
            toldBroke = true;
            PlayerServerEvents.sendMessageToPlayer(ownerName, "server.reignofnether.heavens_bell_starved", false,
                displayName(), STAGE_ENERGY);
        }
        return false;
    }

    @Override
    protected void strike(ServerLevel sl, BlockPos pos, String victim) {
        stormPos = pos;
        aftermath = STORM_TICKS;
        boltsOnUnits = 0;
        boltsOnBuildings = 0;
        sl.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 12, pos.getZ() + 0.5, 60, RADIUS * 0.6, 0.6, RADIUS * 0.6, 0.01);
        sl.playSound(null, pos, SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 4f, 0.5f);
        sl.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6f, 0.6f);
    }

    /** The storm: a bolt every BOLT_EVERY ticks. Public for the game test. */
    @Override
    public void tickAftermath(ServerLevel sl) {
        if (stormPos == null)
            return;
        if (aftermath % 20 == 0)
            sl.sendParticles(ParticleTypes.CLOUD, stormPos.getX() + 0.5, stormPos.getY() + 12, stormPos.getZ() + 0.5, 20, RADIUS * 0.6, 0.4, RADIUS * 0.6, 0.01);
        if (aftermath % BOLT_EVERY == 0)
            bolt(sl);
        if (aftermath == 0)
            stormPos = null;
    }

    /** One bolt: a random enemy unit in the storm, else the nearest enemy building. Public for the game test. */
    public void bolt(ServerLevel sl) {
        if (stormPos == null)
            return;
        double cx = stormPos.getX() + 0.5, cz = stormPos.getZ() + 0.5;
        enemyUnitsNear(sl, cx, stormPos.getY(), cz, RADIUS, scratch, hits);
        if (!hits.isEmpty()) {
            LivingEntity le = hits.get(sl.random.nextInt(hits.size()));
            hits.clear();
            flash(sl, le.position());
            le.hurt(sl.damageSources().lightningBolt(), UNIT_DAMAGE);
            boltsOnUnits++;
            return;
        }
        BuildingPlacement best = null;
        double bestD = Double.MAX_VALUE;
        for (BuildingPlacement bp : enemyBuildingsNear(stormPos, RADIUS)) {
            double d = bp.centrePos.distSqr(stormPos);
            if (d < bestD) {
                bestD = d;
                best = bp;
            }
        }
        if (best == null)
            return;
        flash(sl, Vec3.atBottomCenterOf(best.centrePos));
        best.destroyRandomBlocks(BUILDING_DAMAGE);
        boltsOnBuildings++;
    }

    void flash(ServerLevel sl, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt == null)
            return;
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);   // the look and the thunder; the damage is ours (no fire, no charged creepers)
        sl.addFreshEntity(bolt);
    }
}
