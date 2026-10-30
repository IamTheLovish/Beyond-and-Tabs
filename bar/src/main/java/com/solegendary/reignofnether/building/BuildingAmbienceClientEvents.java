package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;
import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Ambient life for the base. Any building actively producing something shows it, in its faction's idiom:
 *   - the Kingdom: chimney smoke drifting from the roof
 *   - the Fallen: soul flames guttering at the eaves
 *   - the Gilded Legion: flame and rising embers
 * Smithy-type buildings (blacksmiths, forges, laboratories) also throw sparks while working.
 * Client-side particles only - nothing synced, nothing simulated - and none at all in strategic zoom.
 *
 * Idle life (design/lively_buildings.md, lovish: "simulate effects of the game coming to life"): every faction's
 * capitol, energy building and labs breathe even when idle, within IDLE_DIST of the camera - Sunforged gilded glints,
 * Gravebound soul flames, Horde chimney smoke and lava pops, Verdant falling petals and spores, Tidewrought splashes off
 * the waterwheel, Stormcallers sparks off the lightning-rod tip, Brassworks steam and firebox embers, and smoke off a
 * Geothermal Plant's vent. Energy buildings emit from their own spots (chimney, braziers, crown, wheel, pipe tops,
 * rotated with the placement, worked out once); capitols and labs from their roofline. Off while a building is under
 * construction, rate-limited (a pass every IDLE_PERIOD ticks, at most IDLE_BUDGET emitters a pass), and the whole
 * class follows the client option "Building ambience" (Display settings).
 *
 * Building animations, slice 1 (lovish, Oct 30: "smoke = working", medium; rules in {@link BuildingWork}): a lab
 * no longer breathes idle life, so smoke over a lab always means it is producing. A working lab smokes from its real
 * stacks - its open campfires - 2 puffs a stack a pass in a ~5-block column drifting with the pennants' wind (south-
 * east), with a 4-puff chuff when a unit leaves; a lab without a stack keeps its faction's roof-centre idiom. Only
 * while built, in sight and queued, within WORK_DIST of the render camera, at most WORK_BUDGET labs a pass. The
 * vanilla smoke of a lab's stack campfires is silenced ({@link #isQuietStack}, CampfireBlockEntityMixin): idle, a
 * lab is quiet.
 */
public class BuildingAmbienceClientEvents {

    static final Minecraft MC = Minecraft.getInstance();
    static final Random random = new Random();
    // particles past 64 blocks of the render camera are never made in the RTS view (LevelRendererMixin): don't spend
    // the budget on them
    private static final double WORK_DIST_SQR = 64 * 64;
    // the pennants' wind (PartRenderClientEvents.WIND_YAW): smoke leans south-east, broadside to the default camera
    private static final double DRIFT = 0.008;

    /** placement -> {centre, roof height, a lab's stacks}; recomputed if the building changes shape. */
    private record Shape(int blockCount, BlockPos centre, int height, List<BlockPos> stacks) { }
    private static final Map<BuildingPlacement, Shape> shapes = new WeakHashMap<>();

    private static Shape shapeOf(BuildingPlacement placement) {
        int count = placement.getBlocks().size();
        Shape s = shapes.get(placement);
        if (s != null && s.blockCount() == count)
            return s;
        int max = 0;
        boolean lab = isLab(placement);
        Set<BlockPos> solid = new HashSet<>();
        List<BlockPos> fires = new ArrayList<>();
        for (BuildingBlock bb : placement.getBlocks()) {
            max = Math.max(max, bb.getBlockPos().getY() - placement.originPos.getY());
            if (lab && !bb.getBlockState().isAir()) {
                solid.add(bb.getBlockPos());
                if (bb.getBlockState().getBlock() instanceof CampfireBlock)
                    fires.add(bb.getBlockPos());
            }
        }
        s = new Shape(count, BuildingUtils.getCentrePos(placement.getBlocks()), max,
            List.copyOf(BuildingWork.stackTops(fires, solid)));
        shapes.put(placement, s);
        return s;
    }

