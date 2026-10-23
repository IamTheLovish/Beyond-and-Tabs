package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.CanopySilhouettes;
import com.solegendary.reignofnether.orthoview.LeafFade;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.perf.FrameParts;
import com.solegendary.reignofnether.perf.SectionRebuilds;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FrameTimer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 19_canopy (Oct 22, lovish: trees stay solid, units show through them): a 3 x 3 grove of oaks planted on a pad beside
 * our base, a squad of ours under it, enemy workers under its far side and two of ours in the open, photographed:
 * <ul>
 *   <li>19_canopy_trees - the grove alone before any unit arrives, leaves solid;</li>
 *   <li>19_canopy_silhouettes - leaves solid (the default), unit silhouettes on (the default);</li>
 *   <li>19_canopy_plain - the same frame with silhouettes off: the A/B of what the feature adds;</li>
 *   <li>19_canopy_close - zoomed in, silhouettes on;</li>
 *   <li>19_canopy_fade - the FADE leaf mode, three of ours under one oak selected (each gets a soft hole);</li>
 *   <li>19_canopy_all - the ALL mode, every leaf hidden.</li>
 * </ul>
 * Between the first stills, ~50 more of ours fill the grove and the silhouettes' cost is measured ABAB (on, off, off,
 * on: four 4-second slices): our canopy frame part (CanopySilhouettes + LeafFade, ms per frame) and the frame times
 * (p50 / p95), as "[Eyes] 19_canopy cost". Wall-clock timeline, like scenes 16 and 17 (the shader lane's client runs a
 * few ticks a second).
 */
final class EyesCanopyScene {
    private EyesCanopyScene() { }

    private static final int ZOOM = 30, CLOSE_ZOOM = 14;
    private static final int OUT = 40;            // the grove's centre: this far from the capitol toward the map's middle
    private static final int HALF = 15;           // the pad is 31 x 31
    private static final int SPACING = 8;         // between the oaks of the 3 x 3 grove
    private static final int CROWD = 48;          // extra units for the cost A/B
    private static final double SLICE_S = 8;   // the shader lane draws ~1 frame a second on CI

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static BlockPos site;                 // the pad's centre, the first free block above the grass
    private static volatile boolean padReady = false;
    private static volatile String serverNote = null;
    private static long lastRebuilds = -1, quietSince = 0;

    private static int slice = 0;
    private static long sliceNanos = 0, sliceCanopyNs0 = 0, sliceBodiesNs0 = 0, sliceFrames0 = 0;
    private static long sliceDrawNs0 = 0, sliceCompositeNs0 = 0;
    private static long sliceTickNs0 = 0, sliceModelNs0 = 0, sliceCapsuleNs0 = 0, sliceTargetNs0 = 0, sliceVerts0 = 0;
    // the cost A/B, mirrored (ABCDE EDCBA) so a drift over the run cancels: off, the whole feature, and its parts alone -
    // the cover rays only (nothing drawn), capsules only (no model shapes), the target's bind / clear / composite only
    private static final CanopySilhouettes.EyesVariant FULL = CanopySilhouettes.EyesVariant.FULL;
    private static final CanopySilhouettes.EyesVariant[] SLICE_VARIANT = { null, FULL,
            CanopySilhouettes.EyesVariant.RAYS_ONLY, CanopySilhouettes.EyesVariant.CAPSULES_ONLY,
            CanopySilhouettes.EyesVariant.TARGET_ONLY, CanopySilhouettes.EyesVariant.TARGET_ONLY,
            CanopySilhouettes.EyesVariant.CAPSULES_ONLY, CanopySilhouettes.EyesVariant.RAYS_ONLY, FULL, null };
    private static final boolean[] SLICE_ON = new boolean[SLICE_VARIANT.length];
    static {
        for (int i = 0; i < SLICE_VARIANT.length; i++)
            SLICE_ON[i] = SLICE_VARIANT[i] != null;
    }
    private static final List<List<Double>> sliceFrames = new ArrayList<>();
    private static final List<Double> frames = new ArrayList<>();
    private static int frameCursor = -1;
    private static final double[][] results = new double[SLICE_ON.length][];

