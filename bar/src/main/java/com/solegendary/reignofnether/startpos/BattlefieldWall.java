package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The battlefield boundary: when a match forms, a great ring wall rises around all the start positions - stone
 * brick ramparts with crenellations and watchtowers, following the terrain like the Great Wall. It bounds the
 * arena (no more wandering off hunting for the enemy) without giving anyone's base away.
 *
 * Built a few columns per tick so even a 2km wall never causes a lag spike. A classic start (/bot add, Quick
 * Battle's instant start) still raises it over the opening half minute of the match; a prepared match (MatchPrep)
 * raises it BEFORE the start phase at four times the speed ({@link #raise}), because nobody is playing yet - the
 * Oct 17 playtest had the wall and the arena generation running during the first 35 s of play and the server
 * 40 ticks behind. A prepared wall is deterministic from the setup's seed - the ring's rolls from the seed, each
 * column's weathering from the seed and its index, every column founded on the ground as it was BEFORE the wall
 * ({@link #groundY} caches it ahead of the first write) - so a world saved half-way carries on from the column it
 * reached (the count and the ground readings travel in the save, {@link #saveProgress}) and ends up with the wall it
 * would have had.
 */
public class BattlefieldWall {

    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    static final int WALL_HEIGHT = 7;        // rampart body above ground
    /**
     * Blocks either side of a ring column that the widest wall pieces fill: a watchtower is 3x3 round its column
     * (TOWER_HALF), a gatehouse 5x5 (GATEHOUSE_HALF); the plain wall is the column and one towards the centre. Plain
     * ints (inlined by javac), so maps.ExtractorClearance asks them without loading this class: the metal fill keeps an
     * extractor's whole footprint clear of the widest piece, wherever on the ring one stands.
     */
    public static final int TOWER_HALF = 1, GATEHOUSE_HALF = 2;
    static final int COLUMNS_PER_TICK = 40;  // build speed in play (~30-90s for a full ring)
    static final int MAX_RADIUS = 520;
    // every match rolls its own arena: breathing room, tower spacing and weathering all vary
    static int towerEvery = 48;
    static int mossPct = 10;
    static int crackedPct = 8;
    static int gatehouseEvery = 5;   // every Nth tower is a gatehouse (purely visual; still impassable)
    static Material material;

    /**
     * A wall material set: main body, two weathered variants, crenellation and walkway. Each match rolls one,
     * so one game is fought inside grey ramparts, the next inside desert sandstone or blackstone.
     */
    record Material(String name, BlockState body, BlockState weathered, BlockState cracked,
                    BlockState crenel, BlockState walk, BlockState accent) { }

    static final Material[] MATERIALS = {
        new Material("stone brick", Blocks.STONE_BRICKS.defaultBlockState(),
            Blocks.MOSSY_STONE_BRICKS.defaultBlockState(), Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
            Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.STONE_BRICK_SLAB.defaultBlockState(),
            Blocks.CHISELED_STONE_BRICKS.defaultBlockState()),
        new Material("sandstone", Blocks.CUT_SANDSTONE.defaultBlockState(),
            Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.SANDSTONE_WALL.defaultBlockState(), Blocks.CUT_SANDSTONE_SLAB.defaultBlockState(),
            Blocks.CHISELED_SANDSTONE.defaultBlockState()),
        new Material("deepslate", Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
            Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
            Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState(),
            Blocks.CHISELED_DEEPSLATE.defaultBlockState()),
        new Material("blackstone", Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            Blocks.BLACKSTONE.defaultBlockState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState(),
            Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState(),
            Blocks.GILDED_BLACKSTONE.defaultBlockState()),
        new Material("mud brick", Blocks.MUD_BRICKS.defaultBlockState(),
            Blocks.PACKED_MUD.defaultBlockState(), Blocks.MUD_BRICKS.defaultBlockState(),
            Blocks.MUD_BRICK_WALL.defaultBlockState(), Blocks.MUD_BRICK_SLAB.defaultBlockState(),
            Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState()),
    };

    /** One column of wall still to be built; {@code index} is its place on the ring (the save's column count). */
    record Column(int index, int x, int z, double angle, boolean tower, boolean crenel, boolean gatehouse) { }

    static final ArrayDeque<Column> queue = new ArrayDeque<>();
    static ServerLevel buildLevel = null;
    static final Random random = new Random();
    /** The running build's speed and size (for the preparation's progress). */
    static int columnsPerTick = COLUMNS_PER_TICK;
    static int planned = 0;
    /** A prepared build's seed: every column's weathering is drawn from it and the column's index. 0: in-play rolls. */
    static long columnSeed = 0;
    /**
     * The ground of every cell this build read, as it was before the build wrote there (key: x << 32 ^ z). A column
     * next to a tower used to read the tower's top off the heightmap and found a rampart on it, seven blocks up (a
     * spike at every tower); the preparation's resume test caught it: the second run of the same ring differed in
     * 73 blocks. With the ground read once, ahead of any write, each column is a function of the seed, its index
     * and the terrain before the wall. Cleared with the plan.
     */
    static final Map<Long, Integer> ground = new HashMap<>();

    /** Plans the ring and starts the per-tick build. Safe to call once per match. */
    public static void build(ServerLevel level, List<BlockPos> positions) {
        build(level, positions, -1);
    }

    /** radiusOverride > 0 pins the ring radius (lobby arena size); otherwise it is rolled. */
    public static void build(ServerLevel level, List<BlockPos> positions, int radiusOverride) {
        if (level == null || positions.isEmpty())
            return;
        double cx = 0, cz = 0;
        for (BlockPos p : positions) {
            cx += p.getX() + 0.5;
            cz += p.getZ() + 0.5;
        }
        cx /= positions.size();
        cz /= positions.size();
        double radius = 0;
        for (BlockPos p : positions)
            radius = Math.max(radius, Math.hypot(p.getX() + 0.5 - cx, p.getZ() + 0.5 - cz));
        // roll this match's arena: a roomy ring (sometimes vast), with its own tower rhythm and weathering
        int margin = 130 + random.nextInt(140);        // 130-270 beyond the farthest base
        int minRadius = 200 + random.nextInt(120);     // 200-320 even for close spawns
        radius = Math.min(MAX_RADIUS, Math.max(minRadius, radius + margin));
        if (radiusOverride > 0)   // the lobby asked for a size; never tighter than the bases need
            radius = Math.min(MAX_RADIUS + 200, Math.max(radius - margin + 60, radiusOverride));
        towerEvery = 36 + random.nextInt(29);          // 36-64 columns between watchtowers
        mossPct = 5 + random.nextInt(21);              // 5-25% weathered blocks
        crackedPct = 4 + random.nextInt(10);
        material = MATERIALS[rollMaterial(random)];
        gatehouseEvery = 4 + random.nextInt(4);
        columnSeed = 0;
        planWall(level, cx, cz, radius, false, COLUMNS_PER_TICK, 0, true);
    }

    /**
     * The roll of {@link #build}: stone brick stays the most common; the others make some matches look like a
     * different world. An index into the material set, so a prepared match can roll it from its saved seed.
     */
    public static int rollMaterial(Random rng) {
        return rng.nextInt(100) < 40 ? 0 : 1 + rng.nextInt(MATERIALS.length - 1);
    }

    /**
     * A pre-made map's wall (design/maps.md): exactly on the map's edge round its centre, in the map's own material
     * (sandstone round the desert and the islands, blackstone round the Nether Rift...). Index into MATERIALS. In
     * play speed, with the arena generated behind it - the classic formation's road.
     */
    public static void buildAt(ServerLevel level, double cx, double cz, double radius, int materialIndex) {
        buildAt(level, cx, cz, radius, materialIndex, false);
    }

    /** As above; {@code square}: straight sides {@code radius} from the centre (a square generated map), corner towers. */
    public static void buildAt(ServerLevel level, double cx, double cz, double radius, int materialIndex, boolean square) {
        if (level == null)
            return;
        towerEvery = 40 + random.nextInt(17);
        mossPct = 5 + random.nextInt(11);
        crackedPct = 4 + random.nextInt(6);
        material = MATERIALS[Math.max(0, Math.min(MATERIALS.length - 1, materialIndex))];
        gatehouseEvery = 5;
        columnSeed = 0;
        planWall(level, cx, cz, radius, square, COLUMNS_PER_TICK, 0, true);
    }

    /**
     * A prepared match's wall (match.MatchPrep): {@code radius} round (cx, cz) in material {@code materialIndex},
     * {@code perTick} columns a tick, every roll (tower rhythm, weathering, each column's body blocks) drawn from
     * {@code seed} so the same call after a reload lays the same blocks. {@code resume} is a save's
     * {@link #saveProgress}: the ring carries on at the column count it holds (the columns before stand already)
     * with the ground readings it holds, so the cells the standing part shares with the rest - a tower's footprint,
     * the ring's closing columns - found on the earth and not on the wall; null for a fresh ring. The arena is NOT
     * generated from here: the preparation scouts it as its own stage, after the wall, with its own budget.
     */
    public static void raise(ServerLevel level, double cx, double cz, double radius, int materialIndex, long seed,
                             int perTick, @Nullable CompoundTag resume) {
        raise(level, cx, cz, radius, materialIndex, seed, perTick, resume, false);
    }

    /** As above; {@code square}: the wall of a square arena (straight sides {@code radius} from the centre). */
    public static void raise(ServerLevel level, double cx, double cz, double radius, int materialIndex, long seed,
                             int perTick, @Nullable CompoundTag resume, boolean square) {
        if (level == null)
            return;
        Random rolls = new Random(seed);
        towerEvery = 40 + rolls.nextInt(17);
        mossPct = 5 + rolls.nextInt(11);
        crackedPct = 4 + rolls.nextInt(6);
        material = MATERIALS[Math.max(0, Math.min(MATERIALS.length - 1, materialIndex))];
        gatehouseEvery = 5;
        columnSeed = seed == 0 ? 1 : seed;
        int fromColumn = resume == null ? 0 : Math.max(0, resume.getInt("columns"));
        planWall(level, cx, cz, radius, square, Math.max(1, perTick), fromColumn, false);
        if (resume != null) {
            int[] flat = resume.getIntArray("ground");
            for (int i = 0; i + 2 < flat.length; i += 3)
                ground.put(((long) flat[i] << 32) ^ (flat[i + 1] & 0xffffffffL), flat[i + 2]);
        }
    }

    /**
     * The running ring's progress for the match save (match.MatchPrep): the columns done and every cell's ground as
     * read before the wall touched it - what {@link #raise} needs to lay the rest exactly as one uninterrupted
     * build would. Null when no ring is being raised.
     */
    @Nullable
    public static CompoundTag saveProgress() {
        if (planned <= 0)
            return null;
        CompoundTag tag = new CompoundTag();
        tag.putInt("columns", columnsDone());
        int[] flat = new int[ground.size() * 3];
        int i = 0;
        for (Map.Entry<Long, Integer> e : ground.entrySet()) {
            long key = e.getKey();
            flat[i++] = (int) (key >> 32);
            flat[i++] = (int) key;
            flat[i++] = e.getValue();
        }
        tag.putIntArray("ground", flat);
        return tag;
    }

    /** How many columns a ring of {@code radius} round (cx, cz) has (distinct block columns on the circle). */
    public static int ringLength(double cx, double cz, double radius) {
        return ringColumns(cx, cz, radius).size();
    }

    /**
     * The distinct block columns {x, z} of a ring of {@code radius} round (cx, cz), in ring order - the cells
     * planWall visits on a round arena, without the world. A watchtower or gatehouse is centred on one of them, and
     * the plain wall's second layer is one block towards the centre; the game tests lay the widest piece on every one
     * of them to prove what the metal fill keeps clear.
     */
    public static List<int[]> ringColumns(double cx, double cz, double radius) {
        int steps = (int) Math.ceil(Math.PI * 2 * radius);
        Set<Long> seen = new HashSet<>();
        List<int[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = (int) Math.round(cx + Math.cos(a) * radius);
            int z = (int) Math.round(cz + Math.sin(a) * radius);
            if (seen.add(((long) x << 32) ^ (z & 0xffffffffL)))
                out.add(new int[] { x, z });
        }
        return out;
    }

    /** How many columns the wall of a square arena of half side {@code half} round (cx, cz) has. */
    public static int squareLength(double cx, double cz, double half) {
        return outline(cx, cz, half, true).size();
    }

    /**
     * The wall's line, column by column in order round the arena: {x, z, outward normal angle, corner 0/1}. A ring
     * samples the circle (each block column once); a square runs its four straight sides (lovish Oct 21: "square maps
     * ... as well as round"), each corner the first column of a side, the normal square to the side so the inner face
     * and the gatehouses' arches sit straight.
     */
    static java.util.List<double[]> outline(double cx, double cz, double radius, boolean square) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        if (square) {
            int x0 = (int) Math.round(cx - radius), x1 = (int) Math.round(cx + radius);
            int z0 = (int) Math.round(cz - radius), z1 = (int) Math.round(cz + radius);
            for (int z = z0; z < z1; z++)
                out.add(new double[] { x1, z, 0, z == z0 ? 1 : 0 });
            for (int x = x1; x > x0; x--)
                out.add(new double[] { x, z1, Math.PI / 2, x == x1 ? 1 : 0 });
            for (int z = z1; z > z0; z--)
                out.add(new double[] { x0, z, Math.PI, z == z1 ? 1 : 0 });
            for (int x = x0; x < x1; x++)
                out.add(new double[] { x, z0, -Math.PI / 2, x == x0 ? 1 : 0 });
            return out;
        }
        int steps = (int) Math.ceil(Math.PI * 2 * radius);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = (int) Math.round(cx + Math.cos(a) * radius);
            int z = (int) Math.round(cz + Math.sin(a) * radius);
            if (seen.add(((long) x << 32) ^ (z & 0xffffffffL)))
                out.add(new double[] { x, z, a, 0 });
        }
        return out;
    }

    static void planWall(ServerLevel level, double cx, double cz, double radius, boolean square, int perTick,
                         int fromColumn, boolean pregen) {
        // every arena gets the RTS pathfinder and a warm navmesh, whatever its size (worldborder/ArenaPathing); its
        // circle covers a square arena's corners too
        com.solegendary.reignofnether.worldborder.ArenaPathing.onArena(level, cx, cz, square ? radius * Math.sqrt(2) : radius);
        queue.clear();
        ground.clear();
        int col = 0;
        int towers = 0;
        for (double[] c : outline(cx, cz, radius, square)) {
            boolean corner = c[3] > 0;
            // a square's corners always carry a tower (and never a gatehouse: its arch would face one side only)
            boolean tower = corner || col % towerEvery == 0;
            boolean gatehouse = tower && !corner && towers++ % gatehouseEvery == gatehouseEvery - 1;
            if (col >= fromColumn)   // the columns before stand already (a resume)
                queue.add(new Column(col, (int) c[0], (int) c[1], c[2], tower, col % 2 == 0, gatehouse));
            col++;
        }
        planned = col;
        columnsPerTick = perTick;
        buildLevel = level;
        // what must cover the whole arena covers a square's corners too
        double cover = square ? radius * Math.sqrt(2) : radius;
        ArenaStructureClearing.setArenaRadius(level, (float) cx, (float) cz, (float) cover);
        // the whole arena generated in the background, so a spectator's camera never waits on world generation
        if (pregen)
            ArenaPregen.start(level, cx, cz, cover);
        ReignOfNether.LOGGER.info("[BattlefieldWall] raising a {} {} wall: centre [{}, {}], {} {}, {} columns"
            + " at {} a tick{}", material.name(), square ? "square" : "ring", (int) cx, (int) cz,
            square ? "half side" : "radius", (int) radius, col, perTick,
            fromColumn > 0 ? " (resumed at column " + Math.min(fromColumn, col) + ")" : "");
    }

    public static boolean isBuilding() {
        return !queue.isEmpty();
    }

    /** Columns of the current ring (0 when none was planned). */
    public static int columnsPlanned() {
        return planned;
    }

    /** Columns of the current ring already built (a resumed ring counts the columns it started past). */
    public static int columnsDone() {
        return planned - queue.size();
    }

    /** Drop the build (what stands stays). */
    public static void cancel() {
        queue.clear();
        ground.clear();
        planned = 0;
        columnSeed = 0;
        buildLevel = null;
    }

    /** Builds every queued column now (the game tests; never in play - a 2km ring in one tick is the lag spike). */
    public static void finishNow() {
        buildNow(Integer.MAX_VALUE);
    }

    /** Builds up to {@code columns} queued columns now (the game tests). Returns how many it built. */
    public static int buildNow(int columns) {
        int built = 0;
        while (built < columns && !queue.isEmpty() && buildLevel != null) {
            buildColumn(buildLevel, queue.poll());
            built++;
        }
        return built;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        queue.clear();
        ground.clear();
        planned = 0;
        columnSeed = 0;
        buildLevel = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || queue.isEmpty() || buildLevel == null)
            return;
        for (int n = 0; n < columnsPerTick && !queue.isEmpty(); n++)
            buildColumn(buildLevel, queue.poll());
        if (queue.isEmpty())
            ReignOfNether.LOGGER.info("[BattlefieldWall] the wall is complete ({} columns)", planned);
    }

    static BlockState body() {
        Material m = material != null ? material : MATERIALS[0];
        int r = random.nextInt(100);
        if (r < mossPct) return m.weathered();
        if (r < mossPct + crackedPct) return m.cracked();
        return m.body();
    }

    static Material mat() {
        return material != null ? material : MATERIALS[0];
    }

    static void buildColumn(ServerLevel level, Column c) {
        // a prepared wall: this column's weathering from the seed and its index, whatever was built before it
        if (columnSeed != 0)
            random.setSeed(columnSeed ^ ((c.index() + 1) * 0x9E3779B97F4A7C15L));
        // the wall runs 2 thick: this column and one block towards the ring centre
        int ix = (int) Math.round(c.x() - Math.cos(c.angle()));
        int iz = (int) Math.round(c.z() - Math.sin(c.angle()));

        if (c.gatehouse()) {
            buildGatehouse(level, c.x(), c.z(), c.angle());
            return;
        }
        if (c.tower()) {
            buildTower(level, c.x(), c.z());
            return;
        }
        int outerBase = groundY(level, c.x(), c.z());
        int innerBase = groundY(level, ix, iz);
        int top = Math.max(outerBase, innerBase) + WALL_HEIGHT;

        // outer face: body + crenellation on alternating columns
        fillColumn(level, c.x(), c.z(), outerBase - 2, top);
        if (c.crenel())
            level.setBlock(new BlockPos(c.x(), top + 1, c.z()), mat().crenel(), FLAGS);

        // inner face: body + walkway slab
        if (ix != c.x() || iz != c.z()) {
            fillColumn(level, ix, iz, innerBase - 2, top);
            level.setBlock(new BlockPos(ix, top + 1, iz), mat().walk(), FLAGS);
        }
    }

    static void buildTower(ServerLevel level, int tx, int tz) {
        int base = groundY(level, tx, tz);
        int top = base + WALL_HEIGHT + 4;
        // the eight cells round the centre are read (cached) before anything is written: the next columns of the
        // ring share them and must see the ground, not this tower
        for (int x = tx - TOWER_HALF; x <= tx + TOWER_HALF; x++)
            for (int z = tz - TOWER_HALF; z <= tz + TOWER_HALF; z++)
                groundY(level, x, z);
        for (int x = tx - TOWER_HALF; x <= tx + TOWER_HALF; x++)
            for (int z = tz - TOWER_HALF; z <= tz + TOWER_HALF; z++) {
                boolean edge = x == tx - TOWER_HALF || x == tx + TOWER_HALF || z == tz - TOWER_HALF || z == tz + TOWER_HALF;
                fillColumn(level, x, z, base - 2, top);
                // corner crenellations and a lit beacon so towers read at night
                if (edge && (x != tx && z != tz))
                    level.setBlock(new BlockPos(x, top + 1, z), mat().crenel(), FLAGS);
            }
        level.setBlock(new BlockPos(tx, top + 1, tz), Blocks.CAMPFIRE.defaultBlockState(), FLAGS);
    }

    /**
     * A gatehouse: a wide twin-towered block with a sealed portcullis arch on its inner face - it reads as a
     * gate from the battlefield but stays solid, so the arena boundary is never breached.
     */
    static void buildGatehouse(ServerLevel level, int gx, int gz, double angle) {
        int base = groundY(level, gx, gz);
        int top = base + WALL_HEIGHT + 3;
        // every cell of the block read (cached) before the first write, as the tower does
        for (int x = gx - GATEHOUSE_HALF; x <= gx + GATEHOUSE_HALF; x++)
            for (int z = gz - GATEHOUSE_HALF; z <= gz + GATEHOUSE_HALF; z++)
                groundY(level, x, z);
        for (int x = gx - GATEHOUSE_HALF; x <= gx + GATEHOUSE_HALF; x++)
            for (int z = gz - GATEHOUSE_HALF; z <= gz + GATEHOUSE_HALF; z++) {
                int b = groundY(level, x, z);
                fillColumn(level, x, z, Math.min(b, base) - 2, top);
            }
        // twin turrets on the two ends along the wall line, with accent bands and crenellations
        double tx = -Math.sin(angle), tz = Math.cos(angle);   // tangent to the ring
        for (int side = -1; side <= 1; side += 2) {
            int cx = (int) Math.round(gx + tx * GATEHOUSE_HALF * side);
            int cz = (int) Math.round(gz + tz * GATEHOUSE_HALF * side);
            for (int y = top + 1; y <= top + 3; y++)
                level.setBlock(new BlockPos(cx, y, cz), body(), FLAGS);
            level.setBlock(new BlockPos(cx, top + 2, cz), mat().accent(), FLAGS);
            level.setBlock(new BlockPos(cx, top + 4, cz), mat().crenel(), FLAGS);
        }
        // the sealed portcullis on the inner face: iron bars set into a dark recess, an accent keystone above
        double inX = -Math.cos(angle), inZ = -Math.sin(angle);   // towards the ring centre
        for (int w = -1; w <= 1; w++) {
            int fx = (int) Math.round(gx + inX * GATEHOUSE_HALF + tx * w);
            int fz = (int) Math.round(gz + inZ * GATEHOUSE_HALF + tz * w);
            int fb = groundY(level, fx, fz);
            for (int y = fb + 1; y <= fb + 4; y++)
                level.setBlock(new BlockPos(fx, y, fz), Blocks.IRON_BARS.defaultBlockState(), FLAGS);
            level.setBlock(new BlockPos(fx, fb + 5, fz), mat().accent(), FLAGS);
        }
        // a lantern-lit banner of flame atop the gate
        level.setBlock(new BlockPos(gx, top + 1, gz), Blocks.CAMPFIRE.defaultBlockState(), FLAGS);
    }

    static void fillColumn(ServerLevel level, int x, int z, int from, int to) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int min = Math.max(level.getMinBuildHeight() + 1, from);
        for (int y = min; y <= to; y++)
            level.setBlock(p.set(x, y, z), body(), FLAGS);
    }

    /**
     * The ground the wall founds on at (x, z): top solid-ish surface, water surface over rivers and lakes - read from
     * the heightmap the first time this build asks for the cell and remembered, so a cell the wall has written since
     * (a tower's footprint, a neighbour's inner face) still founds on the earth. Every writer asks for its cells
     * before writing. The cache travels in the match save ({@link #saveProgress}), so a resumed ring reads the same
     * ground under the cells the standing part shares with the rest (the ring's closing columns meet the first
     * tower).
     */
    static int groundY(ServerLevel level, int x, int z) {
        long key = ((long) x << 32) ^ (z & 0xffffffffL);
        Integer known = ground.get(key);
        if (known != null)
            return known;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        ground.put(key, y);
        return y;
    }
}
