package com.solegendary.reignofnether.building.gameender;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The World Seed's strike (WorldSeed; design/big_units.md 2.6): <b>Overgrowth</b>. The seed lands as a
 * <b>Heartroot</b> - a rooted Sentinel Treant body with {@link #HEARTROOT_HP} HP that cannot move - at the target, and for
 * as long as it lives (at most {@link #OVERGROWTH_TICKS}) the ground within {@link #RADIUS} blocks of it is grove: once a
 * second every enemy building there takes {@link #BUILDING_DPS}, every enemy unit {@link #UNIT_DPS} and is slowed, every
 * Court (own or allied) unit heals {@link #FRIEND_HEAL}. A damage-over-time strike rather than a burst: fully grown it
 * does more than the Sun Spire's lance (20 x 25 = 500 a building), and every second the defender spends killing the
 * Heartroot takes 25 off that. The Heartroot carries the risen tag (no wreck, no kill credit) and crumbles at the end.
 */
public class WorldSeedPlacement extends GameEnderPlacement {

    public static final int RADIUS = 12;
    public static final int OVERGROWTH_TICKS = 20 * 20;
    public static final float BUILDING_DPS = 25f;
    public static final float UNIT_DPS = 4f;
    public static final float FRIEND_HEAL = 3f;
    public static final float HEARTROOT_HP = 400f;

    /** The live Heartroot of the last strike, or null (game test). */
    @Nullable public LivingEntity heartroot = null;
    /** What the last overgrowth second did (game test). */
    public int lastFoesHurt = 0, lastFriendsHealed = 0, lastBuildingsHit = 0;

    private final List<LivingEntity> scratch = new ArrayList<>(), hits = new ArrayList<>();

    public WorldSeedPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                              ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
    }

    @Override
    protected ParticleOptions chargeParticle() {
        return ParticleTypes.HAPPY_VILLAGER;
    }

    @Override
    protected void strike(ServerLevel sl, BlockPos pos, String victim) {
        if (heartroot != null && heartroot.isAlive())
            heartroot.discard();   // a second strike replaces the first grove (never two Heartroots per seed)
        int y = Math.max(pos.getY(), sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1);
        Entity e = UnitServerEvents.spawnMob(EntityRegistrar.SENTINEL_TREANT_UNIT.get(), sl, new BlockPos(pos.getX(), y, pos.getZ()), ownerName);
        heartroot = e instanceof LivingEntity le ? le : null;
        if (heartroot == null)
            return;
        AttributeInstance hp = heartroot.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null)
            hp.setBaseValue(HEARTROOT_HP);
        heartroot.setHealth(HEARTROOT_HP);
        AttributeInstance speed = heartroot.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
            speed.setBaseValue(0);   // rooted: it fights where it landed
        heartroot.setCustomName(net.minecraft.network.chat.Component.translatable("units.reignofnether.heartroot"));
        GameEnderServerEvents.markRisen(heartroot, sl.getGameTime() + OVERGROWTH_TICKS);
        aftermath = OVERGROWTH_TICKS;
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.5, y + 2, pos.getZ() + 0.5, 60, RADIUS * 0.5, 1, RADIUS * 0.5, 0);
        sl.sendParticles(ParticleTypes.COMPOSTER, pos.getX() + 0.5, y + 1, pos.getZ() + 0.5, 40, 1.5, 1.5, 1.5, 0.05);
        sl.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 4f, 0.5f);
        sl.playSound(null, pos, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.NEUTRAL, 4f, 0.6f);
    }

    /** True while the last strike's Heartroot stands (the grove lives with it). */
    public boolean groveAlive() {
        return heartroot != null && heartroot.isAlive() && !heartroot.isRemoved();
    }

    /** The grove: once a second while the Heartroot lives. Ends at once when it dies. Public for the game test. */
    @Override
    public void tickAftermath(ServerLevel sl) {
        if (!groveAlive()) {
            aftermath = 0;
            heartroot = null;
            return;
        }
        if (aftermath % 20 != 0)
            return;
        overgrow(sl);
    }

    /** One second of the grove round the Heartroot. Public for the game test. */
    public void overgrow(ServerLevel sl) {
        if (!groveAlive())
            return;
        double cx = heartroot.getX(), cy = heartroot.getY(), cz = heartroot.getZ();
        lastFoesHurt = 0;
        lastFriendsHealed = 0;
        for (LivingEntity le : enemyUnitsNear(sl, cx, cy, cz, RADIUS, scratch, hits)) {
            le.hurt(sl.damageSources().generic(), UNIT_DPS);   // roots and thorns (cactus damage is cancelled for units)
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));
            lastFoesHurt++;
        }
        hits.clear();
        double r2 = (double) RADIUS * RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, cx, cz, RADIUS, scratch)) {
            if (le == heartroot || !le.isAlive() || le.level() != sl || !(le instanceof Unit u) || le.getHealth() >= le.getMaxHealth())
                continue;
            String o = u.getOwnerName();
            if (o == null || (!o.equals(ownerName) && !AlliancesServerEvents.isAllied(ownerName, o)))
                continue;
            if (le.distanceToSqr(cx, le.getY(), cz) > r2)
                continue;
            le.heal(FRIEND_HEAL);
            lastFriendsHealed++;
        }
        scratch.clear();
        List<BuildingPlacement> targets = enemyBuildingsNear(heartroot.blockPosition(), RADIUS);
        for (BuildingPlacement bp : targets)
            bp.destroyRandomBlocks(BUILDING_DPS);
        lastBuildingsHit = targets.size();
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, cx, cy + 1.5, cz, 16, RADIUS * 0.5, 0.6, RADIUS * 0.5, 0);
        sl.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, cx, cy + 4, cz, 4, 1, 0.5, 1, 0);
    }
}
