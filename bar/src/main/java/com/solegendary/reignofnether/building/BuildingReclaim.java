package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.fogofwar.FrozenChunkClientboundPacket;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Decommission: reclaiming your own building for metal (design/building_upgrades.md). lovish: "be able to reclaim
 * buildings that aren't necessary to use resources elsewhere".
 *
 * A Decommission order (the building's button, BuildingAction.DECOMMISSION, owner-checked in
 * BuildingServerboundPacket) marks a built building; from then on it dismantles itself at {@link #SELF_POWER} build
 * power plus the build power of every own worker on it (a build/repair order on a decommissioning building becomes
 * dismantling - BuildingPlacement skips the repair - and area reclaim sends workers to such buildings too). The work
 * needed is max({@link #MIN_WORK_SECONDS}, refund / {@link #METAL_PER_POWER_SECOND}) seconds at 1 build power; metal
 * is paid as the work advances (stalling, like wreck reclaim, while storage is full), the structure comes down from
 * the top, and at the end the building is removed quietly - no wreck, no explosion.
 *
 * Irreversible: a cancelled decommission could be repaired back up for free and decommissioned again for metal.
 */
public final class BuildingReclaim {
    private BuildingReclaim() { }

    /** Share of the metal invested (cost + completed levels) a decommission returns. Energy is never returned. */
    public static final float REFUND_SHARE = 0.6f;
    /** The building takes itself apart at this build power even with no worker on it. */
    public static final float SELF_POWER = 0.5f;
    public static final float METAL_PER_POWER_SECOND = 2.5f;
    public static final float MIN_WORK_SECONDS = 15f;
    static final int STEP_TICKS = 10;

    private static final List<BuildingPlacement> active = new ArrayList<>();

    /** Metal invested in a placement: its building's metal cost plus every level it reached. */
    public static int metalInvested(BuildingPlacement bp) {
        ResourceCost cost = bp.getBuilding().cost;
        int m = cost == null ? 0 : cost.metal();
        if (bp.getBuilding().maxLevel > 1)
            m += BuildingLevels.levelMetalInvested(bp.getBuildingLevel());
        return m;
    }

    public static int refundFor(BuildingPlacement bp) {
        return Math.round(metalInvested(bp) * REFUND_SHARE);
    }

    /** Seconds of 1-build-power work a decommission of this building takes. */
    public static float workSeconds(BuildingPlacement bp) {
        return Math.max(MIN_WORK_SECONDS, refundFor(bp) / METAL_PER_POWER_SECOND);
    }

    /** The kinds of building that may ever be decommissioned: owned, not a capitol, not capturable, not a bridge. */
    public static boolean canEverDecommission(BuildingPlacement bp) {
        return bp != null && bp.ownerName != null && !bp.ownerName.isEmpty() && !bp.isCapitol
            && !bp.getBuilding().capturable && !bp.getBuilding().invulnerable
            && !(bp.getBuilding() instanceof AbstractBridge);
    }

    /** Why this building can't be decommissioned right now (an i18n key), or null if it can. Side-neutral. */
    public static String refusal(BuildingPlacement bp, boolean isClientSide) {
        if (!canEverDecommission(bp))
            return "hud.reignofnether.decommission.not_this";
        if (!bp.isBuilt)
            return "hud.reignofnether.decommission.not_built";
        if (bp.decommissioning)
            return "hud.reignofnether.decommission.already";
        if (!SandboxServer.isSandboxPlayer(bp.ownerName)
                && BuildingUtils.getTotalCompletedBuildingsOwned(isClientSide, bp.ownerName) <= 1)
            return "hud.reignofnether.decommission.last_building";
        return null;
    }

    /** Server: start decommissioning (the owner check was done by the packet). Returns true if it started. */
    public static boolean start(BuildingPlacement bp, String playerName) {
        if (bp == null || bp.getLevel() == null || bp.getLevel().isClientSide())
            return false;
        String why = refusal(bp, false);
        if (why != null) {
            HudClientboundPacket.showTempMessageI18n(playerName, why);
            return false;
        }
        bp.decommissioning = true;
        bp.reclaimProgress = 0;
        bp.reclaimPaid = 0;
        // a lab stops producing: refund its queue (what was spent so far) like a cancel would
        if (bp instanceof ProductionPlacement pp && !pp.productionQueue.isEmpty()) {
            while (!pp.productionQueue.isEmpty())
                pp.cancelProductionItem(pp.productionQueue.get(0).item, true);
            BuildingProductionClientboundPacket.clearQueue(pp.ownerName, pp.originPos);
        }
        if (!active.contains(bp))
            active.add(bp);
        return true;
    }

    public static boolean isDecommissioning(BuildingPlacement bp) {
        return bp != null && bp.decommissioning;
    }

    // ---- save / load (design/code_review.md CR-02) ----
    // A decommission used to be "not saved - a reload simply leaves the building standing". But the dismantled blocks
    // and the refunded metal ARE saved (chunks, resource pool), so a reload left a half-dismantled, already-refunded
    // building that could be repaired for free and decommissioned again: the exact loop the class comment rules out.
    // The progress now rides in the building's per-placement save data, and the work resumes after a reload.

    /** {progress, paid} of a decommissioning building; absent (null) on every other building. */
    public static final com.solegendary.reignofnether.building.data.DataType<float[]> DECOMMISSION =
        com.solegendary.reignofnether.building.data.DataType.createRegistered(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                com.solegendary.reignofnether.ReignOfNether.MOD_ID, "decommission"),
            (tag, server) -> tag.contains("progress") ? new float[] { tag.getFloat("progress"), tag.getFloat("paid") } : null,
            v -> {
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                if (v != null && v.length >= 2) {
                    tag.putFloat("progress", v[0]);
                    tag.putFloat("paid", v[1]);
                }
                return tag;
            },
            () -> null);

    /** BuildingServerEvents.saveBuildings: record the dismantling progress (decommissioning is irreversible). */
    public static void writeSaveData(BuildingPlacement bp) {
        if (bp != null && bp.decommissioning)
            bp.getDataStorage().setData(DECOMMISSION, new float[] { bp.reclaimProgress, bp.reclaimPaid });
    }

    /** BuildingServerEvents.loadBuildings: a building saved mid-decommission carries on where it stopped. */
    public static void restoreFromSave(BuildingPlacement bp) {
        if (bp == null || bp.getLevel() == null || bp.getLevel().isClientSide())
            return;
        float[] d = bp.getDataStorage().getData(DECOMMISSION);
        if (d == null || d.length < 2)
            return;
        bp.decommissioning = true;
        bp.reclaimProgress = Math.max(0f, Math.min(1f, d[0]));
        bp.reclaimPaid = Math.max(0f, d[1]);
        if (!active.contains(bp))
            active.add(bp);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || active.isEmpty())
            return;
        ServerLevel level = evt.getServer().overworld();
        if (level.getGameTime() % STEP_TICKS != 0)
            return;
        tick(STEP_TICKS / 20f);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        active.clear();
    }

    /** One dismantling step covering {@code seconds}. Public for the game test. */
    public static void tick(float seconds) {
        for (int i = active.size() - 1; i >= 0; i--) {
            BuildingPlacement bp = active.get(i);
            if (bp.isDestroyedServerside || !BuildingServerEvents.getBuildings().contains(bp)) {
                active.remove(i);   // killed first: the rest of the refund dies with it
                continue;
            }
            float power = SELF_POWER;
            for (WorkerUnit w : bp.getBuilders())
                power += w.getBuildPower();
            // economy pace, like construction, production and wreck reclaim (EconomyPace): a Relaxed match must not
            // dismantle relatively faster than it builds (CR-16)
            power *= com.solegendary.reignofnether.resources.EconomyPace.of(bp.ownerName);
            float dProgress = power * seconds / workSeconds(bp);
            int refund = refundFor(bp);
            if (refund > 0) {
                float want = refund * Math.min(dProgress, 1f - bp.reclaimProgress);
                float got = EconomyServerEvents.addReclaimedMetal(bp.ownerName, want);
                if (want > 0 && got < want)
                    dProgress *= got / want;   // storage full: the dismantling waits, the metal isn't thrown away
                bp.reclaimPaid += got;
            }
            bp.reclaimProgress = Math.min(1f, bp.reclaimProgress + dProgress);
            bp.dismantleTo(1f - bp.reclaimProgress);
            if (bp.reclaimProgress >= 0.999f) {
                active.remove(i);
                finish(bp);
            }
        }
    }

    /** The last step: the building is taken off the map quietly (no wreck, no explosion, no kill-feed line). */
    static void finish(BuildingPlacement bp) {
        if (!(bp.getLevel() instanceof ServerLevel level))
            return;
        if (!BuildingServerEvents.getBuildings().remove(bp))
            return;
        FrozenChunkClientboundPacket.setBuildingDestroyedServerside(bp.originPos);
        int paid = Math.round(bp.reclaimPaid);
        if (paid > 0)
            ResourcesClientboundPacket.showFloatingText(new Resources(bp.ownerName, 0, 0, paid), bp.centrePos);
        bp.demolish(level);
        BuildingClientboundPacket.removeBuilding(bp.originPos);
    }
}
