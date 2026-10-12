package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.resources.MetalPatches;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the faction terraform auras ({@link FactionAura}, design/biome_bases.md): one per built capitol and lab of a
 * faction with a style, growing on a budget and receding when its source is gone.
 * <p>
 * <b>Performance (8v8).</b> Work happens in passes, one every {@link #PASS_TICKS} ticks. A pass visits at most
 * {@link #AURAS_PER_PASS} auras round-robin and converts or restores at most {@link #CELLS_PER_PASS} cells in total
 * ({@link #CELLS_PER_AURA} each); a growing aura looks at no more than FactionAura.EXAMINE_PER_STEP candidates. Sources
 * are re-scanned every {@link #SCAN_TICKS} (one walk over the building list). So the whole server does at most
 * 24 cells (48 block changes) every half second and about 200 block reads, however many bases there are; a capitol's
 * skirt (~120 cells) fills in about a minute when its aura gets every turn, slower in a crowded match.
 * <p>
 * <b>Lifecycle.</b> A source counts once it has stood built for {@link #START_DELAY_TICKS} ("slowly takes the look").
 * Destroyed, decommissioned (from the moment dismantling starts) or the rule {@code doFactionTerraform} turned off:
 * its aura recedes at the same budget, outermost cells first, putting back exactly what was there wherever the block
 * is still the aura's own; finished, it is dropped. A building raised again on the same spot starts a fresh aura,
 * which takes over cells as the old one lets them go. Saved: every aura with its cells and their originals
 * (MatchStateSaveData), so a reload keeps both the look and the way back.
 */
public class FactionAuraServerEvents {

    static final int PASS_TICKS = 10;
    static final int SCAN_TICKS = 40;
    static final int AURAS_PER_PASS = 24;
    static final int CELLS_PER_PASS = 24;
    static final int CELLS_PER_AURA = 1;
    static final long START_DELAY_TICKS = 20 * 20;

    /** The live registry (server thread only). */
    static final Registry SERVER = new Registry();

    /** Every aura and the cells they hold. Instantiable so a GameTest can drive its own without touching the match's. */
    public static final class Registry {
        final List<FactionAura> auras = new ArrayList<>();
        final Long2ObjectOpenHashMap<FactionAura> bySource = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<FactionAura> owners = new Long2ObjectOpenHashMap<>();
        private final FactionAura.Obstacles obstacles = new FactionAura.Obstacles();
        private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        private final LongOpenHashSet seen = new LongOpenHashSet();
        private int next = 0;

        /** Adds an aura that no building drives (GameTests); it grows until {@link FactionAura#setReceding}. */
        public void add(FactionAura aura) {
            auras.add(aura);
        }

        public int auraCount() {
            return auras.size();
        }

        /** The aura holding this converted ground cell, or null. */
        public FactionAura ownerOf(BlockPos cell) {
            return owners.get(cell.asLong());
        }

        public void clear() {
            auras.clear();
            bySource.clear();
            owners.clear();
            next = 0;
        }

        /**
         * One pass: visits up to {@code aurasPerPass} auras round-robin, each growing or receding by at most
         * {@code perAura} cells, until {@code budget} cells have changed; drops auras that have given everything
         * back. Returns the cells changed. A growing aura's obstacles come from the live world (buildings, metal
         * patches, Nether Zones) plus, in a GameTest, {@code extra}.
         */
        public int runPass(ServerLevel level, int budget, int aurasPerPass, int perAura, FactionAura.Obstacles extra) {
            int changed = 0;
            int visits = Math.min(aurasPerPass, auras.size());
            for (int v = 0; v < visits && changed < budget && !auras.isEmpty(); v++) {
                if (next >= auras.size())
                    next = 0;
                FactionAura a = auras.get(next);
                int share = Math.min(perAura, budget - changed);
                if (a.isReceding()) {
                    changed += a.recede(level, share, owners, scratch);
                    if (a.cellCount() == 0) {
                        auras.remove(next);   // the next aura slides into this index: don't advance
                        if (bySource.get(a.sourceKey) == a)
                            bySource.remove(a.sourceKey);
                        continue;
                    }
                } else {
                    collectObstacles(level, a, extra);
                    changed += a.grow(level, share, obstacles, owners, scratch);
                }
                next++;
            }
            return changed;
        }

        /** Buildings (boxes grown by one), metal patches and Nether Zones within reach of the aura. */
        private void collectObstacles(ServerLevel level, FactionAura a, FactionAura.Obstacles extra) {
            obstacles.clear();
            int reach = a.radius + MetalPatches.RESERVED_HALF + 1;
            int minX = a.cx - reach, maxX = a.cx + reach, minZ = a.cz - reach, maxZ = a.cz + reach;
            for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
                if (bp.maxCorner.getX() + 1 < minX || bp.minCorner.getX() - 1 > maxX
                        || bp.maxCorner.getZ() + 1 < minZ || bp.minCorner.getZ() - 1 > maxZ)
                    continue;
                obstacles.addBox(bp.minCorner.getX() - 1, bp.minCorner.getZ() - 1, bp.maxCorner.getX() + 1, bp.maxCorner.getZ() + 1);
            }
            List<BlockPos> patches = MetalPatches.getPatches(level);
            synchronized (patches) {   // a Collections.synchronizedList: iteration must hold its lock
                for (BlockPos c : patches)
                    if (c.getX() >= minX && c.getX() <= maxX && c.getZ() >= minZ && c.getZ() <= maxZ)
                        obstacles.addPatch(c.getX(), c.getZ());
            }
            for (NetherZone nz : BuildingServerEvents.netherZones) {
                int r = (int) Math.ceil(nz.getMaxRange());
                BlockPos o = nz.getOrigin();
                if (o.getX() + r >= minX && o.getX() - r <= maxX && o.getZ() + r >= minZ && o.getZ() - r <= maxZ)
                    obstacles.addZone(o.getX(), o.getZ(), r);
            }
            if (extra != null)
                obstacles.copyFrom(extra);
        }

        /**
         * Matches the auras to the buildings: every source without an aura gets one, every aura whose source is gone
         * (or all of them, when the rule is off) starts receding.
         */
        void scanSources(boolean enabled) {
            seen.clear();
            if (enabled) {
                for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
                    FactionAura.Style style = sourceStyle(bp);
                    if (style == null)
                        continue;
                    long key = bp.originPos.asLong();
                    seen.add(key);
                    FactionAura had = bySource.get(key);
                    if (had != null && !had.fits(bp, style)) {
                        // another faction's (or another kind of) building stands on the old one's spot now
                        had.setReceding(true);
                        bySource.remove(key);
                        had = null;
                    }
                    if (had == null) {
                        FactionAura aura = FactionAura.forBuilding(bp, style);
                        bySource.put(key, aura);
                        auras.add(aura);
                    }
                }
            }
            for (FactionAura a : auras)
                if (!a.isReceding() && bySource.get(a.sourceKey) == a && !seen.contains(a.sourceKey)) {
                    a.setReceding(true);
                    bySource.remove(a.sourceKey);
                }
        }

        // ---- persistence: every aura with its cells; block states through a per-aura palette (ids are not stable
        // across mod sets, NbtUtils' state tags are)

        ListTag saveTag() {
            ListTag list = new ListTag();
            for (FactionAura a : auras) {
                CompoundTag t = new CompoundTag();
                t.putString("owner", a.owner);
                t.putString("style", a.style.name);
                t.putLong("source", a.sourceKey);
                t.putIntArray("geometry", new int[] { a.cx, a.cz, a.groundY, a.halfX, a.halfZ, a.radius });
                t.putInt("cursor", a.cursor);
                t.putBoolean("receding", a.isReceding());
                t.putLongArray("cells", a.cells.toLongArray());
                ListTag palette = new ListTag();
                Int2IntOpenHashMap index = new Int2IntOpenHashMap();
                t.putIntArray("oldGround", encode(a.oldGround.toIntArray(), palette, index));
                t.putIntArray("newGround", encode(a.newGround.toIntArray(), palette, index));
                t.putIntArray("oldCover", encode(a.oldCover.toIntArray(), palette, index));
                t.putIntArray("newCover", encode(a.newCover.toIntArray(), palette, index));
                t.put("palette", palette);
                list.add(t);
            }
            return list;
        }

        private static int[] encode(int[] stateIds, ListTag palette, Int2IntOpenHashMap index) {
            int[] out = new int[stateIds.length];
            for (int i = 0; i < stateIds.length; i++) {
                int id = stateIds[i];
                if (id < 0) {
                    out[i] = -1;
                    continue;
                }
                if (!index.containsKey(id)) {
                    index.put(id, palette.size());
                    palette.add(NbtUtils.writeBlockState(Block.stateById(id)));
                }
                out[i] = index.get(id);
            }
            return out;
        }

        /** Restores saved auras (idempotent: an aura already registered for the same source is skipped). */
        int restore(ListTag list) {
            int restored = 0;
            for (int n = 0; n < list.size(); n++) {
                CompoundTag t = list.getCompound(n);
                FactionAura.Style style = FactionAura.styleNamed(t.getString("style"));
                int[] g = t.getIntArray("geometry");
                if (style == null || g.length != 6) {
                    ReignOfNether.LOGGER.warn("FactionAura: skipping a saved aura with style '{}' ({} geometry values)",
                        t.getString("style"), g.length);
                    continue;
                }
                long source = t.getLong("source");
                if (isRegistered(source))
                    continue;
                FactionAura a = new FactionAura(source, t.getString("owner"), style, g[0], g[1], g[2], g[3], g[4], g[5]);
                a.cursor = Math.max(0, Math.min(a.plan.length, t.getInt("cursor")));
                a.setReceding(t.getBoolean("receding"));
                ListTag palette = t.getList("palette", Tag.TAG_COMPOUND);
                int[] ids = new int[palette.size()];
                for (int i = 0; i < ids.length; i++)
                    ids[i] = Block.getId(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
                long[] cells = t.getLongArray("cells");
                int[] og = t.getIntArray("oldGround"), ng = t.getIntArray("newGround");
                int[] oc = t.getIntArray("oldCover"), nc = t.getIntArray("newCover");
                if (og.length != cells.length || ng.length != cells.length || oc.length != cells.length || nc.length != cells.length) {
                    ReignOfNether.LOGGER.warn("FactionAura: saved aura at {} has mismatched cell arrays; its cells are dropped",
                        BlockPos.of(source));
                    cells = new long[0];
                }
                for (int i = 0; i < cells.length; i++) {
                    if (owners.containsKey(cells[i]))
                        continue;   // a stale double: the cell already has its owner
                    a.addCell(cells[i], decode(og[i], ids), decode(ng[i], ids), decode(oc[i], ids), decode(nc[i], ids));
                    owners.put(cells[i], a);
                }
                auras.add(a);
                if (!a.isReceding())
                    bySource.put(source, a);
                restored++;
            }
            return restored;
        }

        private static int decode(int index, int[] ids) {
            return index < 0 || index >= ids.length ? -1 : ids[index];
        }

        private boolean isRegistered(long source) {
            if (bySource.containsKey(source))
                return true;
            for (FactionAura a : auras)
                if (a.sourceKey == source)
                    return true;
            return false;
        }
    }

    /**
     * The style of a building that drives an aura, or null: built and standing for {@link #START_DELAY_TICKS}, not
     * being dismantled, of a faction with a style, and a capitol or a lab (a production building that makes neither
     * metal nor energy and is not a defence - the army labs, the research halls, the T2/T3 labs).
     */
    static FactionAura.Style sourceStyle(BuildingPlacement bp) {
        if (!bp.isBuilt || bp.decommissioning || bp.isDestroyedServerside || bp.originPos == null
                || bp.getTickAgeAfterBuilt() < START_DELAY_TICKS)
            return null;
        Building b = bp.getBuilding();
        FactionAura.Style style = FactionAura.styleOf(b.getFaction());
        if (style == null)
            return null;
        if (bp.isCapitol)
            return style;
        boolean lab = bp instanceof ProductionPlacement && b.getMetalIncome() <= 0 && b.getEnergyIncome() <= 0
            && !Defences.isDefence(b) && b instanceof ProductionBuilding pb && !pb.productions.get().isEmpty();
        return lab ? style : null;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.level.isClientSide() || evt.level.dimension() != Level.OVERWORLD)
            return;
        ServerLevel level = (ServerLevel) evt.level;
        long t = level.getGameTime();
        if (t % PASS_TICKS != 0)
            return;
        if (t % SCAN_TICKS == 0)
            SERVER.scanSources(level.getGameRules().getBoolean(GameRuleRegistrar.DO_FACTION_TERRAFORM));
        if (SERVER.auraCount() > 0)
            SERVER.runPass(level, CELLS_PER_PASS, AURAS_PER_PASS, CELLS_PER_AURA, null);
    }

    // the registry is snapshotted by MatchStateSaveData's highest-priority stopping handler first; statics outlive an
    // integrated server, so the next world must not inherit this one's auras
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        SERVER.clear();
    }

    /** The live auras as a list tag (MatchStateSaveData). */
    public static ListTag saveTag() {
        return SERVER.saveTag();
    }

    /** Restores saved auras into the live registry (MatchStateSaveData; idempotent). Returns how many. */
    public static int restore(MinecraftServer server, ListTag list) {
        return SERVER.restore(list);
    }
}
