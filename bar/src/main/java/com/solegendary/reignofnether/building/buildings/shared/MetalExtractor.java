package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * BAR's metal extractor: the backbone of the economy. May only be placed on a metal patch (MetalPatches), gives a
 * steady metal income, and taking map control means taking more patches. One shared structure; each faction
 * registers its own instance.
 */
public class MetalExtractor extends com.solegendary.reignofnether.building.production.ProductionBuilding {

    public final static String buildingName = "Metal Extractor";
    public final static String structureName = "metal_extractor";
    public final static String upgradedStructureName = "metal_extractor_t2";
    /** Metal per second at tier 2 (tier 1 is metalIncome). Was 7 (3.5x a T1 extractor) until lovish, Oct 24: "tier 2 ...
     *  rendering tier 1 ineffective" - now 2.25x, so a T1 base stays an economy (resources/TierGate). The economy banks
     *  income as a float every tick (EconomyServerEvents), so a fractional rate is exact. */
    public final static float T2_METAL_INCOME = 4.5f;
    public final static ResourceCost cost = ResourceCosts.METAL_EXTRACTOR;

    /** "" (Kingdom), "_dark" (The Fallen), "_nether" (The Gilded Legion), "_verdant" (Verdant Court), "_tide" (Tidewrought) or "_storm" (Stormcallers); picks the faction's structure skin. */
    public final String variant;

    public MetalExtractor() {
        this("");
    }

    public MetalExtractor(String variant) {
        super(structureName + variant, cost, false);
        this.variant = variant;
        this.name = buildingName;
        this.portraitBlock = Blocks.RAW_IRON_BLOCK;
        // our own icon (tools/gen_hud_icons.py): the raw iron block read as dirt on the build bar (lovish's clip)
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/metal_extractor.png");

        this.metalIncome = 2.0f;

        // softer than the rest but not a converter: 4-6 infantry raiders take 12-18 s, 3-4 T1 vehicles 6-10 s; the
        // T2 copper tower adds the same again (getUpgradeLevel reads the placed blocks) - RaidTuning
        this.maxHealth = com.solegendary.reignofnether.raid.RaidTuning.EXTRACTOR_T1_HEALTH;
        this.maxHealthBonusPerUpgradeLevel = com.solegendary.reignofnether.raid.RaidTuning.EXTRACTOR_T2_BONUS_HEALTH;
        this.buildTimeBlocks = 40;   // the original 40-block derrick's build time, for every faction's take
        this.canSetRallyPoint = false;

        this.productions.add(com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_EXTRACTOR,
                com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot1);
    }

    /** Tier 2 is read from the placed blocks (the copper tower), so it survives saves for free. Waxed copper block
     *  counts too: the faction T2 structures use it because unwaxed copper oxidises mid-match, and RoN's exact
     *  blockstate match would then count the oxidised block as missing. No T1 structure may hold any of these. */
    @Override
    public int getUpgradeLevel(com.solegendary.reignofnether.building.BuildingPlacement placement) {
        for (com.solegendary.reignofnether.building.BuildingBlock block : placement.getBlocks()) {
            net.minecraft.world.level.block.Block b = block.getBlockState().getBlock();
            if (b == Blocks.COPPER_BLOCK || b == Blocks.CUT_COPPER || b == Blocks.WAXED_COPPER_BLOCK)
                return 1;
        }
        return 0;
    }

    /** A destroyed extractor leaves its patch rebuildable at once: its collapse must not crater the ground under it (RaidTuning). */
    @Override
    public boolean keepsGroundIntact() {
        return true;
    }

    @Override
    public String getUpgradedStructureName(int upgradeLevel) {
        return (upgradeLevel > 0 ? upgradedStructureName : structureName) + variant;
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
                        Component.translatable("buildings.reignofnether.metal_extractor").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        FormattedCharSequence.forward("", Style.EMPTY),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip1").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip2").getVisualOrderText(),
                        Component.translatable("buildings.reignofnether.metal_extractor.tooltip3").getVisualOrderText()
                ),
                this
        );
    }
}
