package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
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
 * column's weathering from the seed and its index - so a world saved half-way carries on from the column it
 * reached (the count is saved with the match) and ends up with the wall it would have had.
 */
public class BattlefieldWall {

    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    static final int WALL_HEIGHT = 7;        // rampart body above ground
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
        planRing(level, cx, cz, radius, COLUMNS_PER_TICK, 0, true);
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
        if (level == null)
            return;
        towerEvery = 40 + random.nextInt(17);
        mossPct = 5 + random.nextInt(11);
        crackedPct = 4 + random.nextInt(6);
        material = MATERIALS[Math.max(0, Math.min(MATERIALS.length - 1, materialIndex))];
        gatehouseEvery = 5;
        columnSeed = 0;
        planRing(level, cx, cz, radius, COLUMNS_PER_TICK, 0, true);
    }

    /**
     * A prepared match's wall (match.MatchPrep): {@code radius} round (cx, cz) in material {@code materialIndex},
     * {@code perTick} columns a tick, every roll (tower rhythm, weathering, each column's body blocks) drawn from
     * {@code seed} so the same call after a reload lays the same blocks, starting at column {@code fromColumn} -
     * the count a save mid-wall kept; the columns before it stand already. The arena is NOT generated from here:
     * the preparation scouts it as its own stage, after the wall, with its own budget.
     */
    public static void raise(ServerLevel level, double cx, double cz, double radius, int materialIndex, long seed,
                             int perTick, int fromColumn) {
        if (level == null)
            return;
        Random rolls = new Random(seed);
        towerEvery = 40 + rolls.nextInt(17);
        mossPct = 5 + rolls.nextInt(11);
        crackedPct = 4 + rolls.nextInt(6);
        material = MATERIALS[Math.max(0, Math.min(MATERIALS.length - 1, materialIndex))];
        gatehouseEvery = 5;
        columnSeed = seed == 0 ? 1 : seed;
        planRing(level, cx, cz, radius, Math.max(1, perTick), Math.max(0, fromColumn), false);
    }

    /** How many columns a ring of {@code radius} round (cx, cz) has (distinct block columns on the circle). */
    public static int ringLength(double cx, double cz, double radius) {
        int steps = (int) Math.ceil(Math.PI * 2 * radius);
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = (int) Math.round(cx + Math.cos(a) * radius);
            int z = (int) Math.round(cz + Math.sin(a) * radius);
            seen.add(((long) x << 32) ^ (z & 0xffffffffL));
        }
        return seen.size();
    }

    static void planRing(ServerLevel level, double cx, double cz, double radius, int perTick, int fromColumn,
                         boolean pregen) {
        int steps = (int) Math.ceil(Math.PI * 2 * radius);

        queue.clear();
        Set<Long> seen = new HashSet<>();
        int col = 0;
        int towers = 0;
        for (int i = 0; i < steps; i++) {
            double a = Math.PI * 2 * i / steps;
            int x = (int) Math.round(cx + Math.cos(a) * radius);
            int z = (int) Math.round(cz + Math.sin(a) * radius);
            if (!seen.add(((long) x << 32) ^ (z & 0xffffffffL)))
                continue;
            boolean tower = col % towerEvery == 0;
            boolean gatehouse = tower && towers++ % gatehouseEvery == gatehouseEvery - 1;
            if (col >= fromColumn)   // the columns before stand already (a resume)
                queue.add(new Column(col, x, z, a, tower, col % 2 == 0, gatehouse));
            col++;
        }
        planned = col;
        columnsPerTick = perTick;
        buildLevel = level;
        ArenaStructureClearing.setArenaRadius(level, (float) cx, (float) cz, (float) radius);
        // the whole arena generated in the background, so a spectator's camera never waits on world generation
        if (pregen)
            ArenaPregen.start(level, cx, cz, radius);
        ReignOfNether.LOGGER.info("[BattlefieldWall] raising a {} ring wall: centre [{}, {}], radius {}, {} columns at {}"
            + " a tick{}", material.name(), (int) cx, (int) cz, (int) radius, col, perTick,
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
        for (int x = tx - 1; x <= tx + 1; x++)
            for (int z = tz - 1; z <= tz + 1; z++) {
                boolean edge = x == tx - 1 || x == tx + 1 || z == tz - 1 || z == tz + 1;
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
        for (int x = gx - 2; x <= gx + 2; x++)
            for (int z = gz - 2; z <= gz + 2; z++) {
                int b = groundY(level, x, z);
                fillColumn(level, x, z, Math.min(b, base) - 2, top);
            }
        // twin turrets on the two ends along the wall line, with accent bands and crenellations
        double tx = -Math.sin(angle), tz = Math.cos(angle);   // tangent to the ring
        for (int side = -1; side <= 1; side += 2) {
            int cx = (int) Math.round(gx + tx * 2 * side);
            int cz = (int) Math.round(gz + tz * 2 * side);
            for (int y = top + 1; y <= top + 3; y++)
                level.setBlock(new BlockPos(cx, y, cz), body(), FLAGS);
            level.setBlock(new BlockPos(cx, top + 2, cz), mat().accent(), FLAGS);
            level.setBlock(new BlockPos(cx, top + 4, cz), mat().crenel(), FLAGS);
        }
        // the sealed portcullis on the inner face: iron bars set into a dark recess, an accent keystone above
        double inX = -Math.cos(angle), inZ = -Math.sin(angle);   // towards the ring centre
        for (int w = -1; w <= 1; w++) {
            int fx = (int) Math.round(gx + inX * 2 + tx * w);
            int fz = (int) Math.round(gz + inZ * 2 + tz * w);
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

    /** The ground the wall founds on at (x, z): top solid-ish surface, water surface over rivers and lakes. */
    static int groundY(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }
}
