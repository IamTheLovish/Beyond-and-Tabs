package com.solegendary.reignofnether.mana;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Ley Links (design/stormcallers_plan.md section 3.4): the Geomancer links two own completed buildings within
 * {@link #MAX_DISTANCE} blocks; a connected set of links is a network. This is the slice-1 data hook only - the
 * Geomancer's ability, the particle beam and the resonance bonus are built on it with the units.
 *
 * <pre>
 *   LeyNetwork.link(owner, posA, posB);        // Refusal NONE, or why not (caps, distance, same building)
 *   LeyNetwork.unlink(owner, posA, posB);      // clicking a linked pair again
 *   LeyNetwork.isLinked(owner, posA, posB);
 *   LeyNetwork.linksOf(owner, pos);            // links touching one building (cap 3)
 *   LeyNetwork.onBuildingGone(owner, pos);     // Building.destroy hook: its links die with it
 *   LeyNetwork.incomeMultiplier(owner, pos);   // Ley resonance for a generator; 1.0 until the wind generator reads it
 * </pre>
 * Links are stored per owner as pairs of {@link BlockPos#asLong} (posA, posB) in one flat list - at most
 * {@link #LINKS_PER_PLAYER} pairs, so linear scans are cheaper than any index. Not saved (re-link after a reload).
 */
public final class LeyNetwork {
    private LeyNetwork() { }

    public static final int LINKS_PER_PLAYER = 24;
    public static final int LINKS_PER_BUILDING = 3;
    public static final int MAX_DISTANCE = 16;
    /** Ley resonance: +10% income per other generator in the network, at most +30%. */
    public static final float RESONANCE_PER_GENERATOR = 0.10f;
    public static final float RESONANCE_CAP = 0.30f;

    private static final Map<String, LongArrayList> links = new HashMap<>();

    public static ManaServerEvents.Refusal link(String owner, BlockPos a, BlockPos b) {
        if (owner == null || a == null || b == null || a.equals(b) || a.distSqr(b) > MAX_DISTANCE * MAX_DISTANCE)
            return ManaServerEvents.Refusal.LINK_INVALID;
        if (isLinked(owner, a, b))
            return ManaServerEvents.Refusal.NONE;
        LongArrayList list = links.computeIfAbsent(owner, k -> new LongArrayList());
        if (list.size() / 2 >= LINKS_PER_PLAYER || linksOf(owner, a) >= LINKS_PER_BUILDING
            || linksOf(owner, b) >= LINKS_PER_BUILDING)
            return ManaServerEvents.Refusal.LINK_CAP;
        list.add(a.asLong());
        list.add(b.asLong());
        return ManaServerEvents.Refusal.NONE;
    }

    public static void unlink(String owner, BlockPos a, BlockPos b) {
        LongArrayList list = links.get(owner);
        if (list == null)
            return;
        long la = a.asLong(), lb = b.asLong();
        for (int i = list.size() - 2; i >= 0; i -= 2)
            if ((list.getLong(i) == la && list.getLong(i + 1) == lb) || (list.getLong(i) == lb && list.getLong(i + 1) == la))
                list.removeElements(i, i + 2);
    }

    public static boolean isLinked(String owner, BlockPos a, BlockPos b) {
        LongArrayList list = links.get(owner);
        if (list == null)
            return false;
        long la = a.asLong(), lb = b.asLong();
        for (int i = 0; i + 1 < list.size(); i += 2)
            if ((list.getLong(i) == la && list.getLong(i + 1) == lb) || (list.getLong(i) == lb && list.getLong(i + 1) == la))
                return true;
        return false;
    }

    public static int linksOf(String owner, BlockPos pos) {
        LongArrayList list = links.get(owner);
        if (list == null)
            return 0;
        long lp = pos.asLong();
        int n = 0;
        for (int i = 0; i < list.size(); i++)
            if (list.getLong(i) == lp)
                n++;
        return n;
    }

    /** A linked building died or was sold: every link touching it goes. */
    public static void onBuildingGone(String owner, BlockPos pos) {
        LongArrayList list = links.get(owner);
        if (list == null)
            return;
        long lp = pos.asLong();
        for (int i = list.size() - 2; i >= 0; i -= 2)
            if (list.getLong(i) == lp || list.getLong(i + 1) == lp)
                list.removeElements(i, i + 2);
    }

    /**
     * Ley resonance income factor for a generator at pos. Stub: 1.0 until the Stormcaller wind generator reads it
     * (slice 1 units/buildings); then 1 + min(RESONANCE_CAP, RESONANCE_PER_GENERATOR x other generators in the network).
     */
    public static float incomeMultiplier(String owner, BlockPos pos) {
        return 1f;
    }

    static void clearOwner(String owner) {
        links.remove(owner);
    }

    static void clearAll() {
        links.clear();
    }
}
