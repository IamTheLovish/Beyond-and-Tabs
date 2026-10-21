package com.solegendary.reignofnether.building.merge;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One merged building (claude/merged-buildings.md 2d): a shape over a block of same-type, same-owner members that
 * stand edge to edge. The members stay what they were - each is a <b>section</b> with its own blocks and its own
 * health, so a hit lands on the section under it and splash covers the sections it touches - and this object is the
 * thing the player sees as one: its shape, its summed output bonus, its footprint, and the state the animations read
 * (claude/merged-buildings-animation.md: Lane A owns the state, Lane B draws it):
 * <ul>
 *   <li>{@link #formedAt}: the tick the complex stood (raising lasts {@link MergedBuildings#RAISING_TICKS} from
 *       here; {@link #isRaising});</li>
 *   <li>{@link #previousShape}: the shape it was before this one, when it shrank after losing a section or grew when
 *       one was rebuilt - null for a complex that formed from separate buildings;</li>
 *   <li>{@link #lostSections}: the footprints (min corners) of the sections that fell when it shrank - where the
 *       collapse plays and the ruin a rebuild re-joins.</li>
 * </ul>
 * Members are in row order (z, then x) of the grid, so {@code members.get(0)} is the north-west section.
 */
public final class MergedComplex {
    public final MergedShape shape;
    public final List<BuildingPlacement> members;
    public final String ownerName;
    public final Building building;
    /** The footprint of all members (x / z from the members' corners; y from the lowest to the highest block). */
    public final BlockPos minCorner, maxCorner;
    public final long formedAt;
    public final MergedShape previousShape;
    public final List<BlockPos> lostSections;

    MergedComplex(MergedShape shape, List<BuildingPlacement> members, long formedAt, MergedShape previousShape,
                  List<BlockPos> lostSections) {
        this.shape = shape;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
        this.ownerName = members.get(0).ownerName;
        this.building = members.get(0).getBuilding();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BuildingPlacement p : members) {
            minX = Math.min(minX, p.minCorner.getX());
            minY = Math.min(minY, p.minCorner.getY());
            minZ = Math.min(minZ, p.minCorner.getZ());
            maxX = Math.max(maxX, p.maxCorner.getX());
            maxY = Math.max(maxY, p.maxCorner.getY());
            maxZ = Math.max(maxZ, p.maxCorner.getZ());
        }
        this.minCorner = new BlockPos(minX, minY, minZ);
        this.maxCorner = new BlockPos(maxX, maxY, maxZ);
        this.formedAt = formedAt;
        this.previousShape = previousShape;
        this.lostSections = Collections.unmodifiableList(new ArrayList<>(lostSections));
    }

    /** What every member earns, times this (the spec's "sum of the members x size bonus"). */
    public float outputMultiplier() {
        return shape.outputMultiplier();
    }

    public boolean contains(BuildingPlacement p) {
        for (BuildingPlacement m : members)
            if (m == p)
                return true;
        return false;
    }

    public long ageTicks(long now) {
        return now - formedAt;
    }

    /** The raising moment (claude/merged-buildings-animation.md 1): the first {@link MergedBuildings#RAISING_TICKS} ticks. */
    public boolean isRaising(long now) {
        long age = ageTicks(now);
        return age >= 0 && age < MergedBuildings.RAISING_TICKS;
    }

    /** It stands where a bigger shape stood (a section fell): the collapse moment, same window as raising. */
    public boolean isSettling(long now) {
        return previousShape != null && previousShape.members() > shape.members() && isRaising(now);
    }

    /** Health of all sections over their max health, 0..1 - the one health bar, with a tick per section. */
    public float healthFraction() {
        double hp = 0, max = 0;
        for (BuildingPlacement m : members) {
            hp += Math.max(0, m.getHealth());
            max += Math.max(0, m.getMaxHealth());
        }
        return max <= 0 ? 0f : (float) Math.max(0, Math.min(1, hp / max));
    }

    /** Health of each section over its max, in member order (for the health bar's section ticks). */
    public float[] sectionHealth() {
        float[] out = new float[members.size()];
        for (int i = 0; i < out.length; i++) {
            BuildingPlacement m = members.get(i);
            int max = m.getMaxHealth();
            out[i] = max <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) m.getHealth() / max));
        }
        return out;
    }

    /** The middle of the footprint, at the members' ground level. */
    public BlockPos centre() {
        return new BlockPos((minCorner.getX() + maxCorner.getX()) / 2, minCorner.getY(),
            (minCorner.getZ() + maxCorner.getZ()) / 2);
    }

    @Override
    public String toString() {
        return shape + "(" + members.size() + ") of " + ownerName + " at " + minCorner.getX() + "," + minCorner.getZ()
            + (previousShape != null ? " from " + previousShape : "");
    }
}
