package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

/**
 * BAR's wind generator: a cheap mast that gives a steady trickle of energy. Build them in rows behind the base;
 * they explode a little when destroyed, so don't bunch them up against your factories.
 */
public class WindGenerator extends Building {

    public final static String buildingName = "Wind Generator";
    public final static String structureName = "wind_generator";
    public final static ResourceCost cost = ResourceCosts.WIND_GENERATOR;

    /**
     * Where a mill's sails turn, in the structure's own (unrotated) coordinates: the rotor-hub block, the face the
     * rotor sits on and the blade length. lovish's clip ("windmill propellers are still f***ed"): the renderer used to
     * search the placed blocks for the first horizontal log and offset the sails from the camera ENTITY (not the
     * render camera), so in the RTS view the sails floated beside and above the towers, by an amount that changed
     * as the camera moved. Now every mill declares its hub; WindmillRenderClientEvents rotates it with the
     * placement, and tools/check_sail_hubs.py plus the every_sail_hub_sits_inside_its_mill GameTest keep each one
     * on the hub block inside the footprint. Change a mill's structure (tools/gen_structures.py), change it here.
     *
     * @param pos     the hub block, relative to the structure origin
     * @param outward the face the rotor plane sits outside of (NORTH = the structure's -z front)
     * @param length  blade length in blocks: a 3-wide tower with 3.2-block blades read as sails detached from it,
     *                so the blades now stay a little under the hub's height above the pad
     */
    public record SailHub(BlockPos pos, Direction outward, float length) { }

    public static final Map<String, SailHub> SAIL_HUBS = Map.of(
        "", new SailHub(new BlockPos(1, 4, 0), Direction.NORTH, 2.6f),           // Sunforged plaster mill
        "_dark", new SailHub(new BlockPos(1, 4, 0), Direction.NORTH, 2.6f),      // Gravebound bell tower
        "_nether", new SailHub(new BlockPos(1, 4, 0), Direction.NORTH, 2.6f),    // Horde war mill (bone hub)
        "_verdant", new SailHub(new BlockPos(1, 4, 0), Direction.NORTH, 2.6f),   // Verdant living mill
        "_tide", new SailHub(new BlockPos(1, 5, 0), Direction.NORTH, 3.0f),      // Tidewrought mast
        "_storm", new SailHub(new BlockPos(1, 5, 0), Direction.NORTH, 3.0f)      // Stormcallers wind-chime tower
    );

    /** Energy buildings on the WindGenerator class that are not mills and fly no sails (the Brassworks Steam Boiler
     *  vents steam instead). tools/check_sail_hubs.py and the sail GameTest skip these. */
    public static final java.util.Set<String> NO_SAILS = java.util.Set.of("_brass");

    /** "" / "_dark" / ... - the structure suffix, which is also the key into SAIL_HUBS. */
    public final String variant;

    public WindGenerator() {
        this("");
    }

    /** variant: "" (Kingdom plaster mill), "_dark" (The Fallen), "_nether" (The Gilded Legion), "_verdant" (Verdant Court), "_tide" (Tidewrought) or "_storm" (Stormcallers wind-chime tower). */
    public WindGenerator(String variant) {
        super(structureName + variant, cost, false);
        this.variant = variant;
        this.name = buildingName;
        this.portraitBlock = Blocks.IRON_BLOCK;
        // a mast with the faction's sailcloth (tools/gen_hud_icons.py), not a flat iron square
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/wind_generator" + variant + ".png");

        this.energyIncome = 14.0f;

        this.explodeChance = 0.35f;
        this.maxHealth = 30d;
        this.buildTimeBlocks = 44;   // the original 44-block mill's build time, for every faction's take
    }

    /** This mill's sail hub, or null for a variant without sails (none today; the check scripts fail on one). */
    public SailHub getSailHub() {
        return SAIL_HUBS.get(variant);
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
                        Component.translatable("buildings.reignofnether.wind_generator").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.wind_generator.tooltip1").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.wind_generator.tooltip2").getVisualOrderText()
                ),
                this
        );
    }
}
