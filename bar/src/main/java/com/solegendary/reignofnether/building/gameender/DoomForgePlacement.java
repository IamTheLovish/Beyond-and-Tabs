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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * The Doom Forge's life (DoomForge; design/big_units.md 2.5).
 * <ul>
 *   <li><b>Stoke</b> ({@link #payForStage}): every stage burns {@link #STOKE_METAL} metal up front; while the owner
 *   can't pay the forge waits (and tells its owner once). BAR's lens: the commando strike on the economy that feeds a
 *   super-weapon. The build price is a thousand metal under the Sun Spire's to make room for the three stokes.</li>
 *   <li><b>Meteor</b> ({@link #strike}): every enemy building with its centre within {@link #RADIUS} blocks takes
 *   {@link #BUILDING_DAMAGE}, every enemy unit {@link #UNIT_DAMAGE} and is thrown out from the impact.</li>
 *   <li><b>Burning crater</b> (aftermath, {@link #CRATER_TICKS}): once a second enemy units in the crater take
 *   {@link #CRATER_UNIT_DPS} and enemy buildings {@link #CRATER_BUILDING_DPS} - a rebuilt base burns again. Fire is
 *   particles, never fire blocks: the map must not burn down round the strike.</li>
 * </ul>
 * Unit damage is generic (a building owns the hit), like the Sun Spire's.
 */
public class DoomForgePlacement extends GameEnderPlacement {

    public static final int RADIUS = 8;
    public static final float BUILDING_DAMAGE = 500f;
    public static final float UNIT_DAMAGE = 120f;
    public static final int CRATER_TICKS = 15 * 20;
    public static final float CRATER_UNIT_DPS = 6f;
    public static final float CRATER_BUILDING_DPS = 20f;
    public static final int STOKE_METAL = 350;

    /** What the last impact hit (game test). */
    public int lastUnitsHit = 0, lastBuildingsHit = 0;
    /** True once the owner has been told the forge is waiting for metal this stage (one message, not one a second). */
    private boolean toldBroke = false;
    private BlockPos craterPos = null;

    private final List<LivingEntity> scratch = new ArrayList<>(), hits = new ArrayList<>();

    public DoomForgePlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                              ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return ParticleTypes.LAVA;
    }

    @Override
    protected boolean payForStage(ServerLevel sl, int stageIndex) {
        if (payUpfront(ownerName, STOKE_METAL, 0)) {
            toldBroke = false;
            sl.playSound(null, centrePos, SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 1.5f, 0.5f);
            return true;
        }
        if (!toldBroke) {
            toldBroke = true;
            PlayerServerEvents.sendMessageToPlayer(ownerName, "server.reignofnether.doom_forge_starved", false,
                displayName(), STOKE_METAL);
        }
        return false;
    }

    @Override
    protected void strike(ServerLevel sl, BlockPos pos, String victim) {
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        enemyUnitsNear(sl, cx, pos.getY(), cz, RADIUS, scratch, hits);
        for (LivingEntity le : hits) {
            le.hurt(sl.damageSources().generic(), UNIT_DAMAGE);
            double dx = le.getX() - cx, dz = le.getZ() - cz, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            le.push(dx / d * 1.2, 0.6, dz / d * 1.2);   // thrown out of the crater
            le.hurtMarked = true;
        }
        lastUnitsHit = hits.size();
        hits.clear();
        List<BuildingPlacement> targets = enemyBuildingsNear(pos, RADIUS);
        for (BuildingPlacement bp : targets)
            bp.destroyRandomBlocks(BUILDING_DAMAGE);
        lastBuildingsHit = targets.size();
        craterPos = pos;
        aftermath = CRATER_TICKS;
        // the look: a falling streak, the flash, a ring of lava sparks and a smoke column
        for (int y = 40; y > 0; y -= 2)
            sl.sendParticles(ParticleTypes.FLAME, cx, pos.getY() + y, cz, 2, 0.3, 0.3, 0.3, 0.01);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, pos.getY() + 1, cz, 2, 1.5, 0.5, 1.5, 0);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            sl.sendParticles(ParticleTypes.LAVA, cx + Math.cos(a) * RADIUS * 0.7, pos.getY() + 1, cz + Math.sin(a) * RADIUS * 0.7, 2, 0.3, 0.2, 0.3, 0);
        }
        sl.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 6f, 0.4f);
        sl.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 3f, 0.5f);
    }

    /** The burning crater: once a second, enemy units and buildings in it burn. Public for the game test. */
    @Override
    public void tickAftermath(ServerLevel sl) {
        if (craterPos == null)
            return;
        double cx = craterPos.getX() + 0.5, cz = craterPos.getZ() + 0.5;
        if (aftermath % 5 == 0)
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, cx, craterPos.getY() + 1, cz, 3, RADIUS * 0.4, 0.3, RADIUS * 0.4, 0.01);
        if (aftermath % 20 != 0)
            return;
        sl.sendParticles(ParticleTypes.FLAME, cx, craterPos.getY() + 0.3, cz, 12, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.01);
        for (LivingEntity le : enemyUnitsNear(sl, cx, craterPos.getY(), cz, RADIUS, scratch, hits))
            le.hurt(sl.damageSources().inFire(), CRATER_UNIT_DPS);
        hits.clear();
        for (BuildingPlacement bp : enemyBuildingsNear(craterPos, RADIUS))
            bp.destroyRandomBlocks(CRATER_BUILDING_DPS);
        if (aftermath == 0)
            craterPos = null;
    }
}
