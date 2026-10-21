package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.fogofwar.FrozenChunkClientboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merged buildings, the server's side (project doc claude/merged-buildings.md, section 4): finds same-type, same-owner
 * built buildings standing edge to edge on a grid, matches them to the shape ladder (MergeShapes) and swaps the
 * members for one {@link ComplexPlacement}; shrinks a complex when a section falls; re-forms every complex after a
 * load; tells the clients.
 * <p>
 * Every change goes through a {@link Registry}: the live world's building list (packets to the clients who may see
 * each building) or a test's private list (no packets, nothing another test can see). The rule is always the same
 * re-match: take the connected component of touching cells round a seed, partition it biggest shape first, keep
 * every complex whose sections are exactly one of the new groups, dissolve the others into lone buildings and form
 * the groups that are new. One deterministic function covers "a generator finished", "a section collapsed", "the
 * owner switched merging back on" and "the server loaded a save".
 */
public final class BuildingMerge {
    private BuildingMerge() { }

    /** Neighbours may stand this many blocks higher or lower: the grid tool puts each cell on its own ground. */
    public static final int MAX_Y_STEP = 2;

    // ------------------------------------------------------------------ registry

    /**
     * Where placements live, and what this operation changed. Live: the world's list - a removed complex is taken
     * off the clients (REMOVE to those who see it, the destroyed flag to everyone for fog ghosts), an absorbed lone
     * building is flagged the same way, and {@link #flush} sends every placement that is new and still standing (a
     * lone building as PLACE, a complex as MERGE) after the list is settled, so fog filtering sees the final state.
     */
    public static final class Registry {
        public final List<BuildingPlacement> list;
        public final boolean live;
        private final List<BuildingPlacement> added = new ArrayList<>();

        public Registry(List<BuildingPlacement> list, boolean live) {
            this.list = list;
            this.live = live;
        }

        void remove(BuildingPlacement p) {
            if (!list.contains(p))
                return;
            boolean fresh = added.remove(p);
            if (live && !fresh) {
                // the clients that see it drop it now; everyone else drops their fog ghost when they look again.
                // Sent while it is still listed: the fog filter needs the server's building to find it
                if (p instanceof ComplexPlacement)
                    BuildingClientboundPacket.removeBuilding(p.originPos);
                FrozenChunkClientboundPacket.setBuildingDestroyedServerside(p.originPos);
            }
            list.remove(p);
        }

        void add(BuildingPlacement p) {
            list.add(p);
            added.add(p);
        }

        void flush() {
            if (live)
                for (BuildingPlacement p : added)
                    if (list.contains(p))
                        sendPlacement(p);
            added.clear();
        }
    }

    public static Registry liveRegistry() {
        return new Registry(BuildingServerEvents.getBuildings(), true);
    }

    /** A placement to the clients that may see it: a complex as one MERGE packet, anything else as the usual PLACE. */
    public static void sendPlacement(BuildingPlacement b) {
        if (b instanceof ComplexPlacement c) {
            MergedBuildingClientboundPacket.send(c);
            return;
        }
        BuildingClientboundPacket.placeBuilding(b.originPos,
            b.getBuilding(),
            b.rotation,
            b.ownerName,
            b.scenarioRoleIndex,
            b.blockPlaceQueue.size(),
            b.getBuilding() instanceof AbstractBridge && b.isDiagonalBridge,
            b.getUpgradeLevel(),
            b.isBuilt,
            b instanceof PortalPlacement p ? p.getPortalType() : PortalPlacement.PortalType.BASIC,
            b instanceof PortalPlacement p && p.hasDestination() ? p.destination : new BlockPos(0, 0, 0)
        );
    }

    // ------------------------------------------------------------------ cells and neighbours

    /** A grid cell: a lone built placement (lone set), or one section of a complex (complex + section set). */
    record Cell(BuildingPlacement lone, ComplexPlacement complex, ComplexPlacement.Section section) {
        static Cell of(BuildingPlacement lone) { return new Cell(lone, null, null); }
        static Cell of(ComplexPlacement complex, ComplexPlacement.Section section) { return new Cell(null, complex, section); }

