package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.production.SecondT3Prod;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The alive cap of the Tier 3 (second-t3-design.md section 4): <b>one alive of EACH T3 type per player</b>, so at most
 * two per faction. The first alive cap in the game. Queued ones count (a lab that already queues one refuses a second
 * of the same type, so two labs cannot both start one). Server-side and free of client classes, so the rule is
 * testable headless and shared by the production path and (later) the bots.
 * <p>
 * The two types are told apart by item, not by entity: until the real second units exist the second item trains the
 * first's body tagged {@link #TAG_SECOND}. A living body counts toward the second type iff it carries the tag.
 * The cap number is {@link #CAP}; "lift it for BAR-style mass" is that one constant (later a config value).
 */
public final class T3Cap {
    private T3Cap() { }

    /** Alive + queued per player per T3 type. */
    public static final int CAP = 1;
    /** Persistent-data flag on a body that was trained as the SECOND T3 of its faction. */
    public static final String TAG_SECOND = "bt_t3_second";
    /** Lang key of the refusal. */
    public static final String LIMIT_MSG = "hud.reignofnether.t3_limit";

    /** One faction's pair: the first item, its body, and the second item. */
    public record Pair(String faction, ProductionItem first, EntityType<?> firstType, SecondT3Prod second,
                       @Nullable EntityType<?> secondType) {
        /** A pair whose second item still trains the first's body (tagged {@link #TAG_SECOND}). */
        public Pair(String faction, ProductionItem first, EntityType<?> firstType, SecondT3Prod second) {
            this(faction, first, firstType, second, null);
        }
    }

    private static List<Pair> pairs;

    /** The seven pairs, built on first use (the entity types are registry objects, so not before registration). */
    public static synchronized List<Pair> pairs() {
        if (pairs == null) {
            pairs = List.of(
                new Pair("Sunforged", ProductionItems.SUN_COLOSSUS, EntityRegistrar.SUN_COLOSSUS_UNIT.get(), ProductionItems.SUN_T3B, EntityRegistrar.CATHEDRAL_WARDEN_UNIT.get()),
                new Pair("Gravebound", ProductionItems.BONE_DRAGON, EntityRegistrar.BONE_DRAGON_UNIT.get(), ProductionItems.GRAVE_T3B, EntityRegistrar.PLAGUE_ABOMINATION_UNIT.get()),
                new Pair("Horde", ProductionItems.WAR_MAMMOTH, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), ProductionItems.HORDE_T3B, EntityRegistrar.MAGMA_HURLER_UNIT.get()),
                new Pair("Verdant", ProductionItems.WORLD_TREE_WALKER, EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), ProductionItems.VERDANT_T3B),
                new Pair("Tidewrought", ProductionItems.LEVIATHAN, EntityRegistrar.LEVIATHAN_UNIT.get(), ProductionItems.TIDE_T3B, EntityRegistrar.ABYSSAL_HYDRA_UNIT.get()),
                new Pair("Stormcallers", ProductionItems.CELESTIAL_DRAGON, EntityRegistrar.CELESTIAL_DRAGON_UNIT.get(), ProductionItems.STORM_T3B, EntityRegistrar.JADE_SENTINEL_UNIT.get()),
                new Pair("Brassworks", ProductionItems.IRON_LEVIATHAN_ENGINE, EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get(), ProductionItems.BRASS_T3B));
        }
        return pairs;
    }

    /** The pair whose first or second item is {@code item}, else null (not a T3 item). */
    @Nullable
    public static Pair pairOf(ProductionItem item) {
        for (Pair p : pairs())
            if (p.first() == item || p.second() == item)
                return p;
        return null;
    }

    /** How many of {@code item}'s T3 type {@code owner} has alive or queued ({@code pp}'s own queue always included). */
    public static int count(String owner, ProductionItem item, @Nullable ProductionPlacement pp) {
        Pair p = pairOf(item);
        if (p == null || owner == null)
            return 0;
        boolean second = item == p.second();
        int n = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            EntityType<?> want = second && p.secondType() != null ? p.secondType() : p.firstType();
            if (le.isRemoved() || !le.isAlive() || le.getType() != want || !(le instanceof Unit u))
                continue;
            if (!owner.equals(u.getOwnerName()))
                continue;
            // a real second unit has its own type (counted above); a placeholder body is told apart by its tag
            if (second && p.secondType() != null || le.getPersistentData().getBoolean(TAG_SECOND) == second)
                n++;
        }
        Set<ProductionPlacement> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ProductionPlacement> labs = new ArrayList<>();
        if (pp != null)
            labs.add(pp);
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b instanceof ProductionPlacement lab && owner.equals(lab.ownerName))
                labs.add(lab);
        for (ProductionPlacement lab : labs) {
            if (!seen.add(lab))
                continue;
            for (ActiveProduction ap : lab.productionQueue)
                if (ap.item == item)
                    n++;
        }
        return n;
    }

    /** Null if {@code pp}'s owner may start {@code item}, else the lang key of the refusal. Non-T3 items always pass. */
    @Nullable
    public static String productionError(ProductionPlacement pp, ProductionItem item) {
        if (pairOf(item) == null)
            return null;
        return count(pp.ownerName, item, pp) >= CAP ? LIMIT_MSG : null;
    }
}
