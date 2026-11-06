package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.MapBuilder;
import com.solegendary.reignofnether.maps.MapPreset;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.TreeClearing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tree shapes (lovish, Nov 6: "the tree looked terrible"): every tree our battle maps grow reads as a tree from the
 * RTS camera - a trunk you can see under the crown and a crown that is round from above, not a leaf cube - and stays
 * a natural tree to resources/TreeClearing. The trees are grown through the same calls the game makes: the configured
 * feature behind each tree the battlefield biome places, and MapBuilder.paletteFor(preset).tree(), which
 * MapBuilder.decorate places with ConfiguredFeature.place.
 *
 * Rules (two-sided, reasons fixed before the build):
 * <ul>
 *   <li>R1 visible trunk: the bare trunk (lowest leaf above the ground, minus one) is at least {@link #MIN_BARE} on
 *       every tree and at least {@link #MIN_MEAN_BARE} on average - the RTS camera (tilted 64.8 degrees) loses about
 *       one block of trunk behind a crown's front edge; and the lowest leaf is at most {@link #MAX_BARE} up (a crown
 *       on a trunk, not a pole; trees never hide the view).</li>
 *   <li>R2 round crown: the leaves' top-down footprint fills at most {@link #MAX_FOOTPRINT_FILL} of its bounding box
 *       (a disc fills 0.785, a 5 x 5 leaf block with a corner off 0.96) and at least {@link #MIN_FOOTPRINT_FILL}
 *       (one crown mass, not scattered leaves).</li>
 *   <li>R3 a natural tree: every leaf is natural (not persistent, distance under 7, so nothing decays), TreeClearing
 *       finds one tree holding every log grown, and felling it leaves nothing standing but ground plants.</li>
 * </ul>
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TreeShapeGameTests {

    static final ResourceLocation BATTLEFIELD = new ResourceLocation(ReignOfNether.MOD_ID, "battlefield");
    /** Trees grown per feature: vanilla's oak meets R1 one time in three, so twelve trees cannot pass by luck. */
    static final int SAMPLES = 12;
    static final int MIN_BARE = 2;
    static final double MIN_MEAN_BARE = 3.0;
    static final int MAX_BARE = 10;
    static final double MAX_FOOTPRINT_FILL = 0.80;
    static final double MIN_FOOTPRINT_FILL = 0.30;
    /** Half the pad: a tree's logs stay within 7 of its trunk (TreeClearing.MAX_TREE_REACH); our crowns reach 7, vanilla's
     *  fancy oak about 7. Kept under the GameTest spacing so two tests' pads never meet. */
    static final int HALF = 9;
    static final int HEIGHT = 30;
    /** Above the arena and every other test's scratch pad (they use y 170-190); each test its own height. */
    static final int BIOME_PAD_Y = 226, MAP_PAD_Y = 262;

    /** R4: the skirmish world's biome places no vanilla tree, and places at least one tree. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void battlefield_biome_grows_no_vanilla_trees(GameTestHelper helper) {
        Map<String, Holder<PlacedFeature>> trees = battlefieldTrees(helper.getLevel());
        if (trees == null) {
            helper.fail("no biome " + BATTLEFIELD + " in the registry");
            return;
        }
        List<String> vanilla = new ArrayList<>();
        for (String id : trees.keySet())
            if (id.startsWith("minecraft:"))
                vanilla.add(id);
        if (!vanilla.isEmpty())
            helper.fail("the battlefield biome still places vanilla trees " + vanilla + " (all its trees: " + trees.keySet() + ")");
        else if (trees.isEmpty())
            helper.fail("the battlefield biome places no tree at all");
        else
            helper.succeed();
    }

    /** R1-R3 on every tree feature the battlefield biome places (the skirmish world lovish plays). */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 200)
    public static void battlefield_trees_show_a_trunk_and_a_round_crown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<String, Holder<PlacedFeature>> trees = battlefieldTrees(level);
        if (trees == null || trees.isEmpty()) {
            helper.fail("the battlefield biome places no tree feature to measure: " + trees);
            return;
        }
        Map<String, Holder<ConfiguredFeature<?, ?>>> features = new LinkedHashMap<>();
        trees.forEach((id, placed) -> features.put(id, placed.value().feature()));
        measureAll(helper, features, BIOME_PAD_Y);
    }

    /** R1-R3 on the tree every pre-made map with trees grows (MapBuilder.decorate). */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 200)
    public static void map_trees_show_a_trunk_and_a_round_crown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        Map<String, Holder<ConfiguredFeature<?, ?>>> features = new LinkedHashMap<>();
        for (MapPreset p : MapPreset.values()) {
            ResourceKey<ConfiguredFeature<?, ?>> key = MapBuilder.paletteFor(p).tree();
            if (key == null)
                continue;
            var holder = registry.getHolder(key);
            if (holder.isEmpty()) {
                helper.fail(p + "'s tree " + key.location() + " is not in the registry");
                return;
            }
            features.putIfAbsent(key.location().toString(), holder.get());
        }
        if (features.isEmpty()) {
            helper.fail("no pre-made map grows a tree");
            return;
        }
        measureAll(helper, features, MAP_PAD_Y);
    }

    // ------------------------------------------------------------------ helpers

    /** The battlefield biome's tree features by id ("tree" in the id), or null without the biome. */
    static Map<String, Holder<PlacedFeature>> battlefieldTrees(ServerLevel level) {
        Biome biome = level.registryAccess().registryOrThrow(Registries.BIOME).get(BATTLEFIELD);
        if (biome == null)
            return null;
        Map<String, Holder<PlacedFeature>> out = new LinkedHashMap<>();
        List<HolderSet<PlacedFeature>> steps = biome.getGenerationSettings().features();
        for (HolderSet<PlacedFeature> step : steps)
            for (Holder<PlacedFeature> h : step) {
                String id = h.unwrapKey().map(k -> k.location().toString()).orElse("inline");
                if (id.contains("tree"))
                    out.put(id, h);
            }
        return out;
    }

    static void measureAll(GameTestHelper helper, Map<String, Holder<ConfiguredFeature<?, ?>>> features, int padY) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8)).atY(padY);
        String owner = "gametest_treeshape_" + centre.asLong();
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        List<String> faults = new ArrayList<>();
        StringBuilder tally = new StringBuilder();
        try {
            for (var e : features.entrySet()) {
                int grown = 0, bareSum = 0;
                for (int i = 0; i < SAMPLES; i++) {
                    pad(level, centre);
                    boolean ok = e.getValue().value().place(level, level.getChunkSource().getGenerator(),
                        RandomSource.create(9_000L + i * 7919L), centre);
                    Measure m = measure(level, centre);
                    if (!ok || m.logs == 0) {
                        faults.add(e.getKey() + " #" + i + " did not grow");
                        continue;
                    }
                    grown++;
                    bareSum += m.bare;
                    String id = e.getKey() + " #" + i + " (" + m + ")";
                    if (m.leaves == 0) {
                        faults.add(id + ": no leaves");
                        continue;
                    }
                    if (m.bare < MIN_BARE)
                        faults.add(id + ": R1 bare trunk " + m.bare + " < " + MIN_BARE);
                    if (m.bare > MAX_BARE)
                        faults.add(id + ": R1 lowest leaf " + m.bare + " up > " + MAX_BARE);
                    if (m.fill > MAX_FOOTPRINT_FILL)
                        faults.add(id + ": R2 crown fills " + fmt(m.fill) + " of its box from above > " + MAX_FOOTPRINT_FILL + " (a leaf block)");
                    if (m.fill < MIN_FOOTPRINT_FILL)
                        faults.add(id + ": R2 crown fills " + fmt(m.fill) + " of its box from above < " + MIN_FOOTPRINT_FILL + " (scattered)");
                    if (m.unnaturalLeaves > 0)
                        faults.add(id + ": R3 " + m.unnaturalLeaves + " leaves persistent or at distance 7 (decay / never felled)");
                    String fell = fell(level, centre, owner, m);
                    if (fell != null)
                        faults.add(id + ": R3 " + fell);
                }
                double mean = grown == 0 ? 0 : bareSum / (double) grown;
                if (grown > 0 && mean < MIN_MEAN_BARE)
                    faults.add(e.getKey() + ": R1 mean bare trunk " + fmt(mean) + " < " + MIN_MEAN_BARE);
                tally.append(e.getKey()).append(" grew ").append(grown).append("/").append(SAMPLES)
                    .append(" mean bare ").append(fmt(mean)).append("; ");
            }
        } finally {
            ResourcesServerEvents.resourcesList.remove(pool);
            pad(level, centre);
        }
        ReignOfNether.LOGGER.info("[TreeShape] {} faults; {}", faults.size(), tally);
        if (!faults.isEmpty())
            helper.fail(faults.size() + " faults: " + String.join(" | ", faults.subList(0, Math.min(14, faults.size())))
                + " || " + tally);
        else
            helper.succeed();
    }

    /** A flat grass pad (dirt under it) with air above, centred on the tree's column; centre is the air above it. */
    static void pad(ServerLevel level, BlockPos centre) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int dx = -HALF; dx <= HALF; dx++)
            for (int dz = -HALF; dz <= HALF; dz++) {
                level.setBlock(p.set(centre.getX() + dx, centre.getY() - 3, centre.getZ() + dz), Blocks.DIRT.defaultBlockState(), flags);
                level.setBlock(p.set(centre.getX() + dx, centre.getY() - 2, centre.getZ() + dz), Blocks.DIRT.defaultBlockState(), flags);
                level.setBlock(p.set(centre.getX() + dx, centre.getY() - 1, centre.getZ() + dz), Blocks.GRASS_BLOCK.defaultBlockState(), flags);
                for (int dy = 0; dy < HEIGHT; dy++) {
                    p.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.getBlockState(p).isAir())
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
                }
            }
    }

    /** What one grown tree measures. */
    static final class Measure {
        int logs, leaves, bare, footprint, boxArea, unnaturalLeaves;
        double fill;

        @Override
        public String toString() {
            return logs + " logs, " + leaves + " leaves, bare " + bare + ", crown fill " + fmt(fill) + " of " + boxArea;
        }
    }

    static Measure measure(ServerLevel level, BlockPos centre) {
        Measure m = new Measure();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int lowestLeaf = Integer.MAX_VALUE;
        Set<Long> footprint = new HashSet<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int dx = -HALF; dx <= HALF; dx++)
            for (int dz = -HALF; dz <= HALF; dz++)
                for (int dy = 0; dy < HEIGHT; dy++) {
                    BlockState bs = level.getBlockState(p.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz));
                    if (bs.is(BlockTags.LOGS))
                        m.logs++;
                    else if (bs.is(BlockTags.LEAVES)) {
                        m.leaves++;
                        lowestLeaf = Math.min(lowestLeaf, dy);
                        footprint.add(((long) dx << 32) ^ (dz & 0xffffffffL));
                        minX = Math.min(minX, dx);
                        maxX = Math.max(maxX, dx);
                        minZ = Math.min(minZ, dz);
                        maxZ = Math.max(maxZ, dz);
                        if (bs.getBlock() instanceof LeavesBlock && (bs.getValue(LeavesBlock.PERSISTENT)
                                || bs.getValue(LeavesBlock.DISTANCE) >= 7))
                            m.unnaturalLeaves++;
                    }
                }
        if (m.leaves > 0) {
            m.bare = lowestLeaf;   // centre is the first air block: leaves at dy = n stand on n bare blocks of trunk
            m.footprint = footprint.size();
            m.boxArea = (maxX - minX + 1) * (maxZ - minZ + 1);
            m.fill = m.footprint / (double) m.boxArea;
        }
        return m;
    }

    /** Fells what TreeClearing finds on the pad; null when it took every log as one tree and left only ground plants. */
    static String fell(ServerLevel level, BlockPos centre, String owner, Measure m) {
        TreeClearing.Scan scan = TreeClearing.scan(level, centre.getX() - HALF, centre.getZ() - HALF,
            centre.getX() + HALF, centre.getZ() + HALF, centre.getY() - 1, centre.getY() + 2);
        if (scan.trees.size() != 1)
            return "TreeClearing sees " + scan.trees.size() + " trees, not 1";
        if (scan.logs != m.logs)
            return "TreeClearing's tree holds " + scan.logs + " of the " + m.logs + " logs grown";
        var job = TreeClearing.fellTree(level, owner, scan.trees.get(0).base);
        if (job == null)
            return "TreeClearing will not fell it";
        job.finish();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -HALF; dx <= HALF; dx++)
            for (int dz = -HALF; dz <= HALF; dz++)
                for (int dy = 0; dy < HEIGHT; dy++) {
                    BlockState bs = level.getBlockState(p.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz));
                    if (!bs.isAir() && !(dy == 0 && bs.canBeReplaced()))
                        return "after felling " + bs.getBlock() + " still stands at +" + dx + "," + dy + "," + dz;
                }
        return null;
    }

    static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }
}