        int minX() { return lone != null ? lone.minCorner.getX() : section.minX; }
        int maxX() { return lone != null ? lone.maxCorner.getX() : section.maxX; }
        int minZ() { return lone != null ? lone.minCorner.getZ() : section.minZ; }
        int maxZ() { return lone != null ? lone.maxCorner.getZ() : section.maxZ; }
        int y() { return lone != null ? lone.originPos.getY() : section.y; }
        BlockPos origin() { return lone != null ? lone.originPos : section.originPos; }
    }

    /**
     * Edge to edge on the grid: the same span on one axis and footprints that touch on the other (the Shift+Alt grid
     * lays them exactly so), within {@link #MAX_Y_STEP} of each other. A one-block gap (the bot's 4-block pitch, a
     * hand-laid row) does not merge: "the copies that touch in a tight block grow into one complex".
     */
    static boolean touching(Cell a, Cell b) {
        if (Math.abs(a.y() - b.y()) > MAX_Y_STEP)
            return false;
        boolean sameZ = a.minZ() == b.minZ() && a.maxZ() == b.maxZ();
        boolean sameX = a.minX() == b.minX() && a.maxX() == b.maxX();
        if (sameZ && (b.minX() == a.maxX() + 1 || a.minX() == b.maxX() + 1))
            return true;
        return sameX && (b.minZ() == a.maxZ() + 1 || a.minZ() == b.maxZ() + 1);
    }

    /** Would two lone buildings merge if both stood finished? (The grid-ghost preview and the tests ask.) */
    public static boolean touching(BuildingPlacement a, BuildingPlacement b) {
        return a != null && b != null && touching(Cell.of(a), Cell.of(b));
    }

    /** May this lone building join a complex right now? */
    static boolean mayMerge(BuildingPlacement p) {
        return p != null && !(p instanceof ComplexPlacement) && p.getBuilding() != null && p.getBuilding().canMerge()
            && p.isBuilt && p.isMergeEnabled() && !p.decommissioning && !p.isDestroyedServerside;
    }

    /** Every cell of this building and owner that may merge: lone buildings and the sections of existing complexes. */
    static List<Cell> candidates(Registry reg, Building building, String owner) {
        List<Cell> out = new ArrayList<>();
        for (BuildingPlacement p : reg.list) {
            if (p.getBuilding() != building || owner == null || !owner.equals(p.ownerName))
                continue;
            if (p instanceof ComplexPlacement c) {
                for (ComplexPlacement.Section s : c.getSections())
                    out.add(Cell.of(c, s));
            } else if (mayMerge(p)) {
                out.add(Cell.of(p));
            }
        }
        return out;
    }

    /**
     * The connected component round the seed as a lattice: lattice cell (column along +x, row along +z) -> grid
     * cell, normalised to start at (0, 0). Breadth-first over touching cells; a cell that would land on an occupied
     * lattice spot (misaligned stacks) is left out, so the lattice never lies about who touches whom.
     */
    static Map<MergeShapes.Cell, Cell> component(List<Cell> candidates, Cell seed) {
        Map<Cell, MergeShapes.Cell> coords = new HashMap<>();
        Map<MergeShapes.Cell, Cell> at = new HashMap<>();
        coords.put(seed, new MergeShapes.Cell(0, 0));
        at.put(new MergeShapes.Cell(0, 0), seed);
        Deque<Cell> queue = new ArrayDeque<>();
        queue.add(seed);
        while (!queue.isEmpty()) {
            Cell cur = queue.poll();
            MergeShapes.Cell cc = coords.get(cur);
            for (Cell other : candidates) {
                if (other == cur || coords.containsKey(other) || !touching(cur, other))
                    continue;
                MergeShapes.Cell oc;
                if (other.minZ() == cur.minZ() && other.maxZ() == cur.maxZ())
                    oc = new MergeShapes.Cell(other.minX() > cur.minX() ? cc.col() + 1 : cc.col() - 1, cc.row());
                else
                    oc = new MergeShapes.Cell(cc.col(), other.minZ() > cur.minZ() ? cc.row() + 1 : cc.row() - 1);
                if (at.containsKey(oc))
                    continue;
                coords.put(other, oc);
                at.put(oc, other);
                queue.add(other);
            }
        }
        int minCol = Integer.MAX_VALUE, minRow = Integer.MAX_VALUE;
        for (MergeShapes.Cell c : at.keySet()) {
            minCol = Math.min(minCol, c.col());
            minRow = Math.min(minRow, c.row());
        }
        Map<MergeShapes.Cell, Cell> lattice = new LinkedHashMap<>();
        for (Map.Entry<MergeShapes.Cell, Cell> e : at.entrySet())
            lattice.put(new MergeShapes.Cell(e.getKey().col() - minCol, e.getKey().row() - minRow), e.getValue());
        return lattice;
    }

