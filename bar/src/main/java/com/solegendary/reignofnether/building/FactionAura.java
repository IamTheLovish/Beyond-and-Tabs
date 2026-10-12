package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.FactionTraits;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One faction terraform aura (lovish's vision #5, "factions may terraform the surroundings into their own look";
 * design/biome_bases.md): the ground round a built capitol or lab slowly takes its faction's look - Verdant moss and
 * flowers, Gravebound blighted earth, Tidewrought strand, Stormcallers garden, Brassworks worked ground, Sunforged
 * flagstones. The Horde has none of its own: RoN's Nether Zones already turn the ground round its portals to the Nether.
 * <p>
 * Rules every conversion keeps:
 * <ul>
 *   <li>Only natural ground is converted ({@link #CONVERTIBLE}: grass, dirts, sand, gravel, clay, snow), only where it is
 *       the top of its column and level with the building's foundation (within {@link #MAX_RISE}), and only into a
 *       full, solid, walkable block; decorations ({@link Style#decorations}) are walk-through plants. Units walk exactly
 *       where they walked before - the grid never sees a new obstacle.</li>
 *   <li>Never inside or next to a building (its box grown by one), never on a metal patch's reserved square, never
 *       in a Nether Zone, never in an unloaded chunk, never a cell another aura already holds. A falling block (sand,
 *       gravel) only goes on a sturdy block; elsewhere the style's fallback.</li>
 *   <li>Budgeted: {@link #grow} converts at most the cells it is given and looks at a fixed number of candidates per
 *       call; the registry (FactionAuraServerEvents) hands out a global budget per pass.</li>
 *   <li>Reversible: every converted cell remembers what it was. When the source is destroyed, decommissioned or its
 *       faction's rule is turned off, the aura {@link #recede}s at the same budget, newest (outermost) cells first, the
 *       way StarCraft's creep recedes from a dead hatchery; a cell is only restored while it is still what the aura put
 *       there (a building, a Nether Zone or a player may have changed it since).</li>
 * </ul>
 * Which cells an aura converts and in what order is a pure function of its source position, footprint and style
 * ({@link #plan}), so nothing about the plan is saved; the converted cells and their originals are (saveTag / restore).
 * Server thread only.
 */
public final class FactionAura {

    /** A source's ground may be this far above or below its foundation and still take the look. */
    static final int MAX_RISE = 3;
    /** Candidates one {@link #grow} call examines at most, whatever its budget (bounded block reads). */
    static final int EXAMINE_PER_STEP = 8;
    /** An aura that has walked its whole plan sweeps it again after this (cells freed by felled trees, moved units). */
    static final long RESWEEP_TICKS = 60 * 20;
    static final int MAX_RADIUS = 16;
    /** Block update flags: tell clients, no neighbour updates (nothing cascades), no drops if a plant gives way. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    private static final int SALT_SELECT = 11, SALT_ORDER = 12, SALT_GROUND = 13, SALT_DECO = 14, SALT_DECO_PICK = 15;

    /** How a faction's ground looks. Weights are relative; decorations sit on top of a share of the converted cells. */
    public static final class Style {
        public final String name;
        /** Share of the cells next to the building that convert; it fades to nothing at the aura's rim. */
        public final float coverage;
        final Block[] grounds;
        final float[] cumulative;
        /** Used instead of a falling ground (sand, gravel) where the block under it is not sturdy. */
        final Block fallback;
        final BlockState[] decorations;
        final float decorationChance;
        /** Life melts snow: a snow layer on a cell is cleared and the cell converted (the others leave snowy cells). */
        final boolean meltsSnow;

        Style(String name, float coverage, Block[] grounds, float[] weights, Block fallback, BlockState[] decorations,
              float decorationChance, boolean meltsSnow) {
            this.name = name;
            this.coverage = coverage;
            this.grounds = grounds;
            this.cumulative = new float[weights.length];
            float sum = 0;
            for (float w : weights)
                sum += w;
            float acc = 0;
            for (int i = 0; i < weights.length; i++) {
                acc += weights[i] / sum;
                cumulative[i] = acc;
            }
            this.fallback = fallback;
            this.decorations = decorations;
            this.decorationChance = decorationChance;
            this.meltsSnow = meltsSnow;
        }

        Block groundFor(float roll) {
            for (int i = 0; i < cumulative.length; i++)
                if (roll < cumulative[i])
                    return grounds[i];
            return grounds[grounds.length - 1];
        }

        /** Every ground block this style may place (the GameTests check each is a full, walkable block). */
        public List<Block> groundBlocks() {
            Block[] all = Arrays.copyOf(grounds, grounds.length + 1);
            all[grounds.length] = fallback;
            return List.of(all);
        }

        /** Every decoration this style may place (the GameTests check each is walk-through). */
        public List<BlockState> decorationStates() {
            return List.of(decorations);
        }
    }

    private static BlockState petals(int amount, Direction facing) {
        return Blocks.PINK_PETALS.defaultBlockState().setValue(BlockStateProperties.FLOWER_AMOUNT, amount)
            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    /** Sunforged: a paved royal court - pale flagstones round the hall, no plants. */
    public static final Style ROYAL_COURT = new Style("royal_court", 0.40f,
        new Block[] { Blocks.POLISHED_ANDESITE, Blocks.SMOOTH_STONE, Blocks.STONE_BRICKS }, new float[] { 5, 3, 2 },
        Blocks.SMOOTH_STONE, new BlockState[0], 0f, false);
    /** Gravebound: blighted earth - podzol, coarse dirt and soul soil, a dead bush here and there. */
    public static final Style BLIGHT = new Style("blight", 0.55f,
        new Block[] { Blocks.PODZOL, Blocks.COARSE_DIRT, Blocks.SOUL_SOIL }, new float[] { 4, 4, 2 },
        Blocks.COARSE_DIRT, new BlockState[] { Blocks.DEAD_BUSH.defaultBlockState() }, 0.10f, false);
    /** Verdant Court: moss and grass in bloom - the Court's grove flowers. */
    public static final Style BLOOM = new Style("bloom", 0.65f,
        new Block[] { Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK }, new float[] { 11, 9 },
        Blocks.MOSS_BLOCK, new BlockState[] { Blocks.AZURE_BLUET.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.CORNFLOWER.defaultBlockState(), Blocks.ALLIUM.defaultBlockState() }, 0.25f, true);
    /** Tidewrought: a strand - sand, clay and shingle, a dead coral fan washed up. */
    public static final Style STRAND = new Style("strand", 0.50f,
        new Block[] { Blocks.SAND, Blocks.CLAY, Blocks.GRAVEL }, new float[] { 11, 6, 3 },
        Blocks.CLAY, new BlockState[] { Blocks.DEAD_HORN_CORAL_FAN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false),
            Blocks.DEAD_BRAIN_CORAL_FAN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false) }, 0.06f, false);
    /** Stormcallers: a temple garden - moss, grass and raked gravel under fallen cherry petals. */
    public static final Style GARDEN = new Style("garden", 0.50f,
        new Block[] { Blocks.MOSS_BLOCK, Blocks.GRASS_BLOCK, Blocks.GRAVEL }, new float[] { 4, 3, 3 },
        Blocks.MOSS_BLOCK, new BlockState[] { petals(2, Direction.NORTH), petals(3, Direction.EAST), petals(4, Direction.SOUTH),
            petals(1, Direction.WEST) }, 0.30f, false);
    /** Brassworks: worked ground - gravel, cobble and coarse dirt paths round the works. */
    public static final Style WORKINGS = new Style("workings", 0.50f,
        new Block[] { Blocks.GRAVEL, Blocks.COBBLESTONE, Blocks.COARSE_DIRT }, new float[] { 35, 35, 30 },
        Blocks.COBBLESTONE, new BlockState[0], 0f, false);

    private static final List<Style> STYLES = List.of(ROYAL_COURT, BLIGHT, BLOOM, STRAND, GARDEN, WORKINGS);
    private static final Map<FactionTraits, Style> STYLE_OF = new IdentityHashMap<>();
    static {
        STYLE_OF.put(FactionTraits.SUNFORGED, ROYAL_COURT);
        STYLE_OF.put(FactionTraits.GRAVEBOUND, BLIGHT);
        STYLE_OF.put(FactionTraits.VERDANT, BLOOM);
        STYLE_OF.put(FactionTraits.TIDEWROUGHT, STRAND);
        STYLE_OF.put(FactionTraits.STORMCALLERS, GARDEN);
        STYLE_OF.put(FactionTraits.BRASSWORKS, WORKINGS);
    }

    /** The faction's aura style, or null (the Horde, neutral buildings, factions without one). */
    public static Style styleOf(Faction faction) {
        return STYLE_OF.get(FactionTraits.of(faction));
    }

    /** The style saved under this name (FactionAuraServerEvents.restore), or null for a name this build doesn't know. */
    static Style styleNamed(String name) {
        for (Style s : STYLES)
            if (s.name.equals(name))
                return s;
        return null;
    }

    /** Every style (GameTests). */
    public static List<Style> styles() {
        return STYLES;
    }

    /** Natural ground an aura may convert. Never a building block, a resource, a patch block or anything placed. */
    static final Set<Block> CONVERTIBLE = Collections.newSetFromMap(new IdentityHashMap<>());
    static {
        Collections.addAll(CONVERTIBLE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL, Blocks.ROOTED_DIRT,
            Blocks.MOSS_BLOCK, Blocks.MYCELIUM, Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.SNOW_BLOCK);
    }

    /** A small plant or snow layer on a cell that may be cleared to convert it (taller plants: the cell is skipped). */
    static boolean isClearableCover(BlockState cover, Style style) {
        if (cover.is(Blocks.SNOW))
            return style.meltsSnow;
        return cover.is(Blocks.GRASS) || cover.is(Blocks.FERN) || cover.is(Blocks.DEAD_BUSH) || cover.is(Blocks.PINK_PETALS)
            || cover.is(BlockTags.SMALL_FLOWERS);
    }

    // ------------------------------------------------------------------ one aura

    public final long sourceKey;
    public final String owner;
    public final Style style;
    final int cx, cz, groundY, halfX, halfZ, radius;
    /** Packed (dx, dz) offsets this aura converts, in growth order (see plan()). */
    final int[] plan;
    int cursor = 0;
    long sweepAfter = 0;
    boolean receding = false;

    // converted cells in growth order, and per cell the ground / cover before and after (block state ids; -1 = the
    // cover was left as it was)
    final LongArrayList cells = new LongArrayList();
    final IntArrayList oldGround = new IntArrayList(), newGround = new IntArrayList();
    final IntArrayList oldCover = new IntArrayList(), newCover = new IntArrayList();

    /**
     * An aura for a footprint centred on (cx, cz) with half-extents halfX/halfZ, its foundation at groundY, reaching
     * radius blocks from the centre. Public for the GameTests; the registry makes them from buildings
     * ({@link #forBuilding}).
     */
    public FactionAura(long sourceKey, String owner, Style style, int cx, int cz, int groundY, int halfX, int halfZ, int radius) {
        this.sourceKey = sourceKey;
        this.owner = owner == null ? "" : owner;
        this.style = style;
        this.cx = cx;
        this.cz = cz;
        this.groundY = groundY;
        this.halfX = halfX;
        this.halfZ = halfZ;
        this.radius = Math.min(MAX_RADIUS, radius);
        this.plan = plan(style, cx, cz, halfX, halfZ, this.radius);
    }

    /** A capitol's skirt reaches this far past its walls, a lab's {@link #LAB_SKIRT}. */
    static final int CAPITOL_SKIRT = 6, LAB_SKIRT = 4;

    static FactionAura forBuilding(BuildingPlacement bp, Style style) {
        int halfX = halfX(bp), halfZ = halfZ(bp);
        return new FactionAura(bp.originPos.asLong(), bp.ownerName, style, centreX(bp), centreZ(bp), bp.minCorner.getY(),
            halfX, halfZ, radiusFor(bp, halfX, halfZ));
    }

    /** Whether this aura is the one {@link #forBuilding} would make for bp now (same style, centre and reach). */
    boolean fits(BuildingPlacement bp, Style style) {
        return style == this.style && cx == centreX(bp) && cz == centreZ(bp)
            && radius == Math.min(MAX_RADIUS, radiusFor(bp, halfX(bp), halfZ(bp)));
    }

    private static int halfX(BuildingPlacement bp) {
        return (bp.maxCorner.getX() - bp.minCorner.getX() + 1) / 2;
    }

    private static int halfZ(BuildingPlacement bp) {
        return (bp.maxCorner.getZ() - bp.minCorner.getZ() + 1) / 2;
    }

    private static int centreX(BuildingPlacement bp) {
        return (bp.minCorner.getX() + bp.maxCorner.getX()) >> 1;
    }

    private static int centreZ(BuildingPlacement bp) {
        return (bp.minCorner.getZ() + bp.maxCorner.getZ()) >> 1;
    }

    private static int radiusFor(BuildingPlacement bp, int halfX, int halfZ) {
        return Math.max(halfX, halfZ) + (bp.isCapitol ? CAPITOL_SKIRT : LAB_SKIRT);
    }

    static int pack(int dx, int dz) {
        return (dx << 16) | (dz & 0xFFFF);
    }

    static int unpackX(int packed) {
        return packed >> 16;
    }

    static int unpackZ(int packed) {
        return (short) packed;
    }

    /**
     * The cells an aura will try to convert, nearest the building first with a ragged front: each cell of the skirt is
     * picked with a chance that starts at the style's coverage against the walls and fades to nothing at the rim, and
     * ordered by its distance from the footprint plus a jitter, both from a hash of its world position.
     */
    static int[] plan(Style style, int cx, int cz, int halfX, int halfZ, int radius) {
        float width = Math.max(1, radius - Math.max(halfX, halfZ));
        long[] keyed = new long[(2 * radius + 1) * (2 * radius + 1)];
        int n = 0;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
                int ex = Math.max(0, Math.abs(dx) - halfX), ez = Math.max(0, Math.abs(dz) - halfZ);
                if (ex == 0 && ez == 0)
                    continue;   // under the building
                float edge = (float) Math.sqrt(ex * ex + ez * ez);
                if (edge > width)
                    continue;
                long pos = BlockPos.asLong(cx + dx, 0, cz + dz);
                float chance = style.coverage * (float) Math.pow(1 - edge / width, 0.8);
                if (BiomePalette.hash01(pos, SALT_SELECT) >= chance)
                    continue;
                float order = edge + 1.5f * BiomePalette.hash01(pos, SALT_ORDER);
                // positive floats sort like their bits: order in the high half, the offset in the low half
                keyed[n++] = ((long) Float.floatToIntBits(order) << 32) | (pack(dx, dz) & 0xFFFFFFFFL);
            }
        Arrays.sort(keyed, 0, n);
        int[] out = new int[n];
        for (int i = 0; i < n; i++)
            out[i] = (int) keyed[i];
        return out;
    }

    /** Cells this aura holds right now. */
    public int cellCount() {
        return cells.size();
    }

    /** The planned cells (GameTests). */
    public int plannedCells() {
        return plan.length;
    }

    public boolean isReceding() {
        return receding;
    }

    /** Starts (or stops) giving the ground back (FactionAuraServerEvents: the source is gone, the rule is off). */
    public void setReceding(boolean receding) {
        this.receding = receding;
    }

    /** False while the aura rests between sweeps of a finished plan (or has nothing planned, or recedes). */
    boolean wantsToGrow(long now) {
        return !receding && plan.length > 0 && (cursor < plan.length || now >= sweepAfter);
    }

    /**
     * Converts up to {@code budget} cells of the plan, examining at most {@link #EXAMINE_PER_STEP} candidates; returns
     * how many it converted. {@code owners} maps every converted cell of every aura to its aura (one cell, one owner).
     */
    public int grow(ServerLevel level, int budget, Obstacles obstacles, Long2ObjectOpenHashMap<FactionAura> owners,
                    BlockPos.MutableBlockPos p) {
        if (receding || budget <= 0 || plan.length == 0)
            return 0;
        long now = level.getGameTime();
        if (cursor >= plan.length) {
            if (now < sweepAfter)
                return 0;
            cursor = 0;   // sweep again: skipped cells may be free now (a tree felled, a chunk loaded)
        }
        int done = 0, looked = 0;
        while (done < budget && looked < EXAMINE_PER_STEP && cursor < plan.length) {
            int off = plan[cursor++];
            looked++;
            int x = cx + unpackX(off), z = cz + unpackZ(off);
            if (obstacles.blocks(x, z) || !level.isLoaded(p.set(x, groundY, z)))
                continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockState ground = level.getBlockState(p.set(x, top, z));
            if (ground.is(Blocks.SNOW))
                ground = level.getBlockState(p.set(x, --top, z));   // a snow layer the heightmap counted: it is cover
            if (Math.abs(top - groundY) > MAX_RISE || !CONVERTIBLE.contains(ground.getBlock()))
                continue;
            long key = BlockPos.asLong(x, top, z);
            if (owners.containsKey(key))
                continue;
            BlockState cover = level.getBlockState(p.set(x, top + 1, z));
            if (!cover.isAir() && !isClearableCover(cover, style))
                continue;
            Block groundBlock = style.groundFor(BiomePalette.hash01(key, SALT_GROUND));
            if (groundBlock instanceof FallingBlock && !level.getBlockState(p.set(x, top - 1, z)).isFaceSturdy(level, p, Direction.UP))
                groundBlock = style.fallback;
            if (groundBlock == ground.getBlock())
                continue;   // already the look (natural moss round a Verdant hall): nothing to own
            BlockState placedGround = groundBlock.defaultBlockState();
            BlockState deco = style.decorations.length > 0 && BiomePalette.hash01(key, SALT_DECO) < style.decorationChance
                ? style.decorations[(int) (BiomePalette.hash01(key, SALT_DECO_PICK) * style.decorations.length)] : null;
            // the cover goes first: a plant that would not survive on the new ground is cleared (not broken - no
            // drops), and a decoration replaces whatever small plant was there
            boolean coverChanged = !cover.isAir() && (deco != null || cover.is(Blocks.SNOW) || !placedGround.is(BlockTags.DIRT));
            if (coverChanged)
                level.setBlock(p.set(x, top + 1, z), Blocks.AIR.defaultBlockState(), FLAGS);
            level.setBlock(p.set(x, top, z), placedGround, FLAGS);
            BlockState placedCover = coverChanged ? Blocks.AIR.defaultBlockState() : null;
            if (deco != null && deco.canSurvive(level, p.set(x, top + 1, z))) {
                level.setBlock(p, deco, FLAGS);
                placedCover = deco;
            }
            cells.add(key);
            oldGround.add(Block.getId(ground));
            newGround.add(Block.getId(placedGround));
            oldCover.add(placedCover == null ? -1 : Block.getId(cover));
            newCover.add(placedCover == null ? -1 : Block.getId(placedCover));
            owners.put(key, this);
            done++;
        }
        if (cursor >= plan.length)
            sweepAfter = now + RESWEEP_TICKS;
        return done;
    }

    /**
     * Gives back up to {@code budget} cells, newest first; returns how many. A cell whose chunk is not loaded stops the
     * step (it is retried later, so the order holds). Each block is restored only while it is still what this aura
     * put there.
     */
    public int recede(ServerLevel level, int budget, Long2ObjectOpenHashMap<FactionAura> owners, BlockPos.MutableBlockPos p) {
        int done = 0;
        while (done < budget && !cells.isEmpty()) {
            int i = cells.size() - 1;
            long key = cells.getLong(i);
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            if (!level.isLoaded(p.set(x, y, z)))
                break;
            boolean groundOurs = Block.getId(level.getBlockState(p)) == newGround.getInt(i);
            int coverWas = oldCover.getInt(i);
            // the cover first: our flower goes before the old ground comes back under it (no plant left on a block it
            // can't stand on); an old plant is only put back over the old ground - on ground someone else changed,
            // the aura just takes its own decoration away
            if (coverWas >= 0 && Block.getId(level.getBlockState(p.set(x, y + 1, z))) == newCover.getInt(i)) {
                BlockState was = Block.stateById(coverWas);
                level.setBlock(p, groundOurs || was.isAir() ? was : Blocks.AIR.defaultBlockState(), FLAGS);
            }
            if (groundOurs)
                level.setBlock(p.set(x, y, z), Block.stateById(oldGround.getInt(i)), FLAGS);
            cells.removeLong(i);
            oldGround.removeInt(i);
            newGround.removeInt(i);
            oldCover.removeInt(i);
            newCover.removeInt(i);
            if (owners.get(key) == this)
                owners.remove(key);
            done++;
        }
        return done;
    }

    /** Re-adds a restored cell (FactionAuraServerEvents.restore). */
    void addCell(long key, int oldG, int newG, int oldC, int newC) {
        cells.add(key);
        oldGround.add(oldG);
        newGround.add(newG);
        oldCover.add(oldC);
        newCover.add(newC);
    }

    // ------------------------------------------------------------------ what an aura must keep off

    /**
     * Columns an aura may not touch, gathered fresh for each aura it is about to grow (buildings come and go):
     * building boxes grown by one, metal patches' reserved squares and Nether Zones. Reused - nothing is allocated
     * once its arrays have grown to the busiest base.
     */
    public static final class Obstacles {
        private final IntArrayList boxes = new IntArrayList();     // minX, minZ, maxX, maxZ
        private final IntArrayList patches = new IntArrayList();   // centre x, z
        private final IntArrayList zones = new IntArrayList();     // centre x, z, radius (ceil)

        public void clear() {
            boxes.clear();
            patches.clear();
            zones.clear();
        }

        public void addBox(int minX, int minZ, int maxX, int maxZ) {
            boxes.add(minX);
            boxes.add(minZ);
            boxes.add(maxX);
            boxes.add(maxZ);
        }

        public void addPatch(int x, int z) {
            patches.add(x);
            patches.add(z);
        }

        public void addZone(int x, int z, int radius) {
            zones.add(x);
            zones.add(z);
            zones.add(radius);
        }

        /** Adds everything another set holds (a GameTest's patches and boxes on top of the live world's). */
        public void copyFrom(Obstacles other) {
            boxes.addAll(other.boxes);
            patches.addAll(other.patches);
            zones.addAll(other.zones);
        }

        public boolean blocks(int x, int z) {
            for (int i = 0; i < boxes.size(); i += 4)
                if (x >= boxes.getInt(i) && z >= boxes.getInt(i + 1) && x <= boxes.getInt(i + 2) && z <= boxes.getInt(i + 3))
                    return true;
            int r = com.solegendary.reignofnether.resources.MetalPatches.RESERVED_HALF;
            for (int i = 0; i < patches.size(); i += 2)
                if (Math.abs(x - patches.getInt(i)) <= r && Math.abs(z - patches.getInt(i + 1)) <= r)
                    return true;
            for (int i = 0; i < zones.size(); i += 3) {
                int dx = x - zones.getInt(i), dz = z - zones.getInt(i + 1), zr = zones.getInt(i + 2);
                if (dx * dx + dz * dz <= zr * zr)
                    return true;
            }
            return false;
        }
    }
}
