package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.addon.BuildingAddon;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.faction.Faction;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Registered object in Buildings
// is a member of BuildingPlacement and is generally not instantiated directly
public abstract class Building {
    public String name;
    public String structureName;
    public ResourceLocation icon;
    public final boolean isCapitol;
    public Block portraitBlock; // block rendered in the portrait GUI to represent this building
    public boolean canAcceptResources = false; // can workers drop off resources here?
    private Faction faction;

    // chance for a mini explosion to destroy extra blocks if a player is breaking it
    // should be higher for large fragile buildings so players don't take ages to destroy it
    protected float buildTimeModifier = 1.0f; // only affects non-built buildings, not repair times
    public float getBuildTimeModifier() { return buildTimeModifier; }
    // > 0: construction takes as long as a structure of this many blocks would, whatever the real block count. The
    // economy buildings share one BAR cost/time across factions, so their per-faction art can be as detailed as it
    // likes without one faction's extractor building slower than another's.
    protected int buildTimeBlocks = 0;
    public int getBuildTimeBlocks() { return buildTimeBlocks; }
    protected float repairTimeModifier = 1.25f; // only affects built buildings

    public int captureRange = 20;
    /**
     * Merged buildings (claude/merged-buildings.md; building/merge/MergedBuildings): copies of this building laid edge
     * to edge in a Shift+Alt grid grow into one complex (Pair, Farm, Row, Quarter ...). Slice 1: the White Angel wind
     * generator only; the other factions' generators, converters and storage join in a later slice, labs, capitols,
     * extractors and game enders never (spec 2a).
     */
    public boolean mergeable = false;
    public boolean capturable = false;
    public boolean invulnerable = false;
    public boolean repairable = true;
    public boolean shouldDestroyOnReset = true;
    public boolean drawAggro = true;

    public static final int CAPITOL_SIGHT_RANGE = 32;
    public static final int DEFAULT_SIGHT_RANGE = 16;
    public static final int GARRISONED_BONUS_SIGHT_RANGE = 16;

    public ResourceCost cost;
    public boolean selfBuilding = false;

    // ---- BAR flow economy ----
    // passive income (per second) and extra storage this building type gives its owner once completed.
    // See EconomyServerEvents for the economy itself. Default 0; set by subclasses (farms, stockpiles, etc.).
    // Hook for upcoming Metal Extractor / Energy Generator buildings: override the getters (or set the fields).
    protected float metalIncome = 0;
    protected float energyIncome = 0;
    protected float metalStorage = 0;
    protected float energyStorage = 0;

    public float getMetalIncome() { return metalIncome; }
    public float getEnergyIncome() { return energyIncome; }
    public float getMetalStorage() { return metalStorage; }
    public float getEnergyStorage() { return energyStorage; }
    // BAR energy converter: energy per second this building turns into metal (EconomyServerEvents.CONVERSION_RATIO)
    protected float energyConversion = 0;
    public float getEnergyConversion() { return energyConversion; }

    // ticks of construction needed for EACH block with a total build power of 1.0 (one worker), before
    // buildTimeModifier. Reign of Nether used 10 ticks/block for one villager with diminishing returns for more
    // villagers; BAR build power adds up linearly, so this is set a bit higher to keep 2-3 builders close to the
    // old timings (eg. "60s for a Town Centre with 3 villagers").
    public static final float BUILD_TICKS_PER_BLOCK = 21f;   // was 15 - slower pacing (lovish: "base 0-100 in ten mins")
    public double maxHealth = 0;
    protected double maxHealthBonusPerUpgradeLevel = 0;

    public final static double DEFAULT_HEALTH_PER_BLOCK = 2.0d;

    public double getMaxHealth(BuildingPlacement placement) {
        double hp = maxHealth + (getUpgradeLevel(placement) * maxHealthBonusPerUpgradeLevel);
        // levelled labs (design/building_upgrades.md): x1.5 at L2, x2 at L3 - the bigger structure is a sturdier target
        return maxLevel > 1 ? hp * BuildingLevels.hpMultiplier(placement.getBuildingLevel()) : hp;
    }

    // ---- building levels (design/building_upgrades.md) ----
    // > 1: the building can be upgraded in place to this level (BuildingLevels). Its level-n structure is
    // getLevelStructureName(n); the level a placement is at is read back from the structure it wears.
    public int maxLevel = 1;

