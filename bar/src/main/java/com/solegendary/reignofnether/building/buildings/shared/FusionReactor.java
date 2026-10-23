package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The endgame energy sources (lovish: "add an endgame energy source called fusion reactors and advanced fusion
 * reactors"). BAR's fusions: a huge, steady output for a big metal bill, and they go up with a bang when they die
 * (BAR's fusion blast is smaller than a game ender's; the advanced one is bigger).
 * <p>
 * Numbers are scaled from this mod's own Advanced Solar (75 energy/s for 300 metal / 1500 energy) at BAR's ratios
 * (Fusion = 10 advanced solars of output for about 7 of their metal; Advanced Fusion = 3x the output for 3.5x the
 * price). They are constants in {@link Kind}, like the solar and geothermal numbers, so one place tunes them.
 * <ul>
 *   <li>{@link Kind#FUSION} - T2: a T2 constructor (T2Workers.needsT2Builder), like the Advanced Solar;</li>
 *   <li>{@link Kind#ADVANCED} - T3: a T2 constructor and Tier 3 research (button and server both).</li>
 * </ul>
 * Steady, never wind-driven. One shared structure each (placeholders reuse the Advanced Solar's and the Geothermal
 * Plant's .nbt until the real look is drawn), one instance per faction like the other energy buildings.
 */
public class FusionReactor extends Building {

    public enum Kind {
        // name, structure (placeholder), lang id, cost (wood = energy, ore = metal), energy/s, HP, explode chance per
        // broken block, explode radius, build time in blocks, needs Tier 3 research
        FUSION("Fusion Reactor", "advanced_solar", "fusion_reactor", ResourceCost.Building(0, 3000, 2100, 0),
            750f, 900d, 0.08f, 5.0f, 220, false),
        ADVANCED("Advanced Fusion Reactor", "geothermal_plant", "advanced_fusion_reactor", ResourceCost.Building(0, 10500, 7350, 0),
            2250f, 1600d, 0.12f, 8.0f, 320, true);

        public final String buildingName;
        public final String structureName;
        public final String langId;
        public final ResourceCost cost;
        public final float energyPerSecond;
        public final double maxHealth;
        public final float explodeChance;
        public final float explodeRadius;
        public final int buildTimeBlocks;
        public final boolean needsTier3;

        Kind(String buildingName, String structureName, String langId, ResourceCost cost, float energyPerSecond,
             double maxHealth, float explodeChance, float explodeRadius, int buildTimeBlocks, boolean needsTier3) {
            this.buildingName = buildingName;
            this.structureName = structureName;
            this.langId = langId;
            this.cost = cost;
            this.energyPerSecond = energyPerSecond;
            this.maxHealth = maxHealth;
            this.explodeChance = explodeChance;
            this.explodeRadius = explodeRadius;
            this.buildTimeBlocks = buildTimeBlocks;
            this.needsTier3 = needsTier3;
        }
    }

    public final Kind kind;

    public FusionReactor(Kind kind) {
        super(kind.structureName, kind.cost, false);
        this.kind = kind;
        this.name = kind.buildingName;
        this.portraitBlock = kind == Kind.FUSION ? Blocks.BEACON : Blocks.NETHERITE_BLOCK;
        // placeholder icon: the Advanced Solar's until the fusion icons are drawn
        this.icon = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/buildings/advanced_solar.png");
        this.energyIncome = kind.energyPerSecond;
        this.maxHealth = kind.maxHealth;
        this.explodeChance = kind.explodeChance;
        this.explodeRadius = kind.explodeRadius;
        this.buildTimeBlocks = kind.buildTimeBlocks;
    }

    /** Explosion radius of this reactor's blast (read by the GameTest; BuildingPlacement uses the field). */
    public float blastRadius() {
        return explodeRadius;
    }

    /** Fusion never follows the wind. */
    public boolean isWindDriven() {
        return false;
    }

    /** This faction's reactor of this kind (one instance per faction), or null. */
    @Nullable
    public static FusionReactor of(Faction faction, Kind kind) {
        if (faction == null)
            return null;
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (b instanceof FusionReactor f && f.kind == kind && faction.equals(f.getFaction()))
                return f;
        return null;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String shown = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : kind.buildingName;
        String lang = "buildings.reignofnether." + kind.langId;
        return new BuildingPlaceButton(
            shown,
            this.icon,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == this,
            () -> false,
            () -> (com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker()
                    && (!kind.needsTier3 || ResearchClient.hasResearch(ProductionItems.RESEARCH_TIER_3)))
                || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable(lang).withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(kind.cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable(lang + ".tooltip1", Math.round(energyIncome)).getVisualOrderText(),
                Component.translatable(lang + ".tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
