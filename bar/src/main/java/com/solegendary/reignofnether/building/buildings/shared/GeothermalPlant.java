package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * BAR's geothermal plant (lovish's vision list; design/lively_buildings.md): a big, steady energy source that can only
 * stand over lava - a natural lava pool or one of the maps' lava vents. One shared structure (geothermal_plant.nbt: a
 * basalt-and-blackstone housing whose iron-bar windows look down on lava cauldrons, a smoking vent stack), one instance
 * per faction like the economy buildings, built only by a T2 constructor (T2Workers.needsT2Builder: the build button
 * checks the selection, the server the builders).
 * <p>
 * Placement (BuildingValidators): the footprint centre column must have lava at the foundation layer or the block
 * under it ({@link #isOverLava}); lava under the rest of the footprint counts as ground for this building only, and
 * its foundation may replace lava in its bottom layer. Anywhere else: "Must be placed over lava".
 */
public class GeothermalPlant extends Building {

    public final static String buildingName = "Geothermal Plant";
    public final static String structureName = "geothermal_plant";
    /** 280 metal / 1000 energy (ResourceCost.Building takes food, wood = energy, ore = metal: no RoN conversion). */
    public final static ResourceCost cost = ResourceCost.Building(0, 1000, 280);
    public static final String MUST_BE_LAVA = "building.reignofnether.must_be_lava";
    static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/magma.png");

    public GeothermalPlant() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.MAGMA_BLOCK;
        this.icon = ICON;
        // BAR's T2 geothermal is worth a field of wind: ~9 Wind Generators (14/s each) from one lava vent
        this.energyIncome = 120.0f;
        this.maxHealth = 400d;
        this.explodeChance = 0.6f;   // like BAR's, it goes up when it dies: don't wall your factories with it
        this.buildTimeBlocks = 110;
    }

    /**
     * True if this footprint (absolute blocks, as BuildingUtils.getAbsoluteBlockData returns them) has lava under its
     * centre column: at the foundation layer (the lowest y) or the block below it.
     */
    public static boolean isOverLava(Level level, List<BuildingBlock> absoluteBlocks) {
        if (level == null || absoluteBlocks == null || absoluteBlocks.isEmpty())
            return false;
        BlockPos min = BuildingUtils.getMinCorner(absoluteBlocks);
        BlockPos max = BuildingUtils.getMaxCorner(absoluteBlocks);
        BlockPos centre = new BlockPos((min.getX() + max.getX()) / 2, min.getY(), (min.getZ() + max.getZ()) / 2);
        return isLava(level.getBlockState(centre)) || isLava(level.getBlockState(centre.below()));
    }

    public static boolean isLava(BlockState bs) {
        return bs.getFluidState().is(FluidTags.LAVA);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ICON,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == this,
            () -> false,
            () -> com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker()   // a T2 constructor's job
                || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.geothermal_plant").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.geothermal_plant.tooltip1", Math.round(energyIncome)).getVisualOrderText(),
                Component.translatable("buildings.reignofnether.geothermal_plant.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
