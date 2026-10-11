package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What a hovered building is producing, for its hover card (from the reference clip lovish liked: hover an enemy lab
 * and see what it is making). Server-authoritative and fog-gated: the client asks with
 * {@link BuildingHoverServerboundPacket} (at most a few times a second while it hovers a building it doesn't own),
 * and gets the front item and its progress back only when that player can see the building right now - an unseen
 * building answers "nothing". {@link #describe} is the pure decision, game-tested in
 * enemy_production_info_only_for_visible_buildings.
 */
public final class BuildingHoverInfo {

    /** The front of the queue: the item's registry key (or its name), progress 0..1, and how many are queued. */
    public record Info(String itemKey, float progress, int queued) { }

    // per-player request throttle: a client that spams the packet gets at most one answer per MIN_INTERVAL_MS
    static final long MIN_INTERVAL_MS = 200;
    private static final Map<UUID, Long> lastAnswerMs = new HashMap<>();

    private BuildingHoverInfo() { }

    /** Null when the viewer may not know (can't see it) or nothing is being produced there. */
    @Nullable
    public static Info describe(@Nullable BuildingPlacement b, boolean viewerCanSee) {
        if (!viewerCanSee || !(b instanceof ProductionPlacement pp) || pp.productionQueue.isEmpty())
            return null;
        ActiveProduction front = pp.productionQueue.get(0);
        if (front == null || front.item == null)
            return null;
        float total = front.item.getCost(false, pp.ownerName).ticks;
        float progress = total <= 0 ? front.paidFraction : 1f - front.ticksLeft / total;
        progress = Math.max(0f, Math.min(1f, progress));
        ResourceLocation key = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(front.item);
        return new Info(key != null ? key.toString() : front.item.getItemName(), progress, pp.productionQueue.size());
    }

    /** Owner, ally or inside the player's vision right now (FogOfWarServerEvents, the same test production packets use). */
    public static boolean canSee(ServerPlayer sp, BuildingPlacement b) {
        String name = sp.getName().getString();
        return name.equals(b.ownerName) || AlliancesServerEvents.isAllied(name, b.ownerName)
            || FogOfWarServerEvents.canPlayerSeeBuilding(sp, b);
    }

    /** From the serverbound packet (main thread). */
    static void answer(ServerPlayer sp, net.minecraft.core.BlockPos pos) {
        if (sp == null || pos == null)
            return;
        long now = System.currentTimeMillis();
        Long last = lastAnswerMs.get(sp.getUUID());
        if (last != null && now - last < MIN_INTERVAL_MS)
            return;
        lastAnswerMs.put(sp.getUUID(), now);
        BuildingPlacement b = BuildingUtils.findBuilding(false, pos);
        Info info = b == null ? null : describe(b, canSee(sp, b));
        BuildingHoverClientboundPacket.send(sp, pos, info);
    }

    public static void forget(UUID player) {
        lastAnswerMs.remove(player);
    }
}