    /** The structure a placement of this building wears at {@code level} (1 = the base structure). */
    public String getLevelStructureName(int level) {
        return level <= 1 ? structureName : structureName + "_l" + level;
    }

    /**
     * The level encoded by a saved / synced upgradeLevel (getUpgradeLevel's value). Default: levelled buildings save
     * level - 1 (so an unlevelled lab still saves 0 and loads exactly as before).
     */
    public int getLevelFromUpgradeCode(int upgradeCode) {
        return maxLevel > 1 ? Math.max(1, Math.min(maxLevel, upgradeCode + 1)) : 1;
    }

    public boolean isUsingSetHealth(BuildingPlacement placement) {
        return getMaxHealth(placement) > 0;
    }

    // blocks types that are placed automatically when the building is placed
    // used to control size of initial foundations while keeping it symmetrical
    public final ArrayList<Block> startingBlockTypes = new ArrayList();

    public int foundationYLayers = 1; // how many Y layers from the bottom are part of the foundation

    protected final Abilities abilities = new Abilities();

    private Map<Class<BuildingAddon>, BuildingAddon> activeAddons = new HashMap<>();

    public Abilities getAbilities() {
        return this.abilities;
    }

    public Building(String structureName, ResourceCost cost, boolean isCapitol) {
        this.structureName = structureName;
        this.cost = cost;
        this.isCapitol = isCapitol;
    }

    public float getMeleeDamageMult() {
        return 0.25F; // this is 50% visually, as 1 block is 2hp by default
    }

    public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) {
        return BuildingBlockData.getBuildingBlocksFromNbt(this.structureName, level);
    }

    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new BuildingPlacement(this, level, pos, rotation, ownerName, BuildingUtils.getAbsoluteBlockData(this.getRelativeBlockData(level), level, pos, rotation), this.isCapitol);
    }

    public void setFaction(ResourceLocation faction) {
        this.faction = Factions.getFaction(faction);
    }
    
    public Faction getFaction() {
        return this.faction == null ? Factions.NONE : this.faction;
    }

    public int getUpgradeLevel(BuildingPlacement placement) {
        // a levelled building reports level - 1, which the save and the late-joiner sync carry for free
        return maxLevel > 1 ? placement.getBuildingLevel() - 1 : 0;
    }

    public abstract BuildingPlaceButton getBuildButton(Keybinding var1);

    public boolean isTypeOf(Building building) {
        return this == building;
    }

    public String getUpgradedStructureName(int upgradeLevel) {
        return structureName;
    }

    @Nullable
    public <T extends BuildingAddon> T getActiveAddon(Class<T> addonClass) {
        return (T) activeAddons.get(addonClass);
    }

    public <T extends BuildingAddon> void setActiveAddon(Class<T> addonClass, T addon, boolean active) {
        if (active) {
            activeAddons.put((Class<BuildingAddon>) addonClass, addon);
        } else {
            activeAddons.remove(addonClass);
        }
    }

    public boolean hasActiveAddon(Class<? extends BuildingAddon> addonClass) {
        return activeAddons.containsKey(addonClass);
    }

    public boolean canDestroyBlock(BlockPos relativeBp, BuildingPlacement placement) {
        return true;
    }

    public void onBlockBuilt(BlockPos bp, BlockState bs, BuildingPlacement buildingPlacement) {

    }

    public void onBuilt(BuildingPlacement buildingPlacement) {

    }

    /** A built building has just fallen to damage (not a cancel, a decommission or a clean-up), blocks still standing. Hook for death blasts. */
    public void onFellToDamage(ServerLevel serverLevel, BuildingPlacement placement) { }

    /**
     * Whether this building's collapse blasts (BuildingPlacement.destroy) leave the ground alone. A TNT-style blast at
     * the bottom layer craters the dirt under the footprint, and a metal patch whose ground is holed fails the "ground
     * not flat" check, so the patch could not be rebuilt on. The effect and sound stay; only the terrain damage goes.
     */
    public boolean keepsGroundIntact() { return false; }

    public void destroy(ServerLevel serverLevel, BuildingPlacement placement) {

    }

    public void tick(Level tickLevel, BuildingPlacement buildingPlacement) {

    }

    public String getUpgradedName(BuildingPlacement buildingPlacement) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        if (key == null) {
            return "Unknown";
        }
        return I18n.get("buildings." + key.getNamespace() + "." + key.getPath());
    }
}