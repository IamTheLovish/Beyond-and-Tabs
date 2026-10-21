package com.solegendary.reignofnether.fogofwar;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.perf.SectionRebuilds;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Which chunk sections a fog-of-war change re-meshes (Oct 21 perf plan, PR R1). lovish: "we don't have to render the
 * entire underground and can limit it to help with performance".
 *
 * The fog tint is baked into the block meshes (FogTintingBlockColor, BiomeColorsMixin, LiquidBlockRendererMixin), so
 * a chunk whose visibility changes is re-meshed. Until R1 every change marked the whole column dirty - 24 sections from
 * bedrock to the sky, on the chunk and its 8 neighbours, 216 per flipped chunk - though the RTS camera only ever sees
 * the surface. In lovish's 3-player match (Oct 21, 600 units, client 120 -> 33-55 fps) every moving army flipped fog
 * chunks and edge masks all the time, and on the host the mesher threads took CPU from a server already 20 s behind.
 *
 * While the RTS camera is on, a change now re-meshes only the sections that camera can show, "the surface band":
 * <ul>
 *   <li>from one section under the lowest surface round the chunk (heightmap WORLD_SURFACE over the chunk and the ring of
 *       columns round it: a cliff whose foot is in the next chunk shows its face), down through water and leaves (a lake
 *       or sea bed; the ground under a canopy whose leaves hide round the cursor) - {@link FogSurfaceBand#low};</li>
 *   <li>up to the highest section with any block in it: trees, buildings, terrain remembered under the fog - everything
 *       above is air, which has no mesh to tint ({@link FogSurfaceBand#high});</li>
 *   <li>plus the full height of every building standing in the chunk (an interior seen through glass, a tall spire).</li>
 * </ul>
 * The sections underneath keep their mesh and are remembered as stale. The RTS camera cannot see them: it looks down
 * from the sky, and the renderer's own occlusion pass already culls sealed caves (nothing in the game goes underground
 * where the RTS camera could follow - the Drill Burrower hides while it burrows). The moment the camera can - first
 * person (possession, the Minecraft view) - every stale column is re-meshed once, and while the camera stays in first
 * person every change re-meshes the full column as before. A block change re-meshes its own sections anyway (vanilla's
 * setBlockDirty takes the neighbours too), so a crater opening into a stale section shows the right tint.
 *
 * {@link Mode#FULL} keeps the old whole-column path for an A/B: the eyes tour's fog scene (17) alternates the two, and
 * -Dbat.fogRemesh=full turns it on for a whole session. Every 10 s with fog on, a {@code [FogRemesh]} line logs the
 * sections marked and skipped, the renderer's rebuilds ({@link SectionRebuilds}) and the band's own cost.
 */
public final class FogRemesh {
    private FogRemesh() { }

    public enum Mode { SURFACE, FULL }

    /** SURFACE ships; FULL is the old whole-column path (the eyes A/B, or -Dbat.fogRemesh=full for a session). */
    public static volatile Mode mode = "full".equalsIgnoreCase(System.getProperty("bat.fogRemesh")) ? Mode.FULL : Mode.SURFACE;

    private static final Minecraft MC = Minecraft.getInstance();

    /** What the camera sees through to the blocks under it. */
    private static final Predicate<BlockState> SEE_THROUGH =
            s -> s.is(BlockTags.LEAVES) || s.getFluidState().is(FluidTags.WATER);

    private static final long NO_SPAN = Long.MIN_VALUE;

    // chunks (ChunkPos.toLong) whose sections under the band still carry the tint of an older fog state
    private static final LongOpenHashSet stale = new LongOpenHashSet();
    private static ClientLevel staleLevel = null;

    // running totals, client thread only (fog packets are handled there)
    private static long updates = 0, marked = 0, skipped = 0, flushed = 0, bandNanos = 0;

    /** Running totals since the game started; {@link #totals} minus an earlier one gives a window. */
    public record Totals(long updates, long marked, long skipped, long flushed, long rebuilt, long rebuiltEmpty,
                         long bandNanos) {
        public Totals minus(Totals o) {
            return new Totals(updates - o.updates, marked - o.marked, skipped - o.skipped, flushed - o.flushed,
                    rebuilt - o.rebuilt, rebuiltEmpty - o.rebuiltEmpty, bandNanos - o.bandNanos);
        }
    }

    public static Totals totals() {
        return new Totals(updates, marked, skipped, flushed, SectionRebuilds.built(), SectionRebuilds.empty(), bandNanos);
    }

    public static int staleChunks() {
        return stale.size();
    }

    /** FogOfWarClientEvents: these chunks' fog tint changed (each flipped chunk plus its 8 neighbours). */
    static void markDirty(Set<ChunkPos> chunks) {
        ClientLevel level = MC.level;
        LevelRenderer renderer = MC.levelRenderer;
        if (level == null || renderer == null || chunks.isEmpty())
            return;
        if (level != staleLevel) {
            stale.clear();
            staleLevel = level;
        }
        updates++;
        int minS = level.getMinSection(), maxS = level.getMaxSection();   // maxS exclusive
        if (mode == Mode.FULL || !OrthoviewClientEvents.isEnabled()) {
            for (ChunkPos c : chunks) {
                markColumn(renderer, c.x, c.z, minS, maxS);
                marked += maxS - minS;
                stale.remove(c.toLong());
            }
            return;
        }
        long t0 = System.nanoTime();
        Long2LongOpenHashMap buildingSpans = buildingSpans(chunks);
        for (ChunkPos c : chunks) {
            LevelChunk chunk = level.getChunkSource().getChunk(c.x, c.z, false);
            if (chunk == null) {   // not loaded: nothing drawn there, nothing to keep apart
                markColumn(renderer, c.x, c.z, minS, maxS);
                marked += maxS - minS;
                stale.remove(c.toLong());
                continue;
            }
            int lo = FogSurfaceBand.low(lowestTop(level, chunk), minS, maxS, s -> seeThrough(chunk, s));
            int hi = FogSurfaceBand.high(highestNonEmptySection(chunk), lo, maxS);
            long span = buildingSpans.get(c.toLong());
            if (span != NO_SPAN) {
                lo = Math.max(minS, Math.min(lo, (int) (span >> 32)));
                hi = Math.min(maxS - 1, Math.max(hi, (int) span));
            }
            for (int s = lo; s <= hi; s++)
                renderer.setSectionDirty(c.x, s, c.z);
            marked += hi - lo + 1;
            skipped += (maxS - minS) - (hi - lo + 1);
            if (lo > minS)
                stale.add(c.toLong());   // (above hi is air: nothing there to go stale)
            else
                stale.remove(c.toLong());
        }
        bandNanos += System.nanoTime() - t0;
    }

    /** LevelRenderer.allChanged re-meshes everything from scratch: nothing is stale after it. */
    static void forgetStale() {
        stale.clear();
    }

    private static void markColumn(LevelRenderer renderer, int x, int z, int minS, int maxS) {
        for (int s = minS; s < maxS; s++)
            renderer.setSectionDirty(x, s, z);
    }

    /** The camera can see underground now: re-mesh every column whose lower sections kept an old tint. */
    private static void flushStale(ClientLevel level, LevelRenderer renderer) {
        if (stale.isEmpty())
            return;
        int minS = level.getMinSection(), maxS = level.getMaxSection();
        int n = 0;
        for (LongIterator it = stale.iterator(); it.hasNext(); ) {
            long key = it.nextLong();
            markColumn(renderer, ChunkPos.getX(key), ChunkPos.getZ(key), minS, maxS);
            n++;
        }
        flushed += (long) n * (maxS - minS);
        ReignOfNether.LOGGER.info("[FogRemesh] first-person camera: re-meshing {} columns whose underground kept an older fog tint", n);
        stale.clear();
    }

    /** The lowest top block over the chunk and the ring of columns round it (a face on the edge looks out over the next chunk). */
    private static int lowestTop(ClientLevel level, LevelChunk chunk) {
        int min = Integer.MAX_VALUE;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                min = Math.min(min, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        ChunkPos p = chunk.getPos();
        LevelChunk n = level.getChunkSource().getChunk(p.x, p.z - 1, false);
        LevelChunk s = level.getChunkSource().getChunk(p.x, p.z + 1, false);
        LevelChunk w = level.getChunkSource().getChunk(p.x - 1, p.z, false);
        LevelChunk e = level.getChunkSource().getChunk(p.x + 1, p.z, false);
        for (int i = 0; i < 16; i++) {
            if (n != null) min = Math.min(min, n.getHeight(Heightmap.Types.WORLD_SURFACE, i, 15));
            if (s != null) min = Math.min(min, s.getHeight(Heightmap.Types.WORLD_SURFACE, i, 0));
            if (w != null) min = Math.min(min, w.getHeight(Heightmap.Types.WORLD_SURFACE, 15, i));
            if (e != null) min = Math.min(min, e.getHeight(Heightmap.Types.WORLD_SURFACE, 0, i));
        }
        return min;
    }

    private static boolean seeThrough(LevelChunk chunk, int sectionY) {
        int i = chunk.getSectionIndexFromSectionY(sectionY);
        LevelChunkSection[] sections = chunk.getSections();
        if (i < 0 || i >= sections.length)
            return false;
        LevelChunkSection sec = sections[i];
        return !sec.hasOnlyAir() && sec.maybeHas(SEE_THROUGH);
    }

    private static int highestNonEmptySection(LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = sections.length - 1; i >= 0; i--)
            if (!sections[i].hasOnlyAir())
                return chunk.getSectionYFromSectionIndex(i);
        return Integer.MIN_VALUE;
    }

    /** Section span (low << 32 | high) of the buildings standing in each of these chunks: interiors, spires. */
    private static Long2LongOpenHashMap buildingSpans(Set<ChunkPos> chunks) {
        Long2LongOpenHashMap spans = new Long2LongOpenHashMap();
        spans.defaultReturnValue(NO_SPAN);
        LongOpenHashSet keys = new LongOpenHashSet(chunks.size());
        for (ChunkPos c : chunks)
            keys.add(c.toLong());
        for (BuildingPlacement b : BuildingClientEvents.getBuildings()) {
            if (b.minCorner == null || b.maxCorner == null)
                continue;
            int lo = b.minCorner.getY() >> 4, hi = b.maxCorner.getY() >> 4;
            for (int cx = b.minCorner.getX() >> 4; cx <= b.maxCorner.getX() >> 4; cx++)
                for (int cz = b.minCorner.getZ() >> 4; cz <= b.maxCorner.getZ() >> 4; cz++) {
                    long key = ChunkPos.asLong(cx, cz);
                    if (!keys.contains(key))
                        continue;
                    long old = spans.get(key);
                    int l = old == NO_SPAN ? lo : Math.min(lo, (int) (old >> 32));
                    int h = old == NO_SPAN ? hi : Math.max(hi, (int) old);
                    spans.put(key, ((long) l << 32) | (h & 0xFFFFFFFFL));
                }
        }
        return spans;
    }

    // ---- the first-person flush and the [FogRemesh] line ------------------------------------------------------------

    static final int LOG_EVERY_TICKS = 200;
    private static int ticks = 0;
    private static Totals lastLogged = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        ClientLevel level = MC.level;
        if (level == null || MC.levelRenderer == null) {
            stale.clear();
            staleLevel = null;
            ticks = 0;
            return;
        }
        if (!stale.isEmpty() && level == staleLevel && !OrthoviewClientEvents.isEnabled())
            flushStale(level, MC.levelRenderer);
        if (++ticks % LOG_EVERY_TICKS != 0)
            return;
        Totals now = totals();
        Totals d = lastLogged == null ? null : now.minus(lastLogged);
        lastLogged = now;
        if (d == null || (!FogOfWarClientEvents.isEnabled() && d.updates == 0 && d.flushed == 0))
            return;
        double secs = LOG_EVERY_TICKS / 20.0;
        ReignOfNether.LOGGER.info("[FogRemesh] {} s: {} fog updates, sections marked {}/s (skipped {}/s under the surface "
                        + "band, flushed {}/s), rebuilt {}/s (+{}/s all air) on {}, band {} ms/s, {} mode, {} stale columns",
                Math.round(secs), d.updates, perSec(d.marked, secs), perSec(d.skipped, secs), perSec(d.flushed, secs),
                perSec(d.rebuilt, secs), perSec(d.rebuiltEmpty, secs), SectionRebuilds.renderer(),
                String.format("%.2f", d.bandNanos / 1e6 / secs), mode.name().toLowerCase(), stale.size());
    }

    private static long perSec(long n, double secs) {
        return Math.round(n / secs);
    }
}
