package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingLevels;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A lab annex (design/building_upgrades.md): the in-faction replacement for BAR's nano turret. A small 3x3 building a
 * worker puts up within {@link #ANNEX_RANGE} blocks of an own army lab; it attaches to the nearest one and adds
 * {@link BuildingLevels#ANNEX_BUILD_POWER} of the lab's base production speed (at most
 * {@link BuildingLevels#ANNEX_SLOTS} count per lab). It is its own building - its own HP, targeted and destroyed on
 * its own - so a raid can cut the arms off a lab without killing it. Like a nano it also lends
 * {@link #ASSIST_POWER} build power to the nearest own construction site within {@link #ASSIST_RANGE} blocks.
 *
 * One shared class, one instance per faction (the variant picks the structure: "lab_annex" + variant), like the
 * economy buildings.
 */
public class LabAnnex extends Building {

    public final static String structureName = "lab_annex";
    /** 60 metal / 300 energy (ResourceCost.Building takes food, wood = energy, ore = metal: no RoN conversion). */
    public final static ResourceCost cost = ResourceCost.Building(0, 300, 60);
    /** Max gap (blocks, on x and z) between an annex's footprint and its lab's. */
    public static final int ANNEX_RANGE = 4;
    public static final float ASSIST_POWER = 1.0f;
    public static final int ASSIST_RANGE = 12;

    public final String variant;
    /** The army lab this faction's annex attaches to (also gates the build button). */
    public final Building hostLab;
    private final ResourceLocation buttonIcon;

    public LabAnnex(String variant, Building hostLab, Block portrait, String vanillaIconPath) {
        super(structureName + variant, cost, false);
        this.variant = variant;
        this.hostLab = hostLab;
        this.name = "Lab Annex";
        this.portraitBlock = portrait;
        this.buttonIcon = ResourceLocation.fromNamespaceAndPath("minecraft", vanillaIconPath);
        this.icon = this.buttonIcon;
        this.maxHealth = 80d;
        this.explodeChance = 0.2f;
        this.buildTimeBlocks = 30;   // every faction's annex takes the same time, whatever its art's block count
    }

    // ------------------------------------------------------------------ attachment

    /** The gap between two footprints on x and z (0 = touching or overlapping), Chebyshev style. */
    public static int gap(BuildingPlacement a, BuildingPlacement b) {   // public: BuildingLevels.annexCount's pre-filter
        int gx = Math.max(0, Math.max(a.minCorner.getX() - b.maxCorner.getX(), b.minCorner.getX() - a.maxCorner.getX()) - 1);
        int gz = Math.max(0, Math.max(a.minCorner.getZ() - b.maxCorner.getZ(), b.minCorner.getZ() - a.maxCorner.getZ()) - 1);
        return Math.max(gx, gz);
    }

    /**
     * The lab an annex placement attaches to: the nearest own, built army lab (levelled ProductionPlacement) within
     * ANNEX_RANGE. Proximity only, so the server and every client agree without a packet.
     */
    @Nullable
    public static BuildingPlacement hostOf(BuildingPlacement annex) {
        if (annex.getLevel() == null)
            return null;
        BuildingPlacement best = null;
        long bestD = Long.MAX_VALUE;
        for (BuildingPlacement lab : BuildingUtils.getBuildingsList(annex.getLevel().isClientSide())) {
            if (!lab.isBuilt || !BuildingLevels.isLevelledLab(lab) || !lab.ownerName.equals(annex.ownerName))
                continue;
            if (gap(annex, lab) > ANNEX_RANGE)
                continue;
            long d = (long) annex.centrePos.distSqr(lab.centrePos);
            if (d < bestD) {
                bestD = d;
                best = lab;
            }
        }
        return best;
    }

    /**
     * Placement rule (BuildingValidators): an annex footprint at min..max needs an own built army lab within range
     * with a free slot (built and unbuilt annexes both take one). Returns null if fine, else an i18n key.
     */
    @Nullable
    public static String placementError(Level level, String ownerName, BlockPos min, BlockPos max) {
        for (BuildingPlacement lab : BuildingUtils.getBuildingsList(level.isClientSide())) {
            if (!lab.isBuilt || !BuildingLevels.isLevelledLab(lab) || !lab.ownerName.equals(ownerName))
                continue;
            int gx = Math.max(0, Math.max(min.getX() - lab.maxCorner.getX(), lab.minCorner.getX() - max.getX()) - 1);
            int gz = Math.max(0, Math.max(min.getZ() - lab.maxCorner.getZ(), lab.minCorner.getZ() - max.getZ()) - 1);
            if (Math.max(gx, gz) > ANNEX_RANGE)
                continue;
            int used = 0;
            for (BuildingPlacement other : BuildingUtils.getBuildingsList(level.isClientSide()))
                if (other.getBuilding() instanceof LabAnnex && hostOf(other) == lab)
                    used++;
            if (used < BuildingLevels.ANNEX_SLOTS)
                return null;
            return "building.reignofnether.annex_slots_full";
        }
        return "building.reignofnether.annex_needs_lab";
    }

    // ------------------------------------------------------------------ assist (the nano part)

    @Override
    public void tick(Level tickLevel, BuildingPlacement placement) {
        if (tickLevel.isClientSide() || !placement.isBuilt || placement.decommissioning
                || (placement.tickAge + placement.originPos.getX()) % 20 != 0)
            return;
        BuildingPlacement site = null;
        double best = (double) ASSIST_RANGE * ASSIST_RANGE;
        for (BuildingPlacement b : BuildingUtils.getBuildingsList(false)) {
            if (b == placement || b.isBuilt || !placement.ownerName.equals(b.ownerName))
                continue;
            double d = b.centrePos.distSqr(placement.centrePos);
            if (d <= best) {
                best = d;
                site = b;
            }
        }
        if (site == null)
            return;
        site.assistPower = ASSIST_POWER;
        site.assistUntilTick = tickLevel.getGameTime() + 25;
        if (tickLevel instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.END_ROD, site.centrePos.getX() + 0.5, site.centrePos.getY() + 1.0,
                site.centrePos.getZ() + 0.5, 3, 0.8, 0.6, 0.8, 0.02);
    }

    // ------------------------------------------------------------------ build button

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : this.name;
        return new BuildingPlaceButton(
                name,
                this.buttonIcon,
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == this,
                () -> false,
                () -> BuildingClientEvents.hasFinishedBuilding(hostLab),
                List.of(
                        Component.translatable(key != null ? "buildings.reignofnether." + key.getPath() : "buildings.reignofnether.lab_annex")
                            .withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.lab_annex.tooltip1",
                            Math.round(BuildingLevels.ANNEX_BUILD_POWER * 100), BuildingLevels.ANNEX_SLOTS).getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.lab_annex.tooltip2", ANNEX_RANGE).getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.lab_annex.tooltip3", ASSIST_RANGE).getVisualOrderText()
                ),
                this
        );
    }
}
