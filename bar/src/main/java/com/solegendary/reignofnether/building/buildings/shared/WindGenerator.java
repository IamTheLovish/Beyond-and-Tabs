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

    /** The mills that fly sails (design/lively_buildings.md: sails only where there's a rotor). */
    public static final Map<String, SailHub> SAIL_HUBS = Map.of(
        "", new SailHub(new BlockPos(1, 7, 0), Direction.NORTH, 2.8f),           // Sunforged Sun Mill (tower-top cap)
        "_nether", new SailHub(new BlockPos(0, 5, 1), Direction.WEST, 2.7f)      // Ashborn Ember Mill (basalt hub on the west slope)
    );

    /** Energy buildings on the WindGenerator class that are not mills and fly no sails; each moves another way
     *  (MOTIONS). tools/check_sail_hubs.py and the sail GameTest skip these for sails and check their motion. */
    public static final java.util.Set<String> NO_SAILS = java.util.Set.of("_dark", "_verdant", "_tide", "_storm", "_brass");

    /**
     * lovish (Oct 12): "windmills don't need to be windmill shaped" - every faction's energy building now has its own
     * moving part, drawn by WindmillRenderClientEvents once the building stands (built-only, distance-culled):
     * <ul>
     *   <li>WHEEL: a wheel turning in the vertical plane outside {@code outward} of a horizontal axle log at pos,
     *       size = radius (the Tidewrought waterwheel, the Brassworks flywheel); it must clear the ground</li>
     *   <li>BELL: a bell swinging in the empty cell pos, hung from the solid block above it; size = swing amplitude
     *       in radians (the Gravebound soul-bell)</li>
     *   <li>PETALS: petals spiralling down from the leaf block at pos; size = spiral radius (the Verdant spirit tree)</li>
     *   <li>VANES: jade vanes spinning round the vertical log at pos, over an empty ring; size = arm length
     *       (the Stormcallers wind-chime pagoda)</li>
     * </ul>
     * Positions are in the structure's own (unrotated) coordinates, like SAIL_HUBS. tools/check_sail_hubs.py and the
     * every_moving_part_sits_on_its_anchor GameTest keep each one on its anchor. Change a structure, change it here.
     */
    public enum MotionKind { WHEEL, BELL, PETALS, VANES }
    public record Motion(MotionKind kind, BlockPos pos, Direction outward, float size) { }

    public static final Map<String, Motion> MOTIONS = Map.of(
        "_dark", new Motion(MotionKind.BELL, new BlockPos(1, 8, 1), Direction.NORTH, 0.45f),     // Gravebound Soul-Bell Ossuary
        "_verdant", new Motion(MotionKind.PETALS, new BlockPos(1, 11, 1), Direction.UP, 1.7f),    // Verdant Blossom Stag
        "_tide", new Motion(MotionKind.WHEEL, new BlockPos(0, 2, 1), Direction.WEST, 1.4f),       // Tidewrought waterwheel
        "_storm", new Motion(MotionKind.VANES, new BlockPos(1, 9, 1), Direction.UP, 1.3f),        // Stormcallers Wind-Chime Pagoda
        "_brass", new Motion(MotionKind.WHEEL, new BlockPos(0, 3, 1), Direction.WEST, 1.35f)       // Brassworks Engine House flywheel
    );

    /** "" / "_dark" / ... - the structure suffix, which is also the key into SAIL_HUBS. */
    public final String variant;

    public WindGenerator() {
        this("");
    }

    /** variant: "" (Sunforged gilded sun mill), "_dark" (Gravebound soul-bell tower), "_nether" (Horde war mill),
     *  "_verdant" (Verdant spirit tree), "_tide" (Tidewrought tide-mill), "_storm" (Stormcallers wind-chime pagoda) or
     *  "_brass" (Brassworks steam boiler) - design/lively_buildings.md. */
    public WindGenerator(String variant) {
        super(structureName + variant, cost, false);
        this.variant = variant;
        this.name = buildingName;
        this.portraitBlock = Blocks.IRON_BLOCK;
        // a mast with the faction's sailcloth (tools/gen_hud_icons.py), not a flat iron square
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/wind_generator" + variant + ".png");

        this.energyIncome = 14.0f;

        this.explodeChance = 0.35f;
        this.maxHealth = com.solegendary.reignofnether.raid.RaidTuning.WIND_HEALTH;   // 30 -> 75: 3-4 T1 vehicles take 5-8 s (RaidTuning)
        this.buildTimeBlocks = 44;   // the original 44-block mill's build time, for every faction's take
        // merged buildings, slice 1 (claude/merged-buildings.md build order 1): the White Angel mill is the first
        // building whose Shift+Alt grids grow into complexes; the other factions' generators follow in slice 3
        this.mergeable = variant.isEmpty();
    }

    /**
     * Earns with the wind (resources/Wind): the mills, vanes, bell tower and blossom catch it; the Tidewrought
     * waterwheel (tide) and the Brassworks steam boiler (fuel) run steady - same average, no swings (BAR's tidal and
     * solar against wind).
     */
    public boolean isWindDriven() {
        return !"_tide".equals(variant) && !"_brass".equals(variant);
    }

    /** This mill's sail hub, or null for a variant without sails (NO_SAILS). */
    public SailHub getSailHub() {
        return SAIL_HUBS.get(variant);
    }

    /** This energy building's moving part other than sails (MOTIONS), or null. */
    public Motion getMotion() {
        return MOTIONS.get(variant);
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
