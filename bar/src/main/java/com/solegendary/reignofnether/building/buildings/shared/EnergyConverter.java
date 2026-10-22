package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.raid.RaidTuning;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR's energy converter (metal maker): while its owner's energy is above half of storage, each converter turns
 * up to {@link #CONVERSION} energy/s into metal at {@code EconomyServerEvents.CONVERSION_RATIO} energy per metal.
 * Faction faces: Sunforged alchemist's crucible (lava), Gravebound soul furnace, Horde smelter pit (magma).
 * Fragile and explodes readily - BAR players know to keep converters away from the front.
 */
public class EnergyConverter extends Building {

    public final static String buildingName = "Energy Converter";
    public final static String structureName = "energy_converter";
    public final static ResourceCost cost = ResourceCosts.ENERGY_CONVERTER;
    public static final float CONVERSION = 50f;

    public EnergyConverter() {
        this("");
    }

    /** variant: "" (Sunforged crucible), "_dark" (Gravebound soul furnace), "_nether" (Horde smelter), "_verdant" (Court campfire), "_tide" (Tidewrought soul-fire stove) or "_storm" (Stormcallers Mana Condenser). */
    public EnergyConverter(String variant) {
        super(structureName + variant, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.BLAST_FURNACE;
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/energy_converter.png");   // tools/gen_hud_icons.py

        this.energyConversion = CONVERSION;

        this.explodeChance = 0.5f;
        this.maxHealth = com.solegendary.reignofnether.raid.RaidTuning.CONVERTER_HEALTH;   // the one fragile eco building (RaidTuning)
        this.buildTimeBlocks = 28;   // the original 28-block crucible's build time, for every faction's take
    }

    /**
     * The chain blast (lovish, Oct 22: "converters can be weak to punish players", BAR's metal makers blow up the ones
     * beside them): a built converter that falls to damage hits every OTHER converter - whoever owns it - whose centre
     * is within {@link RaidTuning#CONVERTER_BLAST_RADIUS} blocks for {@link RaidTuning#CONVERTER_BLAST_DAMAGE}, enough
     * to finish a standing one, so a packed converter farm goes down like a fuse while a spaced one loses one at a
     * time. Only converters: never the capitol, a defence or anything else, so a field beside a base is safe for the base.
     * Only when it fell to damage (BuildingServerEvents calls this before destroy(), so not on a cancel, a decommission or a
     * defeat clean-up) and not for a site that never finished.
     */
    @Override
    public void onFellToDamage(ServerLevel serverLevel, BuildingPlacement placement) {
        if (!placement.isBuilt)
            return;
        double r2 = RaidTuning.CONVERTER_BLAST_RADIUS * RaidTuning.CONVERTER_BLAST_RADIUS;
        int hit = 0;
        for (BuildingPlacement other : new ArrayList<>(BuildingServerEvents.getBuildings())) {
            if (other == placement || !(other.getBuilding() instanceof EnergyConverter) || !other.isBuilt
                    || other.getLevel() != serverLevel || other.shouldBeDestroyed())
                continue;
            double dx = other.centrePos.getX() - placement.centrePos.getX(), dz = other.centrePos.getZ() - placement.centrePos.getZ();
            if (dx * dx + dz * dz > r2 || Math.abs(other.centrePos.getY() - placement.centrePos.getY()) > RaidTuning.CONVERTER_BLAST_RADIUS)
                continue;
            if (placement.lastAttacker != null)
                other.lastAttacker = placement.lastAttacker;   // the raider who started the chain is who the owner's alarm looks for
            other.destroyRandomBlocks(RaidTuning.CONVERTER_BLAST_DAMAGE);
            hit++;
        }
        if (hit > 0) {
            double x = placement.centrePos.getX() + 0.5, y = placement.centrePos.getY() + 1.5, z = placement.centrePos.getZ() + 0.5;
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 14, 1.2, 0.6, 1.2, 0.3);
            serverLevel.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.7f, 1.4f);
        }
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
                name,
                this.icon,
                hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == this,
                () -> false,
                () -> true,
                List.of(
                        Component.translatable("buildings.reignofnether.energy_converter").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.energy_converter.tooltip1").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.energy_converter.tooltip2").getVisualOrderText()
                ),
                this
        );
    }
}
