package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The Tier 4 frame (hero-and-tier4-design.md, slice H5a): <b>one alive Tier 4 of each type per player</b>, queued ones
 * counted (a Fortress that already queues a Worldbreaker, or any other of the player's labs, refuses a second), and the
 * announcements every player gets when one is started and when it arrives. Server-side and free of client classes, so the
 * rule is testable headless and shared by the production path and the bots (Lane A calls the same production path).
 * <p>
 * The cap number is {@link #CAP}; each further T4 (the other six factions) adds one {@link #entries()} row.
 */
public final class T4Cap {
    private T4Cap() { }

    /** Alive + queued per player per T4 type. */
    public static final int CAP = 1;
    /** Lang key of the refusal. */
    public static final String LIMIT_MSG = "hud.reignofnether.t4_limit";
    /** Lang key of "a Tier 4 was started" (the arrival reuses the experimental line, {@code server.reignofnether.t3_arrived}). */
    public static final String STARTED_MSG = "server.reignofnether.t4_started";

    /** One Tier 4: its production item and its body. */
    public record Entry(ProductionItem item, EntityType<?> type) { }

    private static List<Entry> entries;

    /** The Tier 4 rows, built on first use (entity types are registry objects, so not before registration). */
    public static synchronized List<Entry> entries() {
        if (entries == null)
            entries = List.of(new Entry(ProductionItems.WORLDBREAKER, EntityRegistrar.WORLDBREAKER_UNIT.get()));
        return entries;
    }

    @Nullable
    public static Entry entryOf(ProductionItem item) {
        for (Entry e : entries())
            if (e.item() == item)
                return e;
        return null;
    }

    /** How many of {@code item}'s T4 type {@code owner} has alive or queued ({@code pp}'s own queue always included). */
    public static int count(String owner, ProductionItem item, @Nullable ProductionPlacement pp) {
        Entry e = entryOf(item);
        if (e == null || owner == null)
            return 0;
        int n = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (!le.isRemoved() && le.isAlive() && le.getType() == e.type() && le instanceof Unit u && owner.equals(u.getOwnerName()))
                n++;
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

    /** Null if {@code pp}'s owner may start {@code item}, else the lang key of the refusal. Non-T4 items always pass. */
    @Nullable
    public static String productionError(ProductionPlacement pp, ProductionItem item) {
        if (entryOf(item) == null)
            return null;
        return count(pp.ownerName, item, pp) >= CAP ? LIMIT_MSG : null;
    }

    // ---- announcements (the game test reads what was sent and to how many players)
    /** The last start / arrival line sent: lang key, owner, and how many players it went to. Test hooks. */
    public static String lastStartedOwner = null, lastArrivedOwner = null;
    public static int lastStartedRecipients = -1, lastArrivedRecipients = -1;

    /** A Tier 4 was queued: one bold line to every player. */
    public static void announceStarted(String owner) {
        lastStartedOwner = owner;
        lastStartedRecipients = PlayerServerEvents.players.size();
        PlayerServerEvents.sendMessageToAllPlayersNoNewlines(STARTED_MSG, true, owner,
            Component.translatable("entity.reignofnether.worldbreaker_unit"));
    }

    /** A Tier 4 body arrived: the experimental line (once per owner and unit per minute, T3ServerEvents) to every player. */
    public static void announceArrived(String owner, Component unitName) {
        lastArrivedOwner = owner;
        lastArrivedRecipients = PlayerServerEvents.players.size();
        T3ServerEvents.announceFielded(owner, unitName);
    }
}
