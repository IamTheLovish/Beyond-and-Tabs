package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

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
 * The Sun Spire's strike (SunSpire; design/big_units.md 2.3): after the 5 s column (units can walk out of it, buildings
 * cannot), a lance of sunlight hits the spot - every enemy building whose centre is within {@link #RADIUS} blocks takes
 * {@link #BUILDING_DAMAGE}, every enemy unit within it {@link #UNIT_DAMAGE}. Friends and allies never. A building has no
 * entity to own the hit, so unit damage is generic (armour soaks it; not magic), like the Rail Battery's.
 */
public class SunSpirePlacement extends GameEnderPlacement {

    public static final int RADIUS = 10;
    public static final float BUILDING_DAMAGE = 400f;
    public static final float UNIT_DAMAGE = 150f;

    /** What the last strike hit (game test). */
    public int lastUnitsHit = 0, lastBuildingsHit = 0;

    private final List<LivingEntity> scratch = new ArrayList<>();

    public SunSpirePlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                             ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return ParticleTypes.END_ROD;
    }

    @Override
    protected void strike(ServerLevel sl, BlockPos pos, String victim) {
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5, r2 = (double) RADIUS * RADIUS;
        int hits = 0;
        for (LivingEntity le : UnitGrid.near(sl, cx, cz, RADIUS, scratch)) {
            if (!le.isAlive() || le.level() != sl || !(le instanceof Unit u) || !isEnemy(u.getOwnerName()))
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz;
            if (dx * dx + dz * dz > r2 || Math.abs(le.getY() - pos.getY()) > RADIUS)
                continue;
            le.hurt(sl.damageSources().generic(), UNIT_DAMAGE);
            hits++;
        }
        scratch.clear();
        lastUnitsHit = hits;
        // buildings: collect first - a hit can destroy one, and destruction edits the server's list
        List<BuildingPlacement> targets = new ArrayList<>();
        for (BuildingPlacement bp : pool != null ? pool : BuildingServerEvents.getBuildings()) {
            if (bp == this || bp.isDestroyedServerside || !isEnemy(bp.ownerName))
                continue;
            double dx = bp.centrePos.getX() - pos.getX(), dz = bp.centrePos.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= r2)
                targets.add(bp);
        }
        for (BuildingPlacement bp : targets)
            bp.destroyRandomBlocks(BUILDING_DAMAGE);
        lastBuildingsHit = targets.size();
        // the look: a white-gold shaft from the sky, a flash and a ring of embers on the ground
        for (int y = 0; y < 40; y += 2)
            sl.sendParticles(ParticleTypes.END_ROD, cx, pos.getY() + y, cz, 3, 0.6, 0.2, 0.6, 0.02);
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16;
            sl.sendParticles(ParticleTypes.FLAME, cx + Math.cos(a) * RADIUS, pos.getY() + 1, cz + Math.sin(a) * RADIUS, 2, 0.2, 0.1, 0.2, 0.01);
        }
        sl.sendParticles(ParticleTypes.FLASH, cx, pos.getY() + 2, cz, 3, 1, 1, 1, 0);
        sl.playSound(null, pos, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 4f, 0.6f);
        sl.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 4f, 0.5f);
    }
}
