package com.solegendary.reignofnether.building.buildings.shared;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * BAR's solar collector and advanced solar (claude/solar-spec.md, lovish Oct 21: "lets do them all"): steady energy for
 * a price. Wind (WindGenerator) is cheap and swings with the weather; solar earns the same every second whatever the
 * wind does ({@link #isWindDriven()} is false: EconomyServerEvents never multiplies it by resources/Wind).
 * <p>
 * Two jobs, one shared structure each, one instance per faction (a building has one faction), registered at the end of
 * Buildings so no id moves:
 * <ul>
 *   <li>{@link Kind#BASIC} - T1, any builder: 150 metal / 60 energy for 20 energy/s (BAR's ratio scaled to our wind),
 *       HP 120, a small pop when it dies;</li>
 *   <li>{@link Kind#ADVANCED} - T2, a T2 constructor only (T2Workers.needsT2Builder, like the Geothermal Plant):
 *       300 metal / 1500 energy for 75 energy/s, HP 500, goes up hard when destroyed.</li>
 * </ul>
 * <b>Shutters</b> (BAR's solar closes when hit; lovish took the recommended call): a hit on a built collector closes it
 * for {@link #SHUT_TICKS} (8 s); while shut it earns nothing and takes half damage ({@link #SHUT_DAMAGE_MULT}). A raid on
 * energy costs the income without wiping the field, and the player can see it shut. Numbers stay flexible (lovish): they
 * are all constants here and in {@link Kind}.
 */
public class SolarGenerator extends Building {

    /** The two jobs, with every number that tunes them. */
    public enum Kind {
        BASIC("Solar Collector", "solar_collector", ResourceCost.Building(0, 60, 150, 0), 20f, 120d, 0.1f, 90, false),
        ADVANCED("Advanced Solar", "advanced_solar", ResourceCost.Building(0, 1500, 300, 0), 75f, 500d, 0.6f, 140, true);

        public final String buildingName;
        public final String structureName;
        public final ResourceCost cost;
        public final float energyPerSecond;
        public final double maxHealth;
        public final float explodeChance;
        public final int buildTimeBlocks;
        public final boolean needsT2Builder;

        Kind(String buildingName, String structureName, ResourceCost cost, float energyPerSecond, double maxHealth,
             float explodeChance, int buildTimeBlocks, boolean needsT2Builder) {
            this.buildingName = buildingName;
            this.structureName = structureName;
            this.cost = cost;
            this.energyPerSecond = energyPerSecond;
            this.maxHealth = maxHealth;
            this.explodeChance = explodeChance;
            this.buildTimeBlocks = buildTimeBlocks;
            this.needsT2Builder = needsT2Builder;
        }
    }

    /** How long a hit keeps the shutters closed (8 s). */
    public static final int SHUT_TICKS = 160;
    /** Damage taken while shut. */
    public static final double SHUT_DAMAGE_MULT = 0.5;

    /** The damage a hit of this size does: halved while the shutters are shut. */
    public static double damageTaken(boolean shut, double amount) {
        return shut ? amount * SHUT_DAMAGE_MULT : amount;
    }

    public final Kind kind;

    public SolarGenerator(Kind kind) {
        super(kind.structureName, kind.cost, false);
        this.kind = kind;
        this.name = kind.buildingName;
        this.portraitBlock = kind == Kind.BASIC ? Blocks.COPPER_BLOCK : Blocks.WAXED_COPPER_BLOCK;
        this.icon = ResourceLocation.fromNamespaceAndPath(com.solegendary.reignofnether.ReignOfNether.MOD_ID,
            "textures/icons/buildings/" + kind.structureName + ".png");
        this.energyIncome = kind.energyPerSecond;
        this.maxHealth = kind.maxHealth;
        this.explodeChance = kind.explodeChance;
        this.buildTimeBlocks = kind.buildTimeBlocks;
    }

    /** Solar never follows the wind (that is what you pay for). */
    public boolean isWindDriven() {
        return false;
    }

    /** This faction's building of this kind (one instance per faction), or null for a faction with none. */
    @Nullable
    public static SolarGenerator of(Faction faction, Kind kind) {
        if (faction == null)
            return null;
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (b instanceof SolarGenerator s && s.kind == kind && faction.equals(s.getFaction()))
                return s;
        return null;
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new SolarPlacement(this, level, pos, rotation, ownerName,
            BuildingUtils.getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    /** The shutters' state: server side only, nothing saved (a reloaded collector simply starts open). */
    public static class SolarPlacement extends BuildingPlacement {
        /** Game time until which the shutters stay closed (0 = open). */
        public long shutUntil = 0;
        private boolean wasShut = false;

        public SolarPlacement(SolarGenerator building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                              ArrayList<BuildingBlock> blocks, boolean isCapitol) {
            super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
        }

        public boolean isShut() {
            return isShut(getLevel());
        }

        public boolean isShut(Level l) {
            return l != null && l.getGameTime() < shutUntil;
        }

        /** Closes the shutters now for {@link #SHUT_TICKS}. Public for the game test. */
        public void shut(Level l) {
            boolean wasOpen = !isShut(l);
            shutUntil = l.getGameTime() + SHUT_TICKS;
            wasShut = true;
            if (wasOpen && l instanceof ServerLevel sl)
                sl.playSound(null, centrePos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.9f, 1.2f);
        }

        /** A shut collector earns nothing. */
        @Override
        public float getEnergyIncome() {
            return isShut() ? 0f : super.getEnergyIncome();
        }

        /** A hit on a built collector halves while it is shut and (re)closes the shutters after it lands. */
        @Override
        public void destroyRandomBlocks(double amount) {
            Level l = getLevel();
            if (l == null || l.isClientSide() || !isBuilt || amount <= 0) {
                super.destroyRandomBlocks(amount);
                return;
            }
            double taken = damageTaken(isShut(l), amount);
            super.destroyRandomBlocks(taken);
            shut(l);
        }

        @Override
        public void tick(Level tickLevel) {
            super.tick(tickLevel);
            if (wasShut && !tickLevel.isClientSide() && !isShut(tickLevel)) {
                wasShut = false;
                if (tickLevel instanceof ServerLevel sl)
                    sl.playSound(null, centrePos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.9f, 1.2f);
            }
        }
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String shown = key != null ? Component.translatable("buildings." + getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()).getString() : kind.buildingName;
        String lang = "buildings.reignofnether." + kind.structureName;
        return new BuildingPlaceButton(
            shown,
            this.icon,
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == this,
            () -> false,
            () -> !kind.needsT2Builder || com.solegendary.reignofnether.unit.T2Workers.selectedHasT2Worker()
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
