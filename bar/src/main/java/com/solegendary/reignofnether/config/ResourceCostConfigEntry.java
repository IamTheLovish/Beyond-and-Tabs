package com.solegendary.reignofnether.config;

import com.electronwill.nightconfig.core.Config;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraftforge.common.ForgeConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ResourceCostConfigEntry {
    public static final List<ResourceCostConfigEntry> ENTRIES = new ArrayList<>();

    private ForgeConfigSpec.ConfigValue<Integer> FOOD;
    private ForgeConfigSpec.ConfigValue<Integer> WOOD;
    private ForgeConfigSpec.ConfigValue<Integer> ORE;
    private ForgeConfigSpec.ConfigValue<Integer> SECONDS;
    private ForgeConfigSpec.ConfigValue<Integer> POPULATION;

    private final int default_food;
    private final int default_wood;
    private final int default_ore;
    private final int default_seconds;
    private final int default_population;
    public final String id;
    private final String comment;

    private ResourceCostConfigEntry(int food, int wood, int ore, int seconds, int population, ResourceCost associatedCost, String comment) {
        this.default_food = food;
        this.default_wood = wood;
        this.default_ore = ore;
        this.default_seconds = seconds;
        this.default_population = population;
        this.comment = comment;
        this.id = associatedCost.id;
        ENTRIES.add(this);

    }
    public static ResourceCostConfigEntry Unit(int food, int wood, int ore, int seconds, int population, ResourceCost associatedCost, String comment) { // units
        return new ResourceCostConfigEntry(food, wood, ore, seconds, population, associatedCost, comment);
    }
    public static ResourceCostConfigEntry Research(int food, int wood, int ore, int seconds, ResourceCost associatedCost, String comment) { // research
        return new ResourceCostConfigEntry(food, wood, ore, seconds, 0, associatedCost, comment);
    }
    public static ResourceCostConfigEntry Building(int food, int wood, int ore, int supply, ResourceCost associatedCost, String comment) { // buildings
        return new ResourceCostConfigEntry(food, wood, ore, 0, supply, associatedCost, comment);
    }
    public static ResourceCostConfigEntry Ability(int food, int wood, int ore, ResourceCost associatedCost, String comment) { // enchantments and equipment
        return new ResourceCostConfigEntry(food, wood, ore, 0, 0, associatedCost, comment);
    }

    //Defines each config value for the given ResourceCostConfigEntry
    public void define(ForgeConfigSpec.Builder builder) {
        builder.push(this.comment);
        this.FOOD = builder.define("Food cost", this.default_food);
        this.WOOD = builder.define("Wood cost", this.default_wood);
        this.ORE = builder.define("Ore cost", this.default_ore);
        this.SECONDS = builder.define("Time to create", this.default_seconds);
        this.POPULATION = builder.define("Population value", this.default_population);
        builder.pop();
    }

    public int getFood() {return read(this.FOOD);}
    public int getWood() {return read(this.WOOD);}
    public int getOre() {return read(this.ORE);}
    public int getSeconds() {return read(this.SECONDS);}
    public int getPopulation() {return read(this.POPULATION);}

    /**
     * A loaded value is never null, but Forge 47's ConfigValue.get() can return null while the config reloads on
     * another thread: it checks its cached value and returns it in two reads of a plain field, and the reload's
     * clearCache() can land between them (PR run 40469588386: mod loading crashed in COMMON_SETUP, where
     * ResourceCosts.deferredLoadResourceCosts reads every entry while the corrected config file reloads). Reading
     * get() again only made that unlikely: #221's CI (run 40587228567) had a value null across 8 reads on a loaded
     * runner. So a null with a loaded config reads the value straight from the loaded config, the way get() itself
     * does on an empty cache (ConfigValue.getRaw), which no reload can clear; no value changes, only the null goes.
     * A null with no loaded config is a real fault and says which value.
     */
    private static int read(ForgeConfigSpec.ConfigValue<Integer> value) {
        Integer v = value.get();
        if (v != null)
            return v;
        Object raw = Raw.read(value);
        if (raw == null)
            throw new IllegalStateException("config value " + String.join(".", value.getPath())
                + " is null and its config is not loaded");
        return (Integer) raw;
    }

    /**
     * Forge keeps the loaded config and getRaw private to ForgeConfigSpec (PR #287's ConfigValueReloadRaceMixin
     * reaches them with an accessor; this one is plain reflection so the JUnit test, which runs without mixins, takes
     * the same path). Forge's own names, not Minecraft's, so the same in production.
     */
    private static final class Raw {
        private static final Field SPEC;
        private static final Field CHILD_CONFIG;
        private static final Field DEFAULT_SUPPLIER;
        private static final Method GET_RAW;

        static {
            try {
                SPEC = ForgeConfigSpec.ConfigValue.class.getDeclaredField("spec");
                CHILD_CONFIG = ForgeConfigSpec.class.getDeclaredField("childConfig");
                DEFAULT_SUPPLIER = ForgeConfigSpec.ConfigValue.class.getDeclaredField("defaultSupplier");
                GET_RAW = ForgeConfigSpec.ConfigValue.class.getDeclaredMethod("getRaw", Config.class, List.class, Supplier.class);
                SPEC.setAccessible(true);
                CHILD_CONFIG.setAccessible(true);
                DEFAULT_SUPPLIER.setAccessible(true);
                GET_RAW.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        /** The value from the loaded config, or null when there is none. */
        static Object read(ForgeConfigSpec.ConfigValue<?> value) {
            try {
                Object spec = SPEC.get(value);
                Object loaded = spec == null ? null : CHILD_CONFIG.get(spec);
                if (loaded == null)
                    return null;
                return GET_RAW.invoke(value, loaded, value.getPath(), DEFAULT_SUPPLIER.get(value));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("config value " + String.join(".", value.getPath())
                    + " could not be read from its loaded config", e);
            }
        }
    }
}
