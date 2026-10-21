package com.solegendary.reignofnether.building.buildings.defence;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;

/**
 * A wall segment (design/defences.md; BAR's dragon's teeth / fortification wall): 2x2 blocks of faction wall, three high
 * (a stone rampart, a bone palisade, a thorn hedge, a coral bulwark, a jade wall, brass plate, a skull palisade - see
 * tools/defence_structures.py). Laid in contiguous lines with Shift+drag (BuildingLineClientEvents: walls step one
 * footprint with no gap and follow the drag cell by cell, so a diagonal line has no corner gaps). Units path around it:
 * the blocks are real, and the top course is a wall/fence block the pathfinder never stands on.
 * <p>
 * Cheap and tough, so it is a speed bump, not a fortress: ordinary units never auto-target a wall (they walk round),
 * but siege (a unit with a building-damage bonus), heavies and T3s do, and hit it {@link #SIEGE_MULT} /
 * {@link #T3_MULT} times as hard (WallPlacement). Reclaimable like any building (BuildingReclaim): workers take it back
 * for part of its metal.
 */
public class WallSegment extends DefenceBuilding {

    /** A heavy (or a siege unit: any building-damage bonus) hits a wall this many times as hard. */
    public static final float SIEGE_MULT = 3f;
    /** An experimental hits a wall this many times as hard: a T3 walks through a wall line. */
    public static final float T3_MULT = 6f;
    /** A unit worth at least this much metal counts as a heavy (T2 tanks and up). */
    public static final int HEAVY_METAL = 250;
    /** ... and at least this much as an experimental. */
    public static final int T3_METAL = 900;

    /** The fortified wall's health (lovish Oct 21: enhanced T1 defences "kinda like in bar"): heavies take longer. */
    public static final double FORTIFIED_HEALTH = 1100d;

    /** Fortified (a T1 lab's wall): same footprint, so it mixes into a plain wall line. */
    public final boolean fortified;

    public WallSegment(String id, String displayName, DefenceStyle style) {
        this(id, displayName, style, null);
    }

    /** @param t1Labs non-null: the fortified wall, waiting for any of these T1 labs */
    public WallSegment(String id, String displayName, DefenceStyle style,
                       @javax.annotation.Nullable java.util.function.Supplier<java.util.List<com.solegendary.reignofnether.building.Building>> t1Labs) {
        super(id, displayName, style, t1Labs != null ? DefenceCosts.WALL_FORTIFIED : DefenceCosts.WALL);
        this.fortified = t1Labs != null;
        this.anyOfLabs = t1Labs;
        this.portraitBlock = style.wallPortrait();
        this.maxHealth = fortified ? FORTIFIED_HEALTH : 400d;
        this.buildTimeModifier = 0.6f;
        this.explodeChance = 0f;   // a wall crumbles, it does not blow up
        this.drawAggro = false;     // see drawsAggroFrom
    }

    /** Whether this unit auto-targets walls: siege, heavies and experimentals only (MiscUtil.isBuildingAutoAttackable). */
    public static boolean drawsAggroFrom(Mob attacker) {
        return damageMultFrom(attacker) > 1f;
    }

    /** How much harder than its normal building damage this attacker hits a wall. */
    public static float damageMultFrom(Entity attacker) {
        if (!(attacker instanceof Unit u))
            return 1f;
        int metal = u.getCost() == null ? 0 : u.getCost().metal();
        if (metal >= T3_METAL)
            return T3_MULT;
        if (metal >= HEAVY_METAL || (attacker instanceof AttackerUnit au && au.getBuildingDamageBonus() > 0)
                || (attacker instanceof LivingEntity le && le.getBbWidth() >= 1.4f))
            return SIEGE_MULT;
        return 1f;
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new WallPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    /** A wall's (and a gate's) placement: only the siege multiplier differs from a plain building. */
    public static class WallPlacement extends BuildingPlacement {
        public WallPlacement(DefenceBuilding building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                             ArrayList<BuildingBlock> blocks, boolean isCapitol) {
            super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
        }

        @Override
        public void destroyRandomBlocks(double amount, Entity attacker) {
            super.destroyRandomBlocks(amount * damageMultFrom(attacker), attacker);
        }
    }
}
