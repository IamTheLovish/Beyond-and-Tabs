package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProdDupeRule;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * BAR's multi-building command card (double-click a building: every own building of that type on screen; Ctrl:
 * the whole map). One click on a selection of N buildings sends ONE packet naming all of them:
 * <ul>
 *   <li>{@link Action#QUEUE} - a unit (any repeatable item): queued round-robin across the selected factories, one
 *       factory per click, like BAR's factory groups. A per-player cursor remembers where the last click went;
 *       a factory that refuses (full queue) is skipped for the next one.</li>
 *   <li>{@link Action#APPLY} - a per-building refit/upgrade (DISALLOW_FOR_BUILDING items, e.g. the Tier 2
 *       extractor): started on every selected building that takes it, each paying its own cost. In the flow economy
 *       nothing is paid upfront, so "unaffordable" means what is banked: a building is skipped once the refits
 *       already started by this click would need more metal or energy than the pool holds - a stalled batch would
 *       crawl everywhere at once (BAR stall slows everything evenly), and a refitting extractor's drills stop. The
 *       first building always starts, as a single click always does. Player-wide research (DISALLOW) never comes
 *       here: the client sends it to one building, so tier research is only ever bought once.</li>
 *   <li>{@link Action#SET_REPEAT} - every selected factory's Repeat set to the same value (not flipped one by one,
 *       or a mixed selection would stay mixed).</li>
 * </ul>
 * Every building is checked against {@link PacketAuth#mayActFor} on its own; buildings the sender may not command
 * are dropped silently. Rally points need no packet here: the client already sends one per selected factory.
 */
public class BuildingBatchServerboundPacket {

    public enum Action { QUEUE, APPLY, SET_REPEAT }

    /** A click never names more than this many buildings (a whole-map selection of a big base still fits). */
    public static final int MAX_BUILDINGS = 64;

    public final Action action;
    public final String itemName;
    public final int value;
    public final List<BlockPos> positions;

    public BuildingBatchServerboundPacket(Action action, String itemName, int value, List<BlockPos> positions) {
        this.action = action;
        this.itemName = itemName == null ? "" : itemName;
        this.value = value;
        this.positions = positions.size() > MAX_BUILDINGS ? new ArrayList<>(positions.subList(0, MAX_BUILDINGS)) : positions;
    }

    public static void send(Action action, String itemName, int value, List<BlockPos> positions) {
        if (positions == null || positions.isEmpty())
            return;
        PacketHandler.INSTANCE.sendToServer(new BuildingBatchServerboundPacket(action, itemName, value, positions));
    }

    public BuildingBatchServerboundPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.itemName = buf.readUtf(256);
        this.value = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), MAX_BUILDINGS);
        this.positions = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            positions.add(buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(itemName, 256);
        buf.writeVarInt(value);
        int n = Math.min(positions.size(), MAX_BUILDINGS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++)
            buf.writeBlockPos(positions.get(i));
    }

    // ------------------------------------------------------------------ server logic (public for the GameTests)

    /** Where each player's next round-robin click starts (index into the selection it names). */
    private static final Map<String, Integer> cursors = new ConcurrentHashMap<>();

    /**
     * Queues {@code item} on one of {@code factories}, starting at index {@code start} and moving on past any that
     * refuse. Returns the index that took it, or -1 if none did.
     */
    public static int queueRoundRobin(List<ProductionPlacement> factories, ProductionItem item, int start) {
        if (factories.isEmpty() || item == null)
            return -1;
        // a lab with an upgrade queued makes nothing until it finishes (design/building_upgrades.md): units go to the
        // others first (tester, #38 item 42); behind the upgrade only when no other lab takes them
        return MultiSelectRules.pickRoundRobin(factories.size(), start,
            i -> !upgradeQueued(factories.get(i)),
            i -> factories.get(i).isBuilt && factories.get(i).startProductionItem(item));
    }

    /** Whether a level-upgrade order is in {@code pp}'s queue. */
    public static boolean upgradeQueued(ProductionPlacement pp) {
        for (var ap : pp.productionQueue)
            if (ap.item instanceof com.solegendary.reignofnether.research.researchItems.UpgradeLabLevel)
                return true;
        return false;
    }

    /**
     * Starts {@code item} on every factory that takes it while the batch's committed metal/energy still fits in
     * the owner's pool (the first always starts). Returns the factories that started it, in order.
     */
    public static List<ProductionPlacement> applyEach(List<ProductionPlacement> factories, ProductionItem item) {
        List<ProductionPlacement> started = new ArrayList<>();
        if (item == null)
            return started;
        float committedMetal = 0, committedEnergy = 0;
        for (ProductionPlacement pp : factories) {
            if (!pp.isBuilt)
                continue;
            // a per-building order (a level upgrade) already in this queue is never started twice; checked on the queue
            // itself so it holds for a placement the world list does not know about too
            if (item.dupeRule == com.solegendary.reignofnether.building.production.ProdDupeRule.DISALLOW_FOR_BUILDING) {
                boolean queued = false;
                for (var ap : pp.productionQueue)
                    if (ap.item == item)
                        queued = true;
                if (queued)
                    continue;
            }
            ResourceCost cost = item.getCost(false, pp.ownerName);
            if (!started.isEmpty()) {
                Resources pool = pool(pp.ownerName);
                // no pool = a neutral/sandbox owner whose flow is free (EconomyServerEvents.requestFlow): no limit
                if (pool != null && (committedMetal + cost.metal() > pool.getMetal() + 0.001f
                        || committedEnergy + cost.energy() > pool.getEnergy() + 0.001f))
                    continue;
            }
            if (pp.startProductionItem(item)) {
                started.add(pp);
                committedMetal += cost.metal();
                committedEnergy += cost.energy();
            }
        }
        return started;
    }

    private static Resources pool(String owner) {
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                return r;
        return null;
    }

    private static ProductionItem resolve(ProductionPlacement pp, String name) {
        if (pp instanceof CustomBuildingPlacement cbp)
            return cbp.getProductionItem(name);
        ResourceLocation rl = ResourceLocation.tryParse(name);
        return rl == null ? null : ReignOfNetherRegistries.PRODUCTION_ITEM.get(rl);
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            // one token per click, the same bucket as single-building production clicks
            if (player == null || !PacketAuth.allow(player, "production", 200, 400))
                return;
            List<ProductionPlacement> factories = new ArrayList<>();
            for (BlockPos pos : positions) {
                BuildingPlacement b = BuildingUtils.findBuilding(false, pos);
                if (b instanceof ProductionPlacement pp && !factories.contains(pp) && PacketAuth.mayActFor(player, pp.ownerName))
                    factories.add(pp);
            }
            if (factories.isEmpty())
                return;
            String me = player.getName().getString();
            switch (action) {
                case SET_REPEAT -> {
                    boolean on = value != 0;
                    for (ProductionPlacement pp : factories) {
                        if (pp.repeatQueue == on)
                            continue;
                        pp.setRepeatQueue(on);
                        BuildingProductionClientboundPacket.toggleRepeat(pp.ownerName, pp.originPos, on);
                    }
                }
                case QUEUE -> {
                    ProductionItem item = resolve(factories.get(0), itemName);
                    if (item == null)
                        return;
                    int at = queueRoundRobin(factories, item, cursors.getOrDefault(me, 0));
                    if (at >= 0) {
                        cursors.put(me, at + 1);
                        ProductionPlacement pp = factories.get(at);
                        BuildingProductionClientboundPacket.startProduction(pp.ownerName, pp.originPos, itemName);
                    }
                }
                case APPLY -> {
                    ProductionItem item = resolve(factories.get(0), itemName);
                    // player-wide research is bought once: only per-building items are applied to every building
                    if (item == null || item.dupeRule != ProdDupeRule.DISALLOW_FOR_BUILDING)
                        return;
                    for (ProductionPlacement pp : applyEach(factories, item))
                        BuildingProductionClientboundPacket.startProduction(pp.ownerName, pp.originPos, itemName);
                }
            }
            ReignOfNether.LOGGER.info("[Building] {} batch {} {} on {} building(s)", me, action, itemName, factories.size());
        });
        ctx.get().setPacketHandled(true);
        return true;
    }
}
