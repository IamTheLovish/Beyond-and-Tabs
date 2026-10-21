package com.solegendary.reignofnether.building;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A merged building (project doc claude/merged-buildings.md): several copies of the same building laid edge to edge
 * in a Shift+Alt grid that have grown into one complex. One placement to the game - one selection box, one health
 * bar, one strategic icon, one minimap mark, one entry in the kill feed - built of {@link Section}s, the members it
 * grew from, each with its own footprint and health.
 * <ul>
 *   <li><b>Blocks:</b> the union of the members' structures (slice 1's placeholder look: the members' own mills,
 *       joined; the shape's real drawing comes from the art lane and will arrive through changeStructure). The base
 *       class's block bookkeeping (placed set, repairs, construction queue) works on that union unchanged.</li>
 *   <li><b>Output:</b> the members' income summed times the shape's bonus ({@link MergeShapes.Shape#bonus}); every
 *       member still follows the wind and the ley network as it did on its own.</li>
 *   <li><b>Damage:</b> a hit lands on the section nearest the attacker (or the block it hit,
 *       {@link #destroyRandomBlocksAt}); the health shown is the sum of the live sections'. A section whose standing
 *       blocks fall to the collapse threshold is dead: its blocks come down and the complex shrinks to the biggest
 *       shapes the survivors still form (BuildingMerge.checkSections re-matches them; the shrunken complexes are new
 *       placements, this one is replaced). Rebuilding a generator on the fallen footprint re-forms the bigger shape
 *       the moment it finishes.</li>
 *   <li><b>Persistence:</b> saved as its sections, as ordinary built buildings; re-formed on load
 *       (BuildingMerge.reformAll). Synced to clients as one MERGE packet naming the members
 *       (MergedBuildingClientboundPacket); the client builds the same complex from them.</li>
 * </ul>
 * Server authoritative: the client never forms or splits a complex on its own, it only mirrors the server's packets.
 * {@link #formedAtTick}, {@link #collapsedAtTick} and {@link #recentlyCollapsed} travel with it so the raising /
 * collapse animation (later) has its cue on every client.
 */
public class ComplexPlacement extends BuildingPlacement {

    /** The "raising" moment after a complex forms (the spec's ~3 s): animation and sound hang off it, nothing else. */
    public static final int RAISE_TICKS = 60;

    /** One member of the complex: its footprint, orientation and blocks. Health is read from the complex's placed set. */
    public static final class Section {
        public final BlockPos originPos;
        public final Rotation rotation;
        public final MergeShapes.Cell cell;
        public final int minX, maxX, minZ, maxZ, y;
        public final BlockPos centrePos;
        final ArrayList<BuildingBlock> blocks;
        final int totalBlocks;

        Section(BlockPos originPos, Rotation rotation, MergeShapes.Cell cell, ArrayList<BuildingBlock> blocks) {
            this.originPos = originPos;
            this.rotation = rotation;
            this.cell = cell;
            this.blocks = blocks;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE, total = 0;
            for (BuildingBlock b : blocks) {
                BlockPos p = b.getBlockPos();
                minX = Math.min(minX, p.getX());
                maxX = Math.max(maxX, p.getX());
                minZ = Math.min(minZ, p.getZ());
                maxZ = Math.max(maxZ, p.getZ());
                minY = Math.min(minY, p.getY());
                maxY = Math.max(maxY, p.getY());
                if (!b.getBlockState().isAir())
                    total++;
            }
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.y = originPos.getY();
            this.totalBlocks = total;
            this.centrePos = new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        }

        public int totalBlocks() { return totalBlocks; }

        public List<BuildingBlock> blocks() { return Collections.unmodifiableList(blocks); }

        /** True if this footprint column holds the position (any height). */
        public boolean coversColumn(BlockPos p) {
            return p.getX() >= minX && p.getX() <= maxX && p.getZ() >= minZ && p.getZ() <= maxZ;
        }

        double distSqrTo(Vec3 pos) {
            double cx = Math.max(minX, Math.min(pos.x, maxX + 1)), cz = Math.max(minZ, Math.min(pos.z, maxZ + 1));
            return (pos.x - cx) * (pos.x - cx) + (pos.z - cz) * (pos.z - cz);
        }
    }

    private final List<Section> sections = new ArrayList<>();
    private MergeShapes.Shape shape;
    /** Game time this complex formed (server; synced). Before formedAtTick + RAISE_TICKS it is "raising". */
    public long formedAtTick;
    /** Game time of the section collapse that made this (shrunken) complex, or -1; the fallen footprints' origins. */
    public long collapsedAtTick = -1;
    public final List<BlockPos> recentlyCollapsed = new ArrayList<>();

    // the section the current destroyRandomBlocks call lands on, and where the hit came from (set for its duration)
    private Section targetSection = null;
    private Vec3 hitFrom = null;
    private boolean sectionsDirty = false;

    private ComplexPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                             ArrayList<BuildingBlock> blocks) {
        super(building, level, originPos, rotation, ownerName, blocks, false);
    }

    /**
     * Builds a complex of {@code shape} from built members (same building, same owner), {@code members.get(i)} standing
     * in lattice cell {@code cells.get(i)}; the first member (row-major: least z, then least x) is the anchor whose
     * origin and rotation the complex carries. The members are NOT touched or unregistered - BuildingMerge does that;
     * on the client they are throwaway placements made from the packet.
     */
    public static ComplexPlacement form(Level level, Building building, String ownerName, List<BuildingPlacement> members,
                                        List<MergeShapes.Cell> cells, MergeShapes.Shape shape, long formedAtTick) {
        if (members == null || members.isEmpty() || cells == null || cells.size() != members.size())
            throw new IllegalArgumentException("a complex needs its members and one cell per member");
        ArrayList<BuildingBlock> union = new ArrayList<>();
        for (BuildingPlacement m : members)
            union.addAll(m.getBlocks());
        BuildingPlacement anchor = members.get(0);
        ComplexPlacement c = new ComplexPlacement(building, level, anchor.originPos, anchor.rotation, ownerName, union);
        for (int i = 0; i < members.size(); i++) {
            BuildingPlacement m = members.get(i);
            c.sections.add(new Section(m.originPos, m.rotation, cells.get(i), new ArrayList<>(m.getBlocks())));
        }
        c.shape = shape;
        c.formedAtTick = formedAtTick;
        c.isBuilt = true;
        c.highestBlockCountReached = c.getBlocksTotal();
        c.setLevel(level);
        c.updateButtons();
        return c;
    }

    /** The members' blocks are already biome-swapped (they were placed as buildings): no second pass over the union. */
    @Override
    protected void setBlocks(ArrayList<BuildingBlock> blocks) {
        this.blocks = blocks;
        int solid = 0;
        for (BuildingBlock block : this.blocks) {
            blockMap.put(block.getBlockPos(), block);
            if (!block.getBlockState().isAir())
                solid++;
        }
        this.totalBlocks = solid;
    }

    public List<Section> getSections() { return Collections.unmodifiableList(sections); }

    public MergeShapes.Shape getShape() { return shape; }

    /** Live sections - the complex's size (the shape's member count while nothing has fallen). */
    @Override
    public int memberCount() { return sections.size(); }

    /** The section whose footprint column holds the position, or null. */
    @Nullable
    public Section sectionAt(BlockPos p) {
        for (Section s : sections)
            if (s.coversColumn(p))
                return s;
        return null;
    }

    /** The section nearest a point (footprint distance), or a random one for an unplaced hit; null with no sections. */
    @Nullable
    public Section nearestSection(@Nullable Vec3 from) {
        if (sections.isEmpty())
            return null;
        if (from == null)
            return sections.get(getLevel().getRandom().nextInt(sections.size()));
        Section best = null;
        double bestD = Double.MAX_VALUE;
        for (Section s : sections) {
            double d = s.distSqrTo(from);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }

    /** Standing (placed, non-air) blocks of a section - server side (the client's placed set is not synced per section). */
    public int sectionBlocksPlaced(Section s) {
        int n = 0;
        for (BuildingBlock b : s.blocks)
            if (!b.getBlockState().isAir() && placedBlockPosSet.contains(b.getBlockPos()))
                n++;
        return n;
    }

    /** A section at or under the collapse threshold (the same rule a lone building dies by: minBlocksPercent). */
    public boolean isSectionDead(Section s) {
        return s.totalBlocks <= 0 || sectionBlocksPlaced(s) <= s.totalBlocks * minBlocksPercent;
    }

    /** True while the complex is still rising out of its members (formedAtTick + RAISE_TICKS). */
    public boolean isRaising(long gameTime) {
        return formedAtTick > 0 && gameTime - formedAtTick < RAISE_TICKS;
    }

    // ---- economy: the members summed, times the shape's bonus (storage sums without a bonus) ----

    @Override
    public float getEnergyIncome() {
        if (!isBuilt || sections.isEmpty())
            return 0;
        float sum = 0;
        for (Section s : sections)
            sum += energyIncomeOfMember(s.centrePos);
        return sum * shape.outputMultiplier();
    }

    @Override
    public float getMetalIncome() {
        if (!isBuilt || sections.isEmpty())
            return 0;
        return getBuilding().getMetalIncome() * sections.size() * shape.outputMultiplier();
    }

    @Override
    public float getMetalStorage() { return isBuilt ? getBuilding().getMetalStorage() * sections.size() : 0; }

    @Override
    public float getEnergyStorage() { return isBuilt ? getBuilding().getEnergyStorage() * sections.size() : 0; }

    // ---- health: the live sections' health summed - one bar, section-sized hits ----

    @Override
    public double getHealthPerBlock() {
        if (getBuilding().isUsingSetHealth(this)) {
            int blocksTotal = getBlocksTotal();
            if (blocksTotal <= 0)
                blocksTotal = 1;
            return (getBuilding().getMaxHealth(this) * Math.max(1, sections.size()) / blocksTotal) * 2;
        }
        return Building.DEFAULT_HEALTH_PER_BLOCK;
    }

    @Override
    public int getMaxHealth() {
        if (getBuilding().isUsingSetHealth(this))
            return (int) Math.round(getBuilding().getMaxHealth(this) * Math.max(1, sections.size()));
        return super.getMaxHealth();
    }

    /** A hit with a known attacker lands on the section nearest it. */
    @Override
    public void destroyRandomBlocks(double amount, @Nullable Entity attacker) {
        hitFrom = attacker != null ? attacker.position() : null;
        try {
            super.destroyRandomBlocks(amount, attacker);
        } finally {
            hitFrom = null;
        }
    }

    /** A hit at a known block (splash, a shell landing) lands on the section under it. */
    public void destroyRandomBlocksAt(BlockPos hit, double amount, @Nullable Entity attacker) {
        hitFrom = hit != null ? Vec3.atCenterOf(hit) : null;
        try {
            super.destroyRandomBlocks(amount, attacker);
        } finally {
            hitFrom = null;
        }
    }

    /** Damage that goes straight to one section (the tests, and splash spread over sections). */
    public void damageSection(Section s, double amount) {
        if (s == null || !sections.contains(s))
            return;
        targetSection = s;
        try {
            super.destroyRandomBlocks(amount);
        } finally {
            targetSection = null;
        }
    }

    @Override
    public void destroyRandomBlocks(double amount) {
        if (getLevel().isClientSide() || sections.isEmpty())
            return;
        if (targetSection != null) {   // damageSection
            super.destroyRandomBlocks(amount);
            return;
        }
        targetSection = nearestSection(hitFrom);
        try {
            super.destroyRandomBlocks(amount);
        } finally {
            targetSection = null;
        }
    }

    /** Only the hit section's blocks are knocked out. */
    @Override
    protected List<BuildingBlock> destroyCandidates() {
        return targetSection != null ? targetSection.blocks : blocks;
    }

    @Override
    public void onBlockBreak(ServerLevel level, BlockPos pos, boolean breakBlocks) {
        super.onBlockBreak(level, pos, breakBlocks);
        sectionsDirty = true;   // a section may have fallen: checked in tick (never inside the buildings loop)
    }

    /**
     * The complex never dies as a whole on the union's block count: its sections die one by one
     * (BuildingMerge.checkSections) and it is gone when the last one has. The client follows the server's flag.
     */
    @Override
    public boolean shouldBeDestroyed() {
        if (getLevel().isClientSide())
            return super.shouldBeDestroyed();
        return isIllegallyOutsideWorldBorder() || sections.isEmpty();
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);
        if (!tickLevel.isClientSide() && sectionsDirty) {
            sectionsDirty = false;
            BuildingMerge.checkSections(this, BuildingMerge.liveRegistry());
        }
    }

    /** Server: whether any section is dead right now (forces the check the next tick would do). */
    public boolean hasDeadSection() {
        for (Section s : sections)
            if (isSectionDead(s))
                return true;
        return false;
    }

    /**
     * A dead section comes down: its standing blocks are removed with the collapse effect (no blasts: the section's
     * blocks were mostly knocked out already) and it leaves the complex. Server only; the caller re-matches the
     * survivors (BuildingMerge.checkSections).
     */
    void collapseSection(ServerLevel level, Section s) {
        if (!sections.remove(s))
            return;
        try {
            com.solegendary.reignofnether.barfx.BarFx.collapse(level,
                new Vec3(s.centrePos.getX() + .5, s.y + 1, s.centrePos.getZ() + .5),
                Math.max(s.maxX - s.minX, s.maxZ - s.minZ) / 2f + .5f,
                Math.max(1, maxCorner.getY() - minCorner.getY() + 1), false,
                com.solegendary.reignofnether.faction.FactionTraits.of(getFaction()).deathDebris);
        } catch (Exception ignored) { }
        for (BuildingBlock b : s.blocks) {
            if (b.getBlockState().isAir())
                continue;
            BlockPos p = b.getBlockPos();
            if (!level.getBlockState(p).isAir())
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            placedBlockPosSet.remove(p);
            blockMap.remove(p);
        }
        blocks.removeAll(s.blocks);
        int solid = 0;
        for (BuildingBlock b : blocks)
            if (!b.getBlockState().isAir())
                solid++;
        totalBlocks = solid;
        highestBlockCountReached = Math.max(2, solid);
        recentlyCollapsed.add(s.originPos);
        collapsedAtTick = level.getGameTime();
        com.solegendary.reignofnether.mana.LeyNetwork.onBuildingGone(ownerName, s.centrePos);
    }

    /** The members as they would stand on their own (fresh built placements): BuildingMerge's dissolve. */
    List<BuildingPlacement> standaloneSections() {
        List<BuildingPlacement> out = new ArrayList<>(sections.size());
        for (Section s : sections) {
            BuildingPlacement p = BuildingUtils.getNewBuildingPlacement(getBuilding(), getLevel(), s.originPos, s.rotation, ownerName, false);
            if (p == null)
                continue;
            p.isBuilt = true;
            p.highestBlockCountReached = p.getBlocksTotal();
            p.tags.addAll(tags);
            out.add(p);
        }
        return out;
    }
}
