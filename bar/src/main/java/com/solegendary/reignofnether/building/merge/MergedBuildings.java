package com.solegendary.reignofnether.building.merge;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.data.DataType;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Merged buildings (claude/merged-buildings.md, lovish Oct 21: "if buildings are built in shift alt in a formation
 * ... they should merge it into a more intricate bigger building, where the model changes depending on how many are
 * in formation together"). Slice 1 (the spec's build order step 1): the White Angel wind generator, sizes 2 to 9.
 * <p>
 * <b>How it works.</b> The members stay ordinary placements - built, paid, repaired, damaged, saved and synced
 * exactly as before, each one a <i>section</i> of the complex with its own health - and this registry is the one
 * thing that knows which of them form a complex. Every tick ({@link #update}) it looks at the built, merge-enabled
 * members of a mergeable building ({@link Building#mergeable}), and whenever that set changes (a member finished,
 * died, was sold or switched off) it re-cuts every owner's grids into shapes with {@link MergeGrid} and keeps the
 * complexes whose members did not change. Members are grid neighbours when their footprints stand <b>edge to edge</b>
 * along x or z (what the Shift+Alt grid lays: BuildingLineClientEvents steps one footprint per cell) and within
 * {@link #MAX_Y_STEP} of each other's ground; a gap, an offset row, a cliff, another owner or another building type
 * never joins. A complex that stands is only re-cut when the grid round it grows into a bigger shape
 * ({@link #biggerShapes}): a Quarter is never re-formed one column over because a twelfth mill appeared beside it,
 * while a Pair whose neighbours complete a Farm does become the Farm. So:
 * <ul>
 *   <li>the complex forms the tick the last member of a shape finishes (spec 2e);</li>
 *   <li>a hit damages the section under it, a destroyed section leaves the registry's input and the complex
 *       shrinks to the biggest shape the survivors still form (2d) - no extra damage code at all;</li>
 *   <li>rebuilding on the empty footprint re-forms the bigger shape as soon as it stands;</li>
 *   <li>the output bonus is {@link #outputMultiplier} in BuildingPlacement.getEnergyIncome, so the once-a-second
 *       income pass, the wind, the ley links and the economy pace all compose with it untouched;</li>
 *   <li>save / load needs nothing but the per-building merge switch ({@link #MERGE_OFF}): the complexes are
 *       re-derived from the loaded buildings on the first tick;</li>
 *   <li>multiplayer: the server registry ({@link #SERVER}) is the authority for income; every client runs the same
 *       derivation ({@link #CLIENT}) over the buildings it has been sent (built flag and merge switch included), for
 *       its selection, card and animations - a client under fog that sees only part of an enemy's grid sees a
 *       smaller shape, which is what fog should show.</li>
 * </ul>
 * What a slice-2 art drop needs is here too: {@link MergedComplex} carries the state the animations read (formed
 * tick, the previous shape, the sections that fell), and the members' structures can be swapped per section with
 * changeStructure without touching the rules.
 */
public final class MergedBuildings {

    /** The biggest shape live in this slice (the Quarter): the Citadel and the Wonder wait for their art and powers. */
    public static final int MAX_MEMBERS = 9;
    /** The raising / collapse moment, in ticks (claude/merged-buildings-animation.md: 2.5-3.5 s). */
    public static final int RAISING_TICKS = 60;
    /**
     * Neighbours may stand this many blocks higher or lower and still join: the grid tool puts each cell on its own
     * ground, so a grid on a slope is a terrace of one-block steps; across a cliff two mills share no wall to grow
     * through, and the shape's art could not span it.
     */
    public static final int MAX_Y_STEP = 2;

    public static final MergedBuildings SERVER = new MergedBuildings();
    public static final MergedBuildings CLIENT = new MergedBuildings();
    /**
     * Client only: the same cut over every placed member, built or not - what a grid under construction is going to
     * become, so the card can say "Quarter, 4 of 9 standing" while the workers are still on it. Never earns anything.
     */
    public static final MergedBuildings CLIENT_PLANNED = new MergedBuildings(true);

    public static MergedBuildings of(boolean isClientSide) {
        return isClientSide ? CLIENT : SERVER;
    }

    /** Saved per building: true when its owner switched merging off (absent = on, the default). */
    public static final DataType<Boolean> MERGE_OFF = DataType.createRegistered(
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "merge_off"),
        (tag, server) -> tag.getBoolean("off"),
        off -> {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("off", off);
            return tag;
        },
        () -> false
    );

    public enum ChangeKind {
        /** A new complex out of separate buildings. */
        FORMED,
        /** A new complex that took over an older, smaller one (a section was rebuilt or more members joined). */
        GREW,
        /** A smaller (or re-cut) complex left where a bigger one stood: {@code destroyed} of its members are gone,
         *  the others were switched off or went to another new complex. */
        SHRUNK,
        /** An old complex with no successor: its members stand alone or are gone. */
        DISSOLVED
    }

    /**
     * What one {@link #recompute} changed. {@code complex} is the new complex (null for DISSOLVED); {@code previous}
     * the old one it came from (null for FORMED); {@code destroyed} how many of the old one's members are no longer
     * in the building list at all (as against switched off or moved to another complex).
     */
    public record Change(ChangeKind kind, MergedComplex complex, MergedComplex previous, int destroyed) { }

    private final List<MergedComplex> complexes = new ArrayList<>();
    private final Map<BuildingPlacement, MergedComplex> byMember = new IdentityHashMap<>();
    private long signature = 0;
    private boolean computedOnce = false;

    /** True for {@link #CLIENT_PLANNED}: members need not be built. */
    public final boolean planned;

    /** The live registries are {@link #SERVER} and {@link #CLIENT}; a GameTest makes its own over its own placements. */
    public MergedBuildings() {
        this(false);
    }

    public MergedBuildings(boolean planned) {
        this.planned = planned;
    }

    /**
     * A building that takes part in merging right now: built, switched on, of a mergeable type, with an owner. A
     * section being decommissioned stays one until it is gone: the server never tells the clients a decommission is
     * running (BuildingReclaimClient), and every side must cut the same grid.
     */
    public static boolean isCandidate(BuildingPlacement p) {
        return p != null && p.isBuilt && isPlannedCandidate(p);
    }

    /** A building that will take part once it stands: switched on, of a mergeable type, with an owner. */
    public static boolean isPlannedCandidate(BuildingPlacement p) {
        return p != null && p.mergeEnabled && p.getBuilding() != null && p.getBuilding().mergeable
            && p.ownerName != null && !p.ownerName.isEmpty();
    }

    boolean candidate(BuildingPlacement p) {
        return planned ? isPlannedCandidate(p) : isCandidate(p);
    }

    public List<MergedComplex> complexes() {
        return Collections.unmodifiableList(complexes);
    }

    /** The complex this building is a section of, or null. */
    public MergedComplex complexOf(BuildingPlacement p) {
        return p == null ? null : byMember.get(p);
    }

    /** What this building's output is multiplied by: its complex's size bonus, or 1 on its own. */
    public float outputMultiplier(BuildingPlacement p) {
        MergedComplex c = complexOf(p);
        return c == null ? 1f : c.outputMultiplier();
    }

    public void clear() {
        complexes.clear();
        byMember.clear();
        signature = 0;
        computedOnce = false;
    }

    /**
     * Once a tick: re-derives the complexes if the set of candidate buildings changed since the last call (a cheap
     * signature over them), else does nothing. Returns what changed (empty when nothing did).
     */
    public List<Change> update(List<BuildingPlacement> all, long now) {
        long sig = signature(all);
        if (computedOnce && sig == signature)
            return List.of();
        signature = sig;
        computedOnce = true;
        return recompute(all, now);
    }

    long signature(List<BuildingPlacement> all) {
        long sig = 0x9E3779B97F4A7C15L;
        int n = 0;
        for (BuildingPlacement p : all) {
            if (!candidate(p))
                continue;
            n++;
            long h = p.originPos.asLong();
            h = h * 31 + p.ownerName.hashCode();
            h = h * 31 + System.identityHashCode(p);
            h = h * 31 + System.identityHashCode(p.getBuilding());
            sig += mix(h);   // order independent: the list is re-sorted and re-filtered by other code
        }
        return sig * 31 + n;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private record GroupKey(String owner, Building building) { }

    /**
     * Re-derives every complex from {@code all} now, keeping the complexes whose member set is unchanged (their
     * formed tick with them). Public so a GameTest can drive its own instance over its own placements.
     */
    public List<Change> recompute(List<BuildingPlacement> all, long now) {
        // by origin, not identity: a client that re-receives a building gets a new object at the same origin, and
        // that must not read as a destroyed section
        Set<BlockPos> alive = new java.util.HashSet<>();
        Map<GroupKey, List<BuildingPlacement>> groups = new LinkedHashMap<>();
        for (BuildingPlacement p : all) {
            alive.add(p.originPos);
            if (candidate(p))
                groups.computeIfAbsent(new GroupKey(p.ownerName, p.getBuilding()), k -> new ArrayList<>()).add(p);
        }
        List<Cut> matched = new ArrayList<>();
        for (List<BuildingPlacement> group : groups.values())
            matchGroup(group, matched, byMember);

        List<MergedComplex> next = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        Set<MergedComplex> oldUsed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Cut cut : matched) {
            List<BuildingPlacement> members = cut.members();
            MergedShape shape = cut.shape();
            MergedComplex same = findSame(members);
            if (same != null) {
                next.add(same);
                oldUsed.add(same);
                continue;
            }
            MergedComplex from = biggestOverlap(members);
            if (from == null) {
                MergedComplex c = new MergedComplex(shape, members, now, null, List.of());
                next.add(c);
                changes.add(new Change(ChangeKind.FORMED, c, null, 0));
                continue;
            }
            oldUsed.add(from);
            List<BlockPos> lost = new ArrayList<>();
            int destroyed = 0;
            for (BuildingPlacement m : from.members)
                if (!members.contains(m) && !alive.contains(m.originPos)) {
                    lost.add(m.minCorner);
                    destroyed++;
                }
            boolean grew = members.size() > from.members.size();
            MergedComplex c = new MergedComplex(shape, members, now, from.shape, lost);
            next.add(c);
            changes.add(new Change(grew ? ChangeKind.GREW : ChangeKind.SHRUNK, c, from, destroyed));
        }
        for (MergedComplex old : complexes) {
            if (oldUsed.contains(old))
                continue;
            boolean overlaps = false;
            for (MergedComplex c : next) {
                for (BuildingPlacement m : old.members)
                    if (c.contains(m)) {
                        overlaps = true;
                        break;
                    }
                if (overlaps)
                    break;
            }
            if (overlaps)
                continue;   // its members went to another complex, which reported GREW / SHRUNK against it or a sibling
            int destroyed = 0;
            for (BuildingPlacement m : old.members)
                if (!alive.contains(m.originPos))
                    destroyed++;
            changes.add(new Change(ChangeKind.DISSOLVED, null, old, destroyed));
        }
        complexes.clear();
        complexes.addAll(next);
        byMember.clear();
        for (MergedComplex c : complexes)
            for (BuildingPlacement m : c.members)
                byMember.put(m, c);
        if (!planned)
            for (Change ch : changes)
                ReignOfNether.LOGGER.info("[Merge] {} {}{}", ch.kind(), ch.complex() != null ? ch.complex() : ch.previous(),
                    ch.previous() != null && ch.complex() != null ? " (was " + ch.previous().shape + ")" : "");
        return changes;
    }

    private MergedComplex findSame(List<BuildingPlacement> members) {
        MergedComplex c = byMember.get(members.get(0));
        if (c == null || c.members.size() != members.size())
            return null;
        for (BuildingPlacement m : members)
            if (!c.contains(m))
                return null;
        return c;
    }

    /** Of the old complexes sharing a member with {@code members}, the one sharing the most (ties: the first). */
    private MergedComplex biggestOverlap(List<BuildingPlacement> members) {
        Map<MergedComplex, Integer> shared = new IdentityHashMap<>();
        for (BuildingPlacement m : members) {
            MergedComplex c = byMember.get(m);
            if (c != null)
                shared.merge(c, 1, Integer::sum);
        }
        MergedComplex best = null;
        int bestShared = 0;
        for (MergedComplex c : complexes) {   // in a stable order, not the identity map's
            int s = shared.getOrDefault(c, 0);
            if (s > bestShared) {
                best = c;
                bestShared = s;
            }
        }
        return best;
    }

    // ---- grid geometry: edge-to-edge neighbours, one connected block at a time ----

    /** One shape cut out of a grid: its members in row order. */
    private record Cut(MergedShape shape, List<BuildingPlacement> members) { }

    /** Cuts one owner's buildings of one type into complexes (each a member list in row order); singles are left out. */
    private static void matchGroup(List<BuildingPlacement> group, List<Cut> out, Map<BuildingPlacement, MergedComplex> standing) {
        List<BuildingPlacement> ps = new ArrayList<>(group);
        ps.sort(Comparator.comparingInt((BuildingPlacement p) -> p.minCorner.getZ()).thenComparingInt(p -> p.minCorner.getX()));
        Set<BuildingPlacement> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BuildingPlacement seed : ps) {
            if (seen.contains(seed))
                continue;
            // one connected block, each member given the cell its neighbours imply (the seed is (0, 0))
            Map<MergeGrid.Cell, BuildingPlacement> cells = new HashMap<>();
            Map<BuildingPlacement, MergeGrid.Cell> cellOf = new IdentityHashMap<>();
            Deque<BuildingPlacement> queue = new ArrayDeque<>();
            seen.add(seed);
            cells.put(new MergeGrid.Cell(0, 0), seed);
            cellOf.put(seed, new MergeGrid.Cell(0, 0));
            queue.add(seed);
            while (!queue.isEmpty()) {
                BuildingPlacement q = queue.poll();
                MergeGrid.Cell c = cellOf.get(q);
                for (BuildingPlacement r : ps) {
                    if (seen.contains(r))
                        continue;
                    MergeGrid.Cell rc = neighbourCell(q, c, r);
                    if (rc == null || cells.containsKey(rc))
                        continue;
                    seen.add(r);
                    cells.put(rc, r);
                    cellOf.put(r, rc);
                    queue.add(r);
                }
            }
            if (cells.size() < 2)
                continue;
            // the lattice is a guide, the world is the truth: two cells reached round a corner may still stand either
            // side of a cliff, so every lattice edge a shape uses is checked against the buildings themselves
            BiPredicate<MergeGrid.Cell, MergeGrid.Cell> touch =
                (a, b) -> b.equals(neighbourCell(cells.get(a), a, cells.get(b)));
            for (MergeGrid.Match m : partition(cells, cellOf, touch, standing)) {
                List<BuildingPlacement> members = new ArrayList<>(m.shape().members());
                for (MergeGrid.Cell mc : m.cells())
                    members.add(cells.get(mc));
                out.add(new Cut(m.shape(), members));
            }
        }
    }

    /**
     * The cut of one connected block. Two cuts are weighed: the fresh one over every cell, and the stable one that
     * first keeps every complex already standing whole in the block and cuts only the rest. The fresh one wins only
     * when it builds something bigger ({@link #biggerShapes}: a Pair's neighbours completing a Farm, a Row's third
     * row completing a Quarter); otherwise the stable one stands, so a complex only ever changes when the grid round
     * it really grew into a bigger shape - never re-formed one column over because a mill appeared on its far side.
     * A complex that lost a member is not whole and is cut afresh with everything else (the shrink).
     */
    private static List<MergeGrid.Match> partition(Map<MergeGrid.Cell, BuildingPlacement> cells,
                                                   Map<BuildingPlacement, MergeGrid.Cell> cellOf,
                                                   BiPredicate<MergeGrid.Cell, MergeGrid.Cell> touch,
                                                   Map<BuildingPlacement, MergedComplex> standing) {
        List<MergeGrid.Match> fresh = MergeGrid.partition(cells.keySet(), MAX_MEMBERS, touch);
        Set<MergedComplex> here = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BuildingPlacement p : cells.values()) {
            MergedComplex c = standing.get(p);
            if (c != null)
                here.add(c);
        }
        if (here.isEmpty())
            return fresh;
        List<MergedComplex> ordered = new ArrayList<>(here);
        ordered.sort(Comparator.comparingInt((MergedComplex c) -> c.minCorner.getZ()).thenComparingInt(c -> c.minCorner.getX()));
        List<MergeGrid.Match> stable = new ArrayList<>();
        Set<MergeGrid.Cell> rest = new java.util.HashSet<>(cells.keySet());
        for (MergedComplex c : ordered) {
            List<MergeGrid.Cell> own = new ArrayList<>(c.members.size());
            for (BuildingPlacement m : c.members) {
                MergeGrid.Cell mc = cellOf.get(m);
                if (mc == null || !rest.contains(mc)) {
                    own = null;   // a member fell, was switched off or is in another block: not whole
                    break;
                }
                own.add(mc);
            }
            if (own == null)
                continue;
            List<MergeGrid.Match> whole = MergeGrid.partition(own, MAX_MEMBERS, touch);
            if (whole.size() == 1 && whole.get(0).shape() == c.shape) {
                stable.add(whole.get(0));
                rest.removeAll(own);
            }
        }
        stable.addAll(MergeGrid.partition(rest, MAX_MEMBERS, touch));
        stable.sort(Comparator.comparingInt(MergeGrid.Match::z0).thenComparingInt(MergeGrid.Match::x0));
        return biggerShapes(fresh, stable) ? fresh : stable;
    }

    /** Shape sizes largest first, compared one by one: [6] beats [2, 2, 2]; [9, 2] ties [9, 2]; [2, 2] beats [2]. */
    public static boolean biggerShapes(List<MergeGrid.Match> a, List<MergeGrid.Match> b) {
        List<Integer> sa = sizes(a), sb = sizes(b);
        for (int i = 0; i < Math.min(sa.size(), sb.size()); i++)
            if (!sa.get(i).equals(sb.get(i)))
                return sa.get(i) > sb.get(i);
        return sa.size() > sb.size();
    }

    private static List<Integer> sizes(List<MergeGrid.Match> matches) {
        List<Integer> out = new ArrayList<>(matches.size());
        for (MergeGrid.Match m : matches)
            out.add(m.shape().members());
        out.sort(Collections.reverseOrder());
        return out;
    }

    /**
     * The cell of {@code r} next to {@code q} (at {@code c}) when the two footprints stand edge to edge along x or z,
     * flush on the other axis and within {@link #MAX_Y_STEP} of each other's ground - what the Shift+Alt grid lays,
     * cell by cell; else null (a gap, an offset, a diagonal, a cliff).
     */
    static MergeGrid.Cell neighbourCell(BuildingPlacement q, MergeGrid.Cell c, BuildingPlacement r) {
        if (q == null || r == null || Math.abs(q.originPos.getY() - r.originPos.getY()) > MAX_Y_STEP)
            return null;
        BlockPos qMin = q.minCorner, qMax = q.maxCorner, rMin = r.minCorner, rMax = r.maxCorner;
        boolean sameZ = qMin.getZ() == rMin.getZ() && qMax.getZ() == rMax.getZ();
        boolean sameX = qMin.getX() == rMin.getX() && qMax.getX() == rMax.getX();
        if (sameZ && rMin.getX() == qMax.getX() + 1)
            return c.east();
        if (sameZ && rMax.getX() == qMin.getX() - 1)
            return c.west();
        if (sameX && rMin.getZ() == qMax.getZ() + 1)
            return c.south();
        if (sameX && rMax.getZ() == qMin.getZ() - 1)
            return c.north();
        return null;
    }
}