    // ------------------------------------------------------------------ the re-match

    /**
     * Re-matches the component round the seed (a lone building, or a section). Returns the lone placements that were
     * in the component, merged or not, so a sweep (reformAll) can skip them. {@code fallenCue} / {@code fallenAt}:
     * the footprints that just collapsed, carried by every complex formed here for the animation.
     */
    static Set<BuildingPlacement> rematch(Registry reg, Building building, String owner, Cell seed,
                                          @Nullable List<BlockPos> fallenCue, long fallenAt) {
        Set<BuildingPlacement> processed = new HashSet<>();
        List<Cell> cands = candidates(reg, building, owner);
        if (!cands.contains(seed)) {
            if (seed.lone() != null)
                processed.add(seed.lone());
            return processed;
        }
        Map<MergeShapes.Cell, Cell> lattice = component(cands, seed);
        Map<BlockPos, BuildingPlacement> loneByOrigin = new HashMap<>();
        Map<ComplexPlacement, Set<BlockPos>> existing = new LinkedHashMap<>();
        for (Cell c : lattice.values()) {
            if (c.lone() != null) {
                processed.add(c.lone());
                loneByOrigin.put(c.lone().originPos, c.lone());
            } else {
                existing.computeIfAbsent(c.complex(), k -> new HashSet<>()).add(c.section().originPos);
            }
        }
        List<MergeShapes.Group> groups = MergeShapes.match(lattice.keySet(), MergeShapes.MAX_MEMBERS,
            (a, b) -> touching(lattice.get(a), lattice.get(b)));

        // complexes whose sections are exactly one of the new groups stand as they are
        Set<ComplexPlacement> kept = new HashSet<>();
        List<MergeShapes.Group> toForm = new ArrayList<>();
        for (MergeShapes.Group g : groups) {
            Set<BlockPos> origins = new HashSet<>();
            for (MergeShapes.Cell cell : g.cells())
                origins.add(lattice.get(cell).origin());
            ComplexPlacement same = null;
            for (Map.Entry<ComplexPlacement, Set<BlockPos>> e : existing.entrySet())
                if (e.getValue().equals(origins) && e.getKey().getSections().size() == origins.size()) {
                    same = e.getKey();
                    break;
                }
            if (same != null)
                kept.add(same);
            else
                toForm.add(g);
        }
        // the others dissolve: their sections stand on their own (damaged as they are) and may join a new group
        for (ComplexPlacement c : existing.keySet()) {
            if (kept.contains(c))
                continue;
            List<BuildingPlacement> parts = c.standaloneSections();
            reg.remove(c);
            for (BuildingPlacement part : parts) {
                reg.add(part);
                loneByOrigin.put(part.originPos, part);
            }
        }
        Level level = seed.lone() != null ? seed.lone().getLevel() : seed.complex().getLevel();
        long now = level == null ? 0 : level.getGameTime();
        for (MergeShapes.Group g : toForm) {
            List<BuildingPlacement> members = new ArrayList<>(g.members());
            for (MergeShapes.Cell cell : g.cells()) {
                BuildingPlacement m = loneByOrigin.get(lattice.get(cell).origin());
                if (m == null || !reg.list.contains(m)) {
                    members = null;
                    break;
                }
                members.add(m);
            }
            if (members == null)
                continue;
            ComplexPlacement complex = ComplexPlacement.form(level, building, owner, members, g.cells(), g.shape(), now);
            if (fallenCue != null && !fallenCue.isEmpty()) {
                complex.recentlyCollapsed.addAll(fallenCue);
                complex.collapsedAtTick = fallenAt;
            }
            for (BuildingPlacement m : members)
                reg.remove(m);
            reg.add(complex);
        }
        return processed;
    }