    private static double seconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    private static void next(int p) {
        phase = p;
        phaseNanos = System.nanoTime();
    }

    /** One client tick of the scene; true when it is over (the caller ends the tour). */
    static boolean tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 19_canopy: no level, player or integrated server - skipped");
            return true;
        }
        switch (phase) {
            case 0 -> setUp(mc);
            case 1 -> {   // the grove planted and meshed, the units in
                if ((!padReady || seconds() < 10) && seconds() < 40)
                    return false;
                if (!padReady) {
                    ReignOfNether.LOGGER.warn("[Eyes] 19_canopy: gave up - the pad was not planted ({})", serverNote);
                    return true;
                }
                // the grove alone, leaves solid (the default since #164): trees must read as trees, not glass
                EyesDirector.shot(mc, "19_canopy_trees");
                summonSquads(mc);
                next(2);
            }
            case 2 -> {
                if (seconds() < 8 || !quiet(2.0) && seconds() < 25)
                    return false;
                List<LivingEntity> units = unitsOnPad(mc);
                UnitClientEvents.sendUnitCommandManual(UnitAction.HOLD, ids(units, true));
                long covered = units.stream().filter(CanopySilhouettes::isUnderCover).count();
                ReignOfNether.LOGGER.info("[Eyes] 19_canopy: {} units on the pad, {} under cover; renderer {}, shader pack: {}",
                        units.size(), covered, SectionRebuilds.renderer(),
                        com.solegendary.reignofnether.postfx.ShaderPacks.inUse());
                EyesDirector.shot(mc, "19_canopy_silhouettes");
                ReignOfNetherClientConfigs.UNIT_SILHOUETTES.set(false);
                next(3);
            }
            case 3 -> {
                if (seconds() < 2)
                    return false;
                EyesDirector.shot(mc, "19_canopy_plain");
                ReignOfNetherClientConfigs.UNIT_SILHOUETTES.set(true);
                summonCrowd(mc);
                next(4);
            }
            case 4 -> {   // the crowd walks in: then the cost slices
                if (seconds() < 10)
                    return false;
                UnitClientEvents.sendUnitCommandManual(UnitAction.HOLD, ids(unitsOnPad(mc), true));
                startSlice(mc, 0);
                next(5);
            }
            case 5 -> {
                sampleFrames(mc);
                if ((System.nanoTime() - sliceNanos) / 1e9 < SLICE_S)
                    return false;
                endSlice(mc);
                if (slice + 1 < SLICE_ON.length) {
                    startSlice(mc, slice + 1);
                    return false;
                }
                summary();
                CanopySilhouettes.eyesVariant = FULL;
                ReignOfNetherClientConfigs.UNIT_SILHOUETTES.set(true);
                EyesDirector.setZoom(CLOSE_ZOOM);
                OrthoviewClientEvents.centreCameraOnPosInstant(Vec3.atBottomCenterOf(site));
                next(6);
            }
            case 6 -> {
                if (seconds() < 4)
                    return false;
                EyesDirector.shot(mc, "19_canopy_close");
                ReignOfNether.LOGGER.info("[Eyes] 19_canopy close: {} capsules + {} model silhouettes, {} px per block",
                        CanopySilhouettes.lastCapsules, CanopySilhouettes.lastModels,
                        String.format("%.1f", CanopySilhouettes.pxPerBlock));
                EyesDirector.setZoom(ZOOM);
                OrthoviewClientEvents.centreCameraOnPosInstant(Vec3.atBottomCenterOf(site));
                // the fade: three of ours under the middle oak selected, each gets its own soft hole
                UnitClientEvents.clearSelectedUnits();
                int picked = 0;
                for (LivingEntity le : unitsOnPad(mc))
                    if (picked < 3 && UnitClientEvents.getPlayerToEntityRelationship(le) == Relationship.OWNED
                            && le.distanceToSqr(Vec3.atBottomCenterOf(site)) < 4.5 * 4.5) {
                        UnitClientEvents.addSelectedUnit(le);
                        picked++;
                    }
                // and the cursor over the grove's left side: a soft hole of its own (the window has no real mouse in CI)
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mc.getWindow().getScreenWidth() * 0.32,
                        mc.getWindow().getScreenHeight() * 0.5);
                setLeafMode(OrthoviewClientEvents.LeafHideMethod.FADE);
                ReignOfNether.LOGGER.info("[Eyes] 19_canopy: fade mode, {} selected under the middle oak", picked);
                next(7);
            }
            case 7 -> {
                if (seconds() < 5 || !quiet(1.5) && seconds() < 20)
                    return false;
                EyesDirector.shot(mc, "19_canopy_fade");
                setLeafMode(OrthoviewClientEvents.LeafHideMethod.ALL);
                next(8);
            }
            case 8 -> {
                if (seconds() < 4 || !quiet(1.5) && seconds() < 20)
                    return false;
                EyesDirector.shot(mc, "19_canopy_all");
                setLeafMode(OrthoviewClientEvents.LeafHideMethod.NONE);
                UnitClientEvents.clearSelectedUnits();
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    private static void setLeafMode(OrthoviewClientEvents.LeafHideMethod m) {
        OrthoviewClientEvents.hideLeavesMethod = m;   // what the HUD leaves button does
        LeafFade.syncMode();
        FogOfWarClientEvents.resetFogChunks();
        lastRebuilds = -1;
    }

    /** No section rebuilt for the last {@code s} seconds. */
    private static boolean quiet(double s) {
        long rebuilds = SectionRebuilds.built() + SectionRebuilds.empty();
        if (rebuilds != lastRebuilds) {
            lastRebuilds = rebuilds;
            quietSince = System.nanoTime();
        }
        return (System.nanoTime() - quietSince) / 1e9 > s;
    }

    private static void setUp(Minecraft mc) {
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        mc.options.hideGui = true;
        BlockPos home = EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre : EyesDirector.findCapitol(mc);
        double dirX = 1, dirZ = 0;
        double len = Math.sqrt((double) home.getX() * home.getX() + (double) home.getZ() * home.getZ());
        if (len > 32) {   // toward the map's middle, like 17_fog
            dirX = -home.getX() / len;
            dirZ = -home.getZ() / len;
        }
        int x = (int) Math.floor(home.getX() + dirX * OUT), z = (int) Math.floor(home.getZ() + dirZ * OUT);
        Vec3 g = EyesDirector.ground(mc, x, z, home.getY());
        site = BlockPos.containing(g);
        OrthoviewClientEvents.hideLeavesMethod = OrthoviewClientEvents.LeafHideMethod.NONE;
        LeafFade.syncMode();
        ReignOfNetherClientConfigs.UNIT_SILHOUETTES.set(true);
        plant(mc);
        EyesDirector.setZoom(ZOOM);
        OrthoviewClientEvents.centreCameraOnPosInstant(Vec3.atBottomCenterOf(site));
        ReignOfNether.LOGGER.info("[Eyes] 19_canopy: planting a 3 x 3 grove at {}", site.toShortString());
        next(1);
    }

    /** On the server thread: a flat grass pad, cleared to the sky, with nine oaks whose leaves never decay. */
    private static void plant(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        final BlockPos c = site;
        server.execute(() -> {
            try {
                var level = server.overworld();
                BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
                BlockState air = Blocks.AIR.defaultBlockState(), log = Blocks.OAK_LOG.defaultBlockState();
                BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                int gy = c.getY();
                for (int dx = -HALF; dx <= HALF; dx++)
                    for (int dz = -HALF; dz <= HALF; dz++) {
                        level.setBlock(new BlockPos(c.getX() + dx, gy - 1, c.getZ() + dz), grass, 2);
                        for (int y = gy - 4; y < gy - 1; y++)
                            level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), dirt, 2);
                        for (int y = gy; y <= gy + 24; y++)
                            level.setBlock(new BlockPos(c.getX() + dx, y, c.getZ() + dz), air, 2);
                    }
                int trees = 0;
                for (int i = -1; i <= 1; i++)
                    for (int j = -1; j <= 1; j++) {
                        int tx = c.getX() + i * SPACING, tz = c.getZ() + j * SPACING;
                        // a broad crown from 3 to 7 blocks up, a little ragged at the rim (no perfect spheres)
                        for (int dx = -4; dx <= 4; dx++)
                            for (int dz = -4; dz <= 4; dz++)
                                for (int dy = 3; dy <= 7; dy++) {
                                    double r = Math.sqrt(dx * dx + dz * dz + (dy - 5) * (dy - 5) * 1.8);
                                    boolean ragged = ((dx * 31 + dz * 17 + dy * 7 + i * 5 + j * 3) & 7) == 0;
                                    if (r <= 4.2 && !(r > 3.4 && ragged))
                                        level.setBlock(new BlockPos(tx + dx, gy + dy, tz + dz), leaves, 2);
                                }
                        for (int dy = 0; dy <= 5; dy++)
                            level.setBlock(new BlockPos(tx, gy + dy, tz), log, 2);
                        trees++;
                    }
                serverNote = trees + " oaks on a " + (2 * HALF + 1) + "-wide pad at y " + gy;
                padReady = true;
            } catch (Exception e) {
                serverNote = "planting failed: " + e;
                ReignOfNether.LOGGER.error("[Eyes] 19_canopy: planting failed on the server", e);
            }
        });
    }

    /** Ours round the middle oaks, enemy workers under the far row, two of ours out in the open. */
    private static void summonSquads(Minecraft mc) {
        String me = mc.player.getName().getString().replace("\"", "");
        int[][] ours = { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 }, { -2, 0 }, { 2, 0 }, { 0, -2 }, { 0, 2 },
                { -SPACING + 2, 1 }, { SPACING - 2, -1 } };
        for (int[] o : ours)
            summon(mc, me, "militia_unit", o[0], o[1]);
        for (int k = -1; k <= 1; k++)   // under the far row: red silhouettes (workers: no fight breaks out)
            summon(mc, "Enemy", "villager_unit", k * SPACING + 1, SPACING + 2);
        summon(mc, me, "militia_unit", -HALF + 2, -HALF + 2);   // in the open: no silhouette expected
        summon(mc, me, "militia_unit", HALF - 2, -HALF + 2);
    }

    /** The cost A/B's crowd: four dozen more of ours spread through the grove. */
    private static void summonCrowd(Minecraft mc) {
        String me = mc.player.getName().getString().replace("\"", "");
        int n = 0;
        for (int dx = -11; dx <= 11 && n < CROWD; dx += 3)
            for (int dz = -11; dz <= 11 && n < CROWD; dz += 3) {
                if (Math.floorMod(dx, SPACING) == 0 && Math.floorMod(dz, SPACING) == 0)
                    continue;   // a trunk
                summon(mc, me, "militia_unit", dx, dz);
                n++;
            }
        ReignOfNether.LOGGER.info("[Eyes] 19_canopy: {} more of ours summoned for the cost A/B", n);
    }

    private static void summon(Minecraft mc, String owner, String unit, int dx, int dz) {
        EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:%s %d %d %d", owner, ReignOfNether.MOD_ID,
                unit, site.getX() + dx, site.getY(), site.getZ() + dz));
    }

    private static List<LivingEntity> unitsOnPad(Minecraft mc) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : new ArrayList<>(UnitClientEvents.getAllUnits()))
            if (le instanceof Unit && le.isAlive() && Math.abs(le.getX() - site.getX() - 0.5) <= HALF + 1
                    && Math.abs(le.getZ() - site.getZ() - 0.5) <= HALF + 1)
                out.add(le);
        return out;
    }

    private static int[] ids(List<LivingEntity> units, boolean ownedOnly) {
        return units.stream()
                .filter(le -> !ownedOnly || UnitClientEvents.getPlayerToEntityRelationship(le) == Relationship.OWNED)
                .mapToInt(Entity::getId).toArray();
    }

    // ---- the cost A/B -----------------------------------------------------------------------------------------------

    private static void startSlice(Minecraft mc, int i) {
        slice = i;
        ReignOfNetherClientConfigs.UNIT_SILHOUETTES.set(SLICE_ON[i]);
        CanopySilhouettes.eyesVariant = SLICE_ON[i] ? SLICE_VARIANT[i] : FULL;
        sliceTickNs0 = CanopySilhouettes.tickNanos;
        sliceModelNs0 = CanopySilhouettes.modelDrawNanos;
        sliceCapsuleNs0 = CanopySilhouettes.capsuleDrawNanos;
        sliceTargetNs0 = CanopySilhouettes.targetNanos;
        sliceVerts0 = CanopySilhouettes.modelVertices;
        sliceNanos = System.nanoTime();
        sliceCanopyNs0 = FrameParts.frameSectionLifetimeNs(FrameParts.CANOPY);
        sliceBodiesNs0 = FrameParts.frameSectionLifetimeNs(FrameParts.UNIT_BODIES);
        sliceDrawNs0 = CanopySilhouettes.drawNanos;
        sliceCompositeNs0 = CanopySilhouettes.compositeNanos;
        sliceFrames0 = FrameParts.framesLifetime();
        frames.clear();
        frameCursor = mc.getFrameTimer().getLogEnd();
    }

    private static void sampleFrames(Minecraft mc) {
        FrameTimer timer = mc.getFrameTimer();
        long[] log = timer.getLog();
        int end = timer.getLogEnd();
        for (int guard = 0; frameCursor != end && guard < log.length; guard++) {
            if (log[frameCursor] > 0)
                frames.add(log[frameCursor] / 1e6);
            frameCursor = timer.wrapIndex(frameCursor + 1);
        }
    }

    private static void endSlice(Minecraft mc) {
        long n = Math.max(1, FrameParts.framesLifetime() - sliceFrames0);
        double canopyMs = (FrameParts.frameSectionLifetimeNs(FrameParts.CANOPY) - sliceCanopyNs0) / 1e6 / n;
        // the capture copies vertices inside the units' own draw, so its share is in unitBodies: on minus off
        double bodiesMs = (FrameParts.frameSectionLifetimeNs(FrameParts.UNIT_BODIES) - sliceBodiesNs0) / 1e6 / n;
        double[] ms = frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        long covered = unitsOnPad(mc).stream().filter(CanopySilhouettes::isUnderCover).count();
        double drawMs = (CanopySilhouettes.drawNanos - sliceDrawNs0) / 1e6 / n;
        double compositeMs = (CanopySilhouettes.compositeNanos - sliceCompositeNs0) / 1e6 / n;
        results[slice] = new double[] { canopyMs, pct(ms, 0.5), pct(ms, 0.95), bodiesMs, covered };
        while (sliceFrames.size() <= slice)
            sliceFrames.add(null);
        sliceFrames.set(slice, new ArrayList<>(frames));
        double tickMs = (CanopySilhouettes.tickNanos - sliceTickNs0) / 1e6 / n;   // cover rays, per FRAME
        double modelMs = (CanopySilhouettes.modelDrawNanos - sliceModelNs0) / 1e6 / n;
        double capsuleMs = (CanopySilhouettes.capsuleDrawNanos - sliceCapsuleNs0) / 1e6 / n;
        double targetMs = (CanopySilhouettes.targetNanos - sliceTargetNs0) / 1e6 / n;
        long verts = (CanopySilhouettes.modelVertices - sliceVerts0) / n;
        String what = SLICE_ON[slice] ? SLICE_VARIANT[slice].name().toLowerCase() : "off";
        ReignOfNether.LOGGER.info("[Eyes] 19_canopy slice {}/{} (silhouettes {}): canopy {} ms/frame (draw {}: target {}, models {}, "
                        + "capsules {}; composite {}), rays {} ms/frame, unitBodies {} ms/frame, model vertices {}/frame, frames {}: "
                        + "p50 {} ms, p95 {} ms; {} units under cover, last frame {} capsules + {} models",
                slice + 1, SLICE_ON.length, what, f2(canopyMs), f2(drawMs), f2(targetMs), f2(modelMs), f2(capsuleMs),
                f2(compositeMs), f2(tickMs), f2(bodiesMs), verts, ms.length, f2(pct(ms, 0.5)), f2(pct(ms, 0.95)), covered,
                CanopySilhouettes.lastCapsules, CanopySilhouettes.lastModels);
    }

    private static void summary() {
        double[] on = new double[4], off = new double[4];
        int nOn = 0, nOff = 0;
        double covered = 0;
        for (int i = 0; i < SLICE_ON.length; i++) {
            double[] r = results[i];
            if (r == null)
                return;
            if (SLICE_ON[i] && SLICE_VARIANT[i] != FULL)
                continue;   // a part alone: in the variants line below
            double[] t = SLICE_ON[i] ? on : off;
            for (int k = 0; k < 4; k++)
                t[k] += r[k];
            if (SLICE_ON[i]) nOn++; else nOff++;
            covered = Math.max(covered, r[4]);
        }
        for (int k = 0; k < 4; k++) {
            on[k] /= Math.max(1, nOn);
            off[k] /= Math.max(1, nOff);
        }
        ReignOfNether.LOGGER.info("[Eyes] 19_canopy cost ({} units under cover, {} px per block, {}{}): silhouettes on: canopy {} ms/frame, "
                        + "unitBodies {}, frame p50 {} / p95 {} ms | off: canopy {} ms/frame, unitBodies {}, frame p50 {} / p95 {} ms",
                (long) covered, String.format("%.1f", CanopySilhouettes.pxPerBlock), SectionRebuilds.renderer(),
                com.solegendary.reignofnether.postfx.ShaderPacks.inUse() ? ", shader pack" : "",
                f2(on[0]), f2(on[3]), f2(on[1]), f2(on[2]), f2(off[0]), f2(off[3]), f2(off[1]), f2(off[2]));
        // every frame of a kind pooled (the slices' own p50s are noisy with ~7 frames a slice in the shader lane)
        double[] offFrames = pooled(null);
        double offP50 = pct(offFrames, 0.5), offP95 = pct(offFrames, 0.95);
        StringBuilder sb = new StringBuilder();
        for (CanopySilhouettes.EyesVariant v : CanopySilhouettes.EyesVariant.values()) {
            double[] f = pooled(v);
            if (f.length == 0)
                continue;
            sb.append(String.format(" | %s: p50 %+.1f%% p95 %+.1f%% (%d frames)", v.name().toLowerCase(),
                    100 * (pct(f, 0.5) - offP50) / offP50, 100 * (pct(f, 0.95) - offP95) / offP95, f.length));
        }
        ReignOfNether.LOGGER.info("[Eyes] 19_canopy pooled vs off (p50 {} / p95 {} ms, {} frames){}", f2(offP50), f2(offP95),
                offFrames.length, sb);
    }

    /** All frames of the slices of one variant (null: the off slices), sorted. */
    private static double[] pooled(CanopySilhouettes.EyesVariant v) {
        List<Double> all = new ArrayList<>();
        for (int i = 0; i < SLICE_VARIANT.length && i < sliceFrames.size(); i++)
            if (SLICE_VARIANT[i] == v && sliceFrames.get(i) != null)
                all.addAll(sliceFrames.get(i));
        return all.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    }

    private static double pct(double[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.round((sorted.length - 1) * p)];
    }

    private static String f2(double v) {
        return String.format("%.2f", v);
    }
}
