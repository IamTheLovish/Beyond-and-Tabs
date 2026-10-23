package com.solegendary.reignofnether.keybinds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BAR's build-category keys (Legacy profile, keybind_defaults.json): Z metal extractors, X energy and converters,
 * C defence, V nano turrets and factories. Each key owns an ordered list; only entries the selected builder can
 * build count; the first press picks the first entry, each further press of the same key the next, wrapping
 * (BAR's gui_buildmenu buildUnitHandler). No Minecraft types: BuildCategoryKeysGameTests checks the rules headless;
 * HudClientEvents.onBuildCategoryKey feeds it the card's buildings.
 *
 * Buildings are named by registry path ("wind_generator_tide"); a faction suffix is ignored, so one list serves all
 * seven factions. A building BAR gives no key (storage, walls, traps, T2 labs, ...) is in no category and keeps its
 * own slot key; when that slot key IS a category key (the stockpiles' Z, the bridges' C) it joins the end of that
 * key's cycle ({@link #listFor}), so it stays reachable on the key it always had.
 */
public final class BuildCategoryKeys {

    public enum Category { METAL, ENERGY, DEFENCE, FACTORY }

    /** The faction suffixes of the registry paths (Buildings, Defences). */
    private static final List<String> FACTION_SUFFIXES =
        List.of("villagers", "monsters", "piglins", "verdant", "tide", "storm", "brass");

    /**
     * Each category's entries in BAR's order, by path with the faction suffix removed.
     * METAL: the extractor (its T2 is an upgrade of the same building, not a second entry).
     * ENERGY: solar, wind, advanced solar, geothermal (BAR's fusion slot), energy converter (BAR's metal makers).
     * DEFENCE: light towers, anti-air, then the heavy emplacements (BAR's LLT, AA, plasma/flak; no radar, torpedo or
     * sonar exist here); each faction's own tower sits with its tier.
     * FACTORY: the lab annex (BAR's nano turret) first, then the faction's T1 army lab.
     */
    private static final Map<Category, List<String>> ORDER = new EnumMap<>(Category.class);
    static {
        ORDER.put(Category.METAL, List.of("metal_extractor"));
        ORDER.put(Category.ENERGY, List.of("solar_collector", "wind_generator", "advanced_solar", "geothermal_plant",
            "energy_converter"));
        ORDER.put(Category.DEFENCE, List.of("raid_tower", "raid_tower_heavy", "watchtower", "dark_watchtower",
            "sky_watch", "sky_watch_heavy", "flak", "storm_oak", "rail_battery", "lighthouse_battery", "anti_t3",
            "ward_spire"));
        ORDER.put(Category.FACTORY, List.of("lab_annex", "barracks", "graveyard", "portal_basic", "grove", "slipway",
            "lotus_academy", "machine_shop"));
    }

    private BuildCategoryKeys() { }

    /** The path without its faction suffix ("raid_tower_heavy_tide" -> "raid_tower_heavy"). */
    public static String stem(String path) {
        for (String f : FACTION_SUFFIXES)
            if (path.endsWith("_" + f))
                return path.substring(0, path.length() - f.length() - 1);
        return path;
    }

    /** The category a building is in, or null when BAR gives it no key. */
    public static Category categoryOf(String path) {
        String stem = stem(path);
        for (Map.Entry<Category, List<String>> e : ORDER.entrySet())
            if (e.getValue().contains(stem))
                return e.getKey();
        return null;
    }

    /** Position in its category's list, or -1. */
    public static int rankOf(String path) {
        Category c = categoryOf(path);
        return c == null ? -1 : ORDER.get(c).indexOf(stem(path));
    }

    /** Every stem of a category in BAR's order (for tests and tooltips). */
    public static List<String> stems(Category category) {
        return ORDER.get(category);
    }

    /**
     * The cycle for one key: {@code buildable} (paths the selected builder can build now, in card order) that are in
     * {@code category}, in BAR's order, then the paths of {@code onKey} (buildings outside every category whose own
     * slot key is this key) in card order.
     */
    public static List<String> listFor(Category category, Collection<String> buildable, Set<String> onKey) {
        List<String> inCategory = new ArrayList<>();
        List<String> tail = new ArrayList<>();
        for (String path : buildable) {
            if (inCategory.contains(path) || tail.contains(path))
                continue;
            Category c = categoryOf(path);
            if (c == category)
                inCategory.add(path);
            else if (c == null && onKey.contains(path))
                tail.add(path);
        }
        inCategory.sort((a, b) -> Integer.compare(rankOf(a), rankOf(b)));   // stable: card order breaks ties
        inCategory.addAll(tail);
        return inCategory;
    }

    /**
     * Whether a Z / X / C / V press belongs to the build list rather than to the ability slot, order or hotkey the
     * key also is: only with a builder (or the commander) selected, no Ctrl / Alt held and something in the list.
     * Otherwise the key keeps its old meaning.
     */
    public static boolean claimsKey(boolean builderSelected, boolean ctrlOrAltHeld, List<String> list) {
        return builderSelected && !ctrlOrAltHeld && !list.isEmpty();
    }

    /** Which key press came last, so the next press of the same key moves on. */
    public static final class Cycle {
        private Category lastCategory = null;
        private String lastPath = null;

        public void reset() {
            lastCategory = null;
            lastPath = null;
        }

        /**
         * The building this press picks: the first of {@code list} unless the last press was this same key and picked
         * a building still in the list, then the one after it (wrapping). Null (state untouched) for an empty list.
         */
        public String press(Category category, List<String> list) {
            if (list.isEmpty())
                return null;
            int at = lastCategory == category && lastPath != null ? list.indexOf(lastPath) : -1;
            String pick = list.get(at < 0 ? 0 : (at + 1) % list.size());
            lastCategory = category;
            lastPath = pick;
            return pick;
        }
    }
}
