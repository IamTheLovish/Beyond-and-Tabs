package com.solegendary.reignofnether.mana;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Ley Links (design/stormcallers_plan.md section 3.4): the Geomancer's Ley Link order links two own completed buildings
 * within {@link #MAX_DISTANCE} blocks; a connected set of links is a network.
 *
 * <pre>
 *   LeyNetwork.link(owner, posA, aIsGen, posB, bIsGen);  // Refusal NONE, or why not (caps, distance, same building)
 *   LeyNetwork.unlink(owner, posA, posB);      // clicking a linked pair again
 *   LeyNetwork.isLinked(owner, posA, posB);
 *   LeyNetwork.linksOf(owner, pos);            // links touching one building (cap 3)
 *   LeyNetwork.onBuildingGone(owner, pos);     // BuildingPlacement.destroy hook: its links die with it
 *   LeyNetwork.incomeMultiplier(owner, pos);   // Ley resonance for a wind generator (BuildingPlacement.getEnergyIncome)
 * </pre>
 * <b>Ley resonance</b>: a wind generator in a network earns +{@link #RESONANCE_PER_GENERATOR} energy income per
 * <i>other</i> wind generator in the same network, at most +{@link #RESONANCE_CAP}. BuildingPlacement.getEnergyIncome
 * reads it, so EconomyServerEvents' once-a-second income pass picks it up - nothing per tick. Buildings are identified
 * by their placement's {@code centrePos}; whether one is a generator is recorded when it is linked (a building never
 * changes type), so the resonance walk never looks buildings up.
 * <p>
 * Links are stored per owner as pairs of {@link BlockPos#asLong} (posA, posB) in one flat list - at most
 * {@link #LINKS_PER_PLAYER} pairs, so linear scans and a small breadth-first walk are cheaper than any index. Each link
 * is drawn as a thin jade beam (a BarFx nano-beam event, drawn and faded by clients) once every {@link #BEAM_TICKS}
 * ticks - one tiny event per link per 2 s. Not saved (re-link after a reload); cleared on defeat and reset.
 */
public final class LeyNetwork {
    private LeyNetwork() { }

    public static final int LINKS_PER_PLAYER = 24;
    public static final int LINKS_PER_BUILDING = 3;
    public static final int MAX_DISTANCE = 16;
    /** Ley resonance: +10% income per other generator in the network, at most +30%. */
    public static final float RESONANCE_PER_GENERATOR = 0.10f;
    public static final float RESONANCE_CAP = 0.30f;
    /** How often each link's beam is redrawn (the client keeps it a little longer, so it reads as steady). */
    public static final int BEAM_TICKS = 40;

    private static final Map<String, LongArrayList> links = new HashMap<>();
    /** Linked buildings that are wind generators, per owner. */
    private static final Map<String, LongOpenHashSet> generators = new HashMap<>();
    /** The level an owner's links live in (for drawing their beams); set when they link. */
    private static final Map<String, ServerLevel> levels = new HashMap<>();
    private static final LongArrayList walk = new LongArrayList();   // server thread only

    /** {@link #link(String, BlockPos, boolean, BlockPos, boolean)} for two buildings that are not generators. */
    public static ManaServerEvents.Refusal link(String owner, BlockPos a, BlockPos b) {
        return link(owner, a, false, b, false);
    }

    public static ManaServerEvents.Refusal link(String owner, BlockPos a, boolean aIsGenerator, BlockPos b, boolean bIsGenerator) {
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
        LongOpenHashSet gens = generators.computeIfAbsent(owner, k -> new LongOpenHashSet());
        if (aIsGenerator)
            gens.add(a.asLong());
        if (bIsGenerator)
            gens.add(b.asLong());
        return ManaServerEvents.Refusal.NONE;
    }

    /** Remember where an owner's links are drawn (the Ley Link order passes its level). */
    public static void setLevel(String owner, ServerLevel level) {
        if (owner != null && level != null)
            levels.put(owner, level);
    }

    public static void unlink(String owner, BlockPos a, BlockPos b) {
        LongArrayList list = links.get(owner);
        if (list == null)
            return;
        long la = a.asLong(), lb = b.asLong();
        for (int i = list.size() - 2; i >= 0; i -= 2)
            if ((list.getLong(i) == la && list.getLong(i + 1) == lb) || (list.getLong(i) == lb && list.getLong(i + 1) == la))
                list.removeElements(i, i + 2);
        forgetIfUnlinked(owner, la);
        forgetIfUnlinked(owner, lb);
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
        return linksOf(owner, pos.asLong());
    }

    static int linksOf(String owner, long lp) {
        LongArrayList list = links.get(owner);
        if (list == null)
            return 0;
        int n = 0;
        for (int i = 0; i < list.size(); i++)
            if (list.getLong(i) == lp)
                n++;
        return n;
    }

    /** Total links an owner has (the {@link #LINKS_PER_PLAYER} cap). */
    public static int linkCount(String owner) {
        LongArrayList list = links.get(owner);
        return list == null ? 0 : list.size() / 2;
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
        LongOpenHashSet gens = generators.get(owner);
        if (gens != null)
            gens.remove(lp);
    }

    private static void forgetIfUnlinked(String owner, long lp) {
        LongOpenHashSet gens = generators.get(owner);
        if (gens != null && linksOf(owner, lp) == 0)
            gens.remove(lp);
    }

    /** How many other wind generators share the network of the building at pos (0 when it has no links). */
    public static int otherGeneratorsInNetwork(String owner, BlockPos pos) {
        LongArrayList list = links.get(owner);
        LongOpenHashSet gens = generators.get(owner);
        if (list == null || list.isEmpty() || gens == null || gens.isEmpty())
            return 0;
        long start = pos.asLong();
        walk.clear();
        walk.add(start);
        // breadth-first over at most LINKS_PER_PLAYER pairs; "visited" is the walk list itself (a network has <= 25 nodes)
        for (int k = 0; k < walk.size(); k++) {
            long node = walk.getLong(k);
            for (int i = 0; i + 1 < list.size(); i += 2) {
                long a = list.getLong(i), b = list.getLong(i + 1);
                long other = a == node ? b : b == node ? a : Long.MIN_VALUE;
                if (other != Long.MIN_VALUE && !walk.contains(other))
                    walk.add(other);
            }
        }
        int n = 0;
        for (int k = 1; k < walk.size(); k++)
            if (gens.contains(walk.getLong(k)))
                n++;
        walk.clear();
        return n;
    }

    /**
     * Ley resonance income factor for a wind generator at pos: 1 + min({@link #RESONANCE_CAP},
     * {@link #RESONANCE_PER_GENERATOR} x other generators in its network). 1.0 for an owner who never linked anything.
     */
    public static float incomeMultiplier(String owner, BlockPos pos) {
        if (owner == null || pos == null || links.isEmpty())
            return 1f;
        int others = otherGeneratorsInNetwork(owner, pos);
        return others <= 0 ? 1f : 1f + Math.min(RESONANCE_CAP, RESONANCE_PER_GENERATOR * others);
    }

    // ------------------------------------------------------------------ beams

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || links.isEmpty() || evt.getServer() == null
                || evt.getServer().getTickCount() % BEAM_TICKS != 0)
            return;
        for (Map.Entry<String, LongArrayList> e : links.entrySet()) {
            ServerLevel level = levels.get(e.getKey());
            LongArrayList list = e.getValue();
            if (level == null || level.getServer() != evt.getServer())
                continue;
            for (int i = 0; i + 1 < list.size(); i += 2)
                beam(level, list.getLong(i), list.getLong(i + 1));
        }
    }

    /** One link's beam, between the two buildings' centres (a little above the middle of each). */
    static void beam(ServerLevel level, long a, long b) {
        int ax = BlockPos.getX(a), ay = BlockPos.getY(a), az = BlockPos.getZ(a);
        int bx = BlockPos.getX(b), by = BlockPos.getY(b), bz = BlockPos.getZ(b);
        com.solegendary.reignofnether.barfx.BarFx.beam(level, new Vec3(ax + 0.5, ay + 1.5, az + 0.5),
            new Vec3(bx + 0.5, by + 1.5, bz + 0.5), com.solegendary.reignofnether.barfx.BarFx.N_STORM, BEAM_TICKS + 6);
    }

    static void clearOwner(String owner) {
        links.remove(owner);
        generators.remove(owner);
        levels.remove(owner);
    }

    static void clearAll() {
        links.clear();
        generators.clear();
        levels.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        clearAll();
    }
}