    private static boolean isLab(BuildingPlacement p) {
        return BuildingWork.isLab(p.isCapitol, p.getBuilding().maxLevel);
    }

    // ------------------------------------------------------------------ lab stacks: quiet unless working

    /** Every lab's stack cells (BlockPos.asLong): their vanilla campfire smoke is off. Client thread only. */
    private static final LongOpenHashSet quietStacks = new LongOpenHashSet();
    private static final int QUIET_REFRESH = 20;
    // client ticks: the working passes count these, not the level's game time, which this mod's client holds equal to the
    // day time (ClientLevelMixin.tickTime) - frozen while the day time is, or stepping in 20s (eyes run 41123019484)
    private static long ticks = 0;

    /** CampfireBlockEntityMixin: true when this campfire is a lab's stack, whose smoke is ours (working only). */
    public static boolean isQuietStack(BlockPos pos) {
        return !quietStacks.isEmpty() && quietStacks.contains(pos.asLong());
    }

    private static void refreshQuietStacks() {
        quietStacks.clear();
        for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
            if (placement.originPos == null || !isLab(placement))
                continue;
            for (BlockPos stack : shapeOf(placement).stacks())
                quietStacks.add(stack.asLong());
        }
    }

    private static boolean isSmithy(BuildingPlacement placement) {
        String n = placement.getBuilding().structureName;
        return n != null && (n.contains("blacksmith") || n.contains("forge") || n.contains("laboratory")
            || n.contains("fortress") || n.contains("bastion"));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.level == null) {
            quietStacks.clear();
            return;
        }
        if (MC.player == null || MC.isPaused())
            return;
        ticks++;
        if (ticks % QUIET_REFRESH == 1)
            refreshQuietStacks();
        // particles are invisible at strategic zoom; don't pay for them (zoom-out performance)
        if (StrategicViewClientEvents.isStrategicView() || !DisplaySettings.buildingAmbience())
            return;
        if (MC.level.getGameTime() % IDLE_PERIOD == 0)
            idleLife();
        if (ticks % BuildingWork.SMOKE_PERIOD != 0)
            return;

        // the RENDER camera: in the RTS view the player entity is elsewhere
        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        int budget = BuildingWork.WORK_BUDGET;
        for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
            if (!(placement instanceof ProductionPlacement pp) || placement.originPos == null)
                continue;
            // taken every pass, drawn or not, so a release never chuffs late
            int chuffs = BuildingWork.takeReleases(placement);
            if (budget <= 0 || !placement.isBuilt || !placement.isExploredClientside)
                continue;
            boolean queued = !pp.productionQueue.isEmpty();
            if (!queued && chuffs == 0)
                continue;
            Shape shape = shapeOf(placement);
            BlockPos centre = shape.centre();
            if (cam.distanceToSqr(centre.getX() + 0.5, centre.getY() + 0.5, centre.getZ() + 0.5) > WORK_DIST_SQR)
                continue;
            // a remembered building's queue is stale: nothing from the fog (the last unit's chuff counts, in sight)
            boolean inSight = FogOfWarClientEvents.isInBrightChunk(centre);
            if (!BuildingWork.isWorking(true, inSight, queued || chuffs > 0))
                continue;
            budget--;
            FactionTraits.Ambience idiom = FactionTraits.of(placement.getBuilding().getFaction()).ambience;

            if (!shape.stacks().isEmpty()) {
                int puffs = (queued ? BuildingWork.PUFFS_PER_STACK : 0) + (chuffs > 0 ? BuildingWork.CHUFF_PUFFS : 0);
                for (BlockPos stack : shape.stacks())
                    stackSmoke(stack, puffs, idiom);
            } else if (queued) {
                roofIdiom(placement, shape, idiom);
            }

            // working smithies throw sparks out of the doorway level
            if (queued && isSmithy(placement) && random.nextInt(2) == 0) {
                ParticleOptions spark = random.nextBoolean() ? ParticleTypes.CRIT : ParticleTypes.SMALL_FLAME;
                double sx = centre.getX() + 0.5 + (random.nextDouble() - 0.5) * 2.5;
                double sz = centre.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2.5;
                double sy = placement.originPos.getY() + 1.5;
                for (int i = 0; i < 3; i++)
                    MC.level.addParticle(spark, sx, sy, sz,
                        (random.nextDouble() - 0.5) * 0.2, 0.12 + random.nextDouble() * 0.1,
                        (random.nextDouble() - 0.5) * 0.2);
            }
        }
    }

    /** A working lab without a stack: its faction's idiom along the roof centre, as before. */
    private static void roofIdiom(BuildingPlacement placement, Shape shape, FactionTraits.Ambience idiom) {
        BlockPos centre = shape.centre();
        double x = centre.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
        double z = centre.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
        double y = placement.originPos.getY() + shape.height() + 0.4;
        switch (idiom) {
            case SOUL_FLAMES -> {
                // soul flames flicker around the eaves, a little lower than chimney height
                MC.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y - 0.6, z, 0, 0.02, 0);
                if (random.nextInt(3) == 0)
                    MC.level.addParticle(ParticleTypes.SOUL, x, y, z, 0, 0.03, 0);
            }
            case FORGE_FIRE -> {
                MC.level.addParticle(ParticleTypes.FLAME, x, y - 0.4, z, 0, 0.02, 0);
                if (random.nextInt(2) == 0)
                    MC.level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            }
            case CHIMNEY_SMOKE -> MC.level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z,
                0, 0.06 + random.nextDouble() * 0.03, 0);
            // Verdant: spores drifting off the living roof, the odd green glint
            case SPORES -> {
                MC.level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 0, 0.01, 0);
                if (random.nextInt(3) == 0)
                    MC.level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y - 0.3, z, 0, 0.02, 0);
            }
            default -> { }   // no faction entry: no faction-flavoured ambience
        }
    }

    /**
     * A working stack: a smoke column leaning with the wind (vanilla's long-lived signal-fire smoke, rising slowly, so the
     * column is dense; its drift halved to keep the lean at ~1 block over the column); a fire faction's stack flares.
     */
    private static void stackSmoke(BlockPos stack, int puffs, FactionTraits.Ambience idiom) {
        double x = stack.getX() + 0.5, y = stack.getY() + 0.7, z = stack.getZ() + 0.5;
        for (int i = 0; i < puffs; i++)
            MC.level.addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                x + (random.nextDouble() - 0.5) * 0.3, y + random.nextDouble() * 0.3, z + (random.nextDouble() - 0.5) * 0.3,
                DRIFT * 0.5, BuildingWork.PUFF_RISE + random.nextDouble() * 0.004, DRIFT * 0.5);
        if (idiom == FactionTraits.Ambience.FORGE_FIRE && random.nextInt(4) == 0)
            MC.level.addParticle(ParticleTypes.FLAME, x, y - 0.2, z, 0, 0.02, 0);
        else if (idiom == FactionTraits.Ambience.SOUL_FLAMES && random.nextInt(4) == 0)
            MC.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y - 0.2, z, 0, 0.02, 0);
    }

    // ------------------------------------------------------------------ idle life

    private static final int IDLE_PERIOD = 4;           // ticks between passes: ~5 puffs a second per building
    private static final double IDLE_DIST = 48;         // blocks from the render camera
    private static final int IDLE_BUDGET = 40;          // emitters a pass, whatever the base size (8v8 safety)

    /** What an emitter puts out. */
    private enum Fx { GLINT, SOUL, SMOKE, PETAL, SPLASH, SPARK, STEAM, EMBER }

    /** An energy building's emitter spots in structure coordinates (x, y, z triples; the block at (bx, by, bz) spans
     *  bx..bx+1), and what each puts out - matching tools/gen_structures.py's buildings. */
    private record Spots(double[] xyz, Fx[] fx) { }
    private static final Map<String, Spots> ENERGY_SPOTS = Map.of(
        "", new Spots(new double[] { 1.5, 11.7, 2.5 }, new Fx[] { Fx.GLINT }),                          // the angel's lantern
        "_dark", new Spots(new double[] { 0.5, 12.0, 0.5, 2.5, 12.0, 2.5, 1.5, 16.0, 1.5 },
            new Fx[] { Fx.SOUL, Fx.SOUL, Fx.SOUL }),                                                     // spirelet lanterns, needle lantern
        "_nether", new Spots(new double[] { 2.5, 11.4, 1.5, 0.0, 2.5, 1.5 }, new Fx[] { Fx.SMOKE, Fx.EMBER }),   // crater fire bowl, west-face crack
        "_verdant", new Spots(new double[] { 1.5, 11.0, 1.5 }, new Fx[] { Fx.PETAL }),                   // under the blossom crown
        "_tide", new Spots(new double[] { -0.3, 1.2, 1.5 }, new Fx[] { Fx.SPLASH }),                     // under the waterwheel
        "_storm", new Spots(new double[] { 1.5, 14.0, 1.5 }, new Fx[] { Fx.SPARK }),                     // the lightning-rod tip
        "_brass", new Spots(new double[] { 0.5, 9.1, 0.5, 1.5, 7.6, 1.5, 2.5, 9.7, 1.5, 1.5, 1.5, 2.9 },
            new Fx[] { Fx.STEAM, Fx.STEAM, Fx.STEAM, Fx.EMBER })                                         // vent top, pressure dome, pipe-to-stack joint, firebox
    );
    private static final Spots GEOTHERMAL_SPOTS = new Spots(new double[] { 2.5, 8.2, 2.5, 1.5, 6.0, 1.5, 3.5, 6.0, 3.5 },
        new Fx[] { Fx.SMOKE, Fx.STEAM, Fx.STEAM });
    /** The capitols' own spots: the Temple of the Sun's gold crown and the gold bosses over its south and west
     *  porches (tools/capitol_town_centre.py, Oct 19), the Necropolis Cathedral's four tower-spire soul lanterns and its
     *  crown (tools/capitol_mausoleum.py, Oct 19), the Central Portal's four braziers (tools/gen_capitol_details.py), the
     *  Grand Foundry's pressure dome and four south / west blast furnaces (tools/capitol_foundry_hall.py), the Admiral's
     *  Beacon's lighthouse vane and dock (tools/capitol_wreck_harbour.py), the Pagoda of a Thousand Storms' crown rod
     *  and corner crystals (tools/capitol_jade_pagoda.py), the World-Tree Court's falling petals
     *  (tools/capitol_heartwood_hall.py, Oct 19). Others breathe from their roofline. */
    private static final Map<String, Spots> CAPITOL_SPOTS = Map.of(
        "town_centre", new Spots(new double[] { 7.5, 20.1, 7.5, 7.5, 14.5, 14.1, 0.9, 14.5, 7.5 },
            new Fx[] { Fx.GLINT, Fx.GLINT, Fx.GLINT }),
        "mausoleum", new Spots(new double[] { 4.5, 16.7, 4.5, 10.5, 16.7, 4.5, 4.5, 16.7, 10.5, 10.5, 16.7, 10.5, 7.5, 21.7, 7.5 },
            new Fx[] { Fx.SOUL, Fx.SOUL, Fx.SOUL, Fx.SOUL, Fx.SOUL }),
        "central_portal", new Spots(new double[] { 1.5, 3.7, 2.5, 9.5, 3.7, 2.5, 1.5, 3.7, 6.5, 9.5, 3.7, 6.5 },
            new Fx[] { Fx.EMBER, Fx.EMBER, Fx.EMBER, Fx.EMBER }),
        "foundry_hall", new Spots(new double[] { 2.5, 13.0, 12.5, 4.5, 2.6, 12.2, 10.5, 2.6, 12.2, 2.8, 2.6, 4.5, 2.8, 2.6, 10.5 },
            new Fx[] { Fx.STEAM, Fx.EMBER, Fx.EMBER, Fx.EMBER, Fx.EMBER }),
        "wreck_harbour", new Spots(new double[] { 7.5, 22.1, 2.5, 2.0, 1.15, 6.5, 2.0, 1.15, 9.5 },
            new Fx[] { Fx.GLINT, Fx.SPLASH, Fx.SPLASH }),
        "jade_pagoda", new Spots(new double[] { 7.5, 21.9, 7.5, 4.5, 21.5, 4.5, 10.5, 21.5, 4.5, 4.5, 21.5, 10.5, 10.5, 21.5, 10.5 },
            new Fx[] { Fx.SPARK, Fx.SPARK, Fx.SPARK, Fx.SPARK, Fx.SPARK }),
        "heartwood_hall", new Spots(new double[] { 2.5, 11.5, 4.5, 12.5, 11.5, 10.5, 4.5, 11.5, 12.5, 10.5, 11.5, 2.5, 7.5, 21.5, 7.5 },
            new Fx[] { Fx.PETAL, Fx.PETAL, Fx.PETAL, Fx.PETAL, Fx.PETAL }));

    /** placement -> its emitter spots in world space (x, y, z triples), or EMPTY when it has none. */
    private static final Map<BuildingPlacement, double[]> worldSpots = new WeakHashMap<>();
    private static final double[] EMPTY = new double[0];

    /** Structure (x, z) rotated as BlockPos.rotate rotates blocks: a continuous point also shifts by a block's width. */
    private static double[] spotsInWorld(BuildingPlacement p, Spots spots) {
        double[] out = new double[spots.xyz().length];
        Rotation rot = p.rotation == null ? Rotation.NONE : p.rotation;
        for (int i = 0; i < out.length; i += 3) {
            double x = spots.xyz()[i], y = spots.xyz()[i + 1], z = spots.xyz()[i + 2];
            double rx, rz;
            switch (rot) {
                case CLOCKWISE_90 -> { rx = 1 - z; rz = x; }
                case CLOCKWISE_180 -> { rx = 1 - x; rz = 1 - z; }
                case COUNTERCLOCKWISE_90 -> { rx = z; rz = 1 - x; }
                default -> { rx = x; rz = z; }
            }
            out[i] = p.originPos.getX() + rx;
            out[i + 1] = p.originPos.getY() + 1 + y;
            out[i + 2] = p.originPos.getZ() + rz;
        }
        return out;
    }

    private static Spots spotDefOf(BuildingPlacement p) {
        if (p.getBuilding() instanceof WindGenerator mill)
            return ENERGY_SPOTS.get(mill.variant);
        if (p.getBuilding() instanceof GeothermalPlant)
            return GEOTHERMAL_SPOTS;
        if (p.isCapitol && p.currentStructureName != null)
            return CAPITOL_SPOTS.get(p.currentStructureName);
        return null;
    }

    private static double[] spotsOf(BuildingPlacement p) {
        double[] w = worldSpots.get(p);
        if (w != null)
            return w;
        Spots spots = spotDefOf(p);
        w = spots == null || p.originPos == null ? EMPTY : spotsInWorld(p, spots);
        worldSpots.put(p, w);
        return w;
    }

    /** Capitols and energy buildings breathe; labs don't (smoke on a lab is work: BuildingWork); the rest stay quiet. */
    private static boolean isLively(BuildingPlacement p) {
        Building b = p.getBuilding();
        return BuildingWork.breathesIdle(p.isCapitol, b instanceof WindGenerator || b instanceof GeothermalPlant, b.maxLevel);
    }

    private static void idleLife() {
        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        double maxD2 = IDLE_DIST * IDLE_DIST;
        int budget = IDLE_BUDGET;
        for (BuildingPlacement placement : BuildingClientEvents.getBuildings()) {
            if (budget <= 0)
                break;
            // off while under construction (and in the fog: nobody sees it)
            if (!placement.isBuilt || !placement.isExploredClientside || placement.originPos == null || !isLively(placement))
                continue;
            BlockPos c = placement.centrePos;
            if (cam.distanceToSqr(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5) > maxD2)
                continue;
            budget--;
            double[] spots = spotsOf(placement);
            if (spots.length > 0) {
                // an energy building: one of its own spots, in its own idiom
                Spots def = spotDefOf(placement);
                int k = random.nextInt(spots.length / 3);
                emit(def.fx()[k], spots[k * 3], spots[k * 3 + 1], spots[k * 3 + 2]);
                continue;
            }
            // a capitol: somewhere along its roofline, in its faction's idiom, half as often (they're bigger)
            Fx fx = roofFx(FactionTraits.of(placement.getBuilding().getFaction()));
            if (fx == null || random.nextInt(2) != 0)
                continue;
            Shape shape = shapeOf(placement);
            double x = c.getX() + 0.5 + (random.nextDouble() - 0.5) * 3.0;
            double z = c.getZ() + 0.5 + (random.nextDouble() - 0.5) * 3.0;
            emit(fx, x, placement.originPos.getY() + shape.height() + 1.2, z);
        }
    }

    private static Fx roofFx(FactionTraits t) {
        if (t == FactionTraits.SUNFORGED) return Fx.GLINT;
        if (t == FactionTraits.GRAVEBOUND) return Fx.SOUL;
        if (t == FactionTraits.HORDE) return Fx.SMOKE;
        if (t == FactionTraits.VERDANT) return Fx.PETAL;
        if (t == FactionTraits.TIDEWROUGHT) return Fx.SPLASH;
        if (t == FactionTraits.STORMCALLERS) return Fx.SPARK;
        if (t == FactionTraits.BRASSWORKS) return Fx.STEAM;
        return null;
    }

    private static void emit(Fx fx, double x, double y, double z) {
        double jx = (random.nextDouble() - 0.5) * 0.4, jz = (random.nextDouble() - 0.5) * 0.4;
        switch (fx) {
            // a gilded glint drifting up off the gold
            case GLINT -> MC.level.addParticle(ParticleTypes.END_ROD, x + jx, y, z + jz, 0, 0.012, 0);
            // a soul flame guttering, now and then a soul rising
            case SOUL -> {
                MC.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + jx, y, z + jz, 0, 0.015, 0);
                if (random.nextInt(4) == 0)
                    MC.level.addParticle(ParticleTypes.SOUL, x, y + 0.3, z, 0, 0.02, 0);
            }
            // a puff of chimney smoke, now and then a lava pop
            case SMOKE -> {
                MC.level.addParticle(ParticleTypes.LARGE_SMOKE, x + jx, y, z + jz, 0, 0.05, 0);
                if (random.nextInt(6) == 0)
                    MC.level.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
            }
            // a petal falling out of the crown, now and then a spore drifting
            case PETAL -> {
                MC.level.addParticle(ParticleTypes.CHERRY_LEAVES, x + jx * 4, y, z + jz * 4, 0, 0, 0);
                if (random.nextInt(3) == 0)
                    MC.level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x + jx * 3, y - 1, z + jz * 3, 0, 0.01, 0);
            }
            // spray off the waterwheel's paddles, drips falling back
            case SPLASH -> {
                MC.level.addParticle(ParticleTypes.SPLASH, x + jx, y, z + jz * 3, 0, 0.1, 0);
                if (random.nextInt(2) == 0)
                    MC.level.addParticle(ParticleTypes.FALLING_WATER, x + jx, y + 1.5, z + jz * 3, 0, 0, 0);
            }
            // a spark crackling off the lightning rod
            case SPARK -> MC.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + jx, y, z + jz, jx * 0.3, 0.05, jz * 0.3);
            // a puff of steam off a pipe
            case STEAM -> MC.level.addParticle(ParticleTypes.CLOUD, x + jx * 0.5, y, z + jz * 0.5, 0, 0.04, 0);
            // a firebox ember
            case EMBER -> MC.level.addParticle(random.nextBoolean() ? ParticleTypes.SMALL_FLAME : ParticleTypes.LAVA,
                x + jx, y, z + jz, 0, 0.02, 0);
        }
    }
}
