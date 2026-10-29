package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.resources.ResourceCost;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;

/**
 * A <b>Risen Skeleton</b>: the skeleton a Tombcaller's tombstone (or its Grave Adept) raises for 30 s. A Skeleton in
 * every way but its cost: free and, lovish's call (Oct 29), no population at all, so a stone raising every 20 s never
 * crowds out real production. Its own type rather than a tag on a plain skeleton, because population is counted on the
 * client too (the HUD, the production buttons) and entity tags never reach the client; the type does.
 */
public class RisenSkeletonUnit extends SkeletonUnit {

    private static final ResourceCost FREE = ResourceCost.Unit(0, 0, 0, 0, 0);

    public RisenSkeletonUnit(EntityType<? extends Skeleton> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public ResourceCost getCost() {
        return FREE;
    }
}
