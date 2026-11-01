package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A building that loses blocks (a hit, its destruction) drops no items. lovish's Oct 29 game: the 1,313 items lying in
 * the world after the match were all building pieces - 542 lanterns, 391 ladders, 361 banners, 239 soul lanterns, 107
 * iron doors: a lantern, ladder, banner or door whose support was removed popped off as an item (BuildingPlacement
 * removes its own blocks without drops, but a neighbour that can no longer stand breaks the vanilla way). Up to 411
 * such items were ticking at once, 2.8 ms of a server tick, and the client drew every one (perf survey Oct 30;
 * lovish: "No drops from buildings").
 *
 * <p>While a placement removes its blocks ({@link #begin} / {@link #end} round the removal), an item that joins the
 * level within {@link #MARGIN} blocks of the building and is one of the building's own blocks is not added
 * (BuildingServerEvents.onItemDrop). Only then and only those: a unit's items or a worker's resources dropped beside a
 * building at any other moment are untouched. Server thread only.
 */
public final class BuildingDrops {
    private BuildingDrops() { }

    /** How far outside the building's corners a dropped piece is still the building's (a wall banner, a blast). */
    public static final int MARGIN = 2;

    @Nullable private static BuildingPlacement removing = null;
    private static final Map<BuildingPlacement, Set<Item>> PALETTES = new WeakHashMap<>();
    /** Pieces kept from dropping since the server started (tests compare it before and after). */
    public static long suppressedTotal = 0;

    /** {@code placement} starts removing blocks; returns the one that was (nested removals), for {@link #end}. */
    @Nullable
    public static BuildingPlacement begin(BuildingPlacement placement) {
        BuildingPlacement was = removing;
        removing = placement;
        return was;
    }

    public static void end(@Nullable BuildingPlacement was) {
        removing = was;
    }

    /** An item joining the level now that is a piece of the building removing its blocks: it is not added. */
    public static boolean suppress(ItemEntity item) {
        BuildingPlacement b = removing;
        if (b == null || item.level().isClientSide())
            return false;
        BlockPos min = b.minCorner, max = b.maxCorner;
        if (item.getX() < min.getX() - MARGIN || item.getX() > max.getX() + 1 + MARGIN
                || item.getY() < min.getY() - MARGIN || item.getY() > max.getY() + 1 + MARGIN
                || item.getZ() < min.getZ() - MARGIN || item.getZ() > max.getZ() + 1 + MARGIN)
            return false;
        if (!PALETTES.computeIfAbsent(b, BuildingDrops::palette).contains(item.getItem().getItem()))
            return false;
        suppressedTotal++;
        return true;
    }

    private static Set<Item> palette(BuildingPlacement b) {
        Set<Item> items = new HashSet<>();
        for (BuildingBlock bb : b.getBlocks()) {
            Item i = bb.getBlockState().getBlock().asItem();
            if (i != Items.AIR)
                items.add(i);
        }
        return items;
    }
}