    // ------------------------------------------------------------------ entry points

    /** Server: a building finished (or had merging switched back on) - it looks for its kind beside it. */
    public static void onBuilt(BuildingPlacement p) {
        if (p == null || p.getLevel() == null || p.getLevel().isClientSide() || !mayMerge(p))
            return;
        Registry reg = liveRegistry();
        if (!reg.list.contains(p))
            return;
        try {
            rematch(reg, p.getBuilding(), p.ownerName, Cell.of(p), null, -1);
        } finally {
            reg.flush();
        }
    }

    /** The owner's merge switch on a lone building (BuildingAction.TOGGLE_MERGE). A complex ignores it in slice 1. */
    public static void toggle(BuildingPlacement b) {
        if (b == null || b instanceof ComplexPlacement)
            return;
        b.setMergeEnabled(!b.isMergeEnabled());
        if (b.isMergeEnabled())
            onBuilt(b);
    }

    /**
     * Every merge-on built building of a mergeable type re-forms its complexes: after a load (complexes are saved as
     * their sections) and in the tests. Each component is matched once.
     */
    public static void reformAll(Registry reg) {
        Set<BuildingPlacement> done = new HashSet<>();
        try {
            for (BuildingPlacement p : new ArrayList<>(reg.list)) {
                if (done.contains(p) || !reg.list.contains(p) || !mayMerge(p))
                    continue;
                done.addAll(rematch(reg, p.getBuilding(), p.ownerName, Cell.of(p), null, -1));
            }
        } finally {
            reg.flush();
        }
    }

    /**
     * Server, from the complex's tick once a block of it was knocked out: every dead section comes down, and the
     * survivors re-match - with any lone neighbour that was never part of it. With no survivor the complex is gone:
     * the kill-feed line, the defeat check and the owner's side effects run through demolish (the sections' own
     * collapses were the visible fall).
     */
    public static void checkSections(ComplexPlacement c, Registry reg) {
        if (c == null || !(c.getLevel() instanceof ServerLevel level) || !reg.list.contains(c))
            return;
        List<ComplexPlacement.Section> dead = new ArrayList<>();
        for (ComplexPlacement.Section s : c.getSections())
            if (c.isSectionDead(s))
                dead.add(s);
        if (dead.isEmpty())
            return;
        try {
            for (ComplexPlacement.Section s : dead)
                c.collapseSection(level, s);
            List<BlockPos> fallen = new ArrayList<>(c.recentlyCollapsed);
            long fallenAt = c.collapsedAtTick;
            if (c.getSections().isEmpty()) {
                com.solegendary.reignofnether.hud.KillFeedServerEvents.onBuildingDestroyed(c, c.lastAttacker);
                reg.remove(c);
                c.demolish(level);
                return;
            }
            List<BuildingPlacement> parts = c.standaloneSections();
            Building building = c.getBuilding();
            String owner = c.ownerName;
            reg.remove(c);
            for (BuildingPlacement part : parts)
                reg.add(part);
            Set<BuildingPlacement> seen = new HashSet<>();
            for (BuildingPlacement part : parts) {
                if (seen.contains(part) || !reg.list.contains(part))
                    continue;
                seen.addAll(rematch(reg, building, owner, Cell.of(part), fallen, fallenAt));
            }
        } finally {
            reg.flush();
        }
    }

    /** The complex a lone placement's origin belongs to (as a section), or null. */
    @Nullable
    public static ComplexPlacement complexWithSection(List<BuildingPlacement> list, BlockPos origin) {
        for (BuildingPlacement p : list)
            if (p instanceof ComplexPlacement c)
                for (ComplexPlacement.Section s : c.getSections())
                    if (s.originPos.equals(origin))
                        return c;
        return null;
    }

    /** The complexes in a list (for tests and the HUD). */
    public static List<ComplexPlacement> complexes(List<BuildingPlacement> list) {
        List<ComplexPlacement> out = new ArrayList<>();
        for (BuildingPlacement p : list)
            if (p instanceof ComplexPlacement c)
                out.add(c);
        return Collections.unmodifiableList(out);
    }
}
