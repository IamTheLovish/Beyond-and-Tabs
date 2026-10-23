package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.perf.FrameParts;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FrameTimer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 18_overlays: what our per-frame overlays cost over a big match, and stills to check they look the same (Oct 21 perf
 * plan, R2 "per-frame loops over everything"). lovish's 3-player match ran 500-650 units and ~1,700 buildings at 33-55
 * fps, and strategic zoom 200 - no unit bodies drawn - still took 17-23 ms a frame against 8 ms empty: the underlines,
 * selection outlines, building boxes and strategic icons walked every unit and every building every frame.
 * <p>
 * The scene (eyes.yml input {@code overlays}: after 02, nothing else of the tour runs):
 * <ul>
 * <li>500 of our fighters in ten squads of 50 - one under the camera, nine on a ring round it - so the normal zoom sees
 *     a fight's worth of them and strategic zoom sees them all, a spread army as in a real match;</li>
 * <li>1,700 villager houses on a grid round them, half ours and half the bot's. They are CLIENT-ONLY placements, made by
 *     the call the server's placement packet makes (BuildingClientEvents.placeBuilding), marked built and whole: the
 *     overlay loops walk them like any building (underline, box, plate, fog test), without 1,700 construction sites on
 *     the server (cleared footprints, forced chunks, scaffolds, block queues) whose cost would swamp what is measured.
 *     Their blocks are not placed, so the terrain a frame draws is lighter than a real base's;</li>
 * <li>225 of the units selected (half the centre squad and four more squads): outlines and opaque underlines.</li>
 * </ul>
 * Stills at a mid zoom (45) with the units selected, then with one of our houses selected; and at strategic zoom. Then
 * the measurement: twelve 8-second slices over normal zoom (30), crowd zoom (60) and strategic zoom (200) ABCCBA-wise,
 * each after a 3-second settle. Crowd zoom is the band just under the fixed icon switch (70) where a late-game army drew
 * every model (lovish's Oct 23 log). Per slice: the frame times (vanilla's frame timer, p50 / p95 / max) and our handlers'
 * milliseconds per frame (FrameParts' lifetime counters: unitOverlays, buildingBoxes, strategicIcons, unitRings, minimap,
 * hud, unitBodies, buildingDecor, worldMarkers); the summary line gives each zoom's medians. The same scene on two builds
 * (eyes.yml build_run) is a before / after.
 * <p>
 * Wall-clock timeline, like scenes 16 and 17 (a shader-lane client runs a few ticks a second).
 */
final class EyesOverlaysScene {
    private EyesOverlaysScene() { }

    private static final int SQUADS = 10, PER_SQUAD = 50, SQUAD_COLS = 10, RING = 70;
    private static final int HOUSES = 1700;
    private static final String[] TYPES = { "militia_unit", "pillager_unit", "vindicator_unit" };
    private static final int ZOOM_NORMAL = 30, ZOOM_MID = 45, ZOOM_CROWD = 60, ZOOM_STRATEGIC = 200;
    private static final int[] ZOOMS = { ZOOM_NORMAL, ZOOM_CROWD, ZOOM_STRATEGIC };
    private static final int[] ORDER = { ZOOM_NORMAL, ZOOM_CROWD, ZOOM_STRATEGIC, ZOOM_STRATEGIC, ZOOM_CROWD, ZOOM_NORMAL,
            ZOOM_NORMAL, ZOOM_CROWD, ZOOM_STRATEGIC, ZOOM_STRATEGIC, ZOOM_CROWD, ZOOM_NORMAL };
    private static final double SETTLE_S = 3, SLICE_S = 8;
    private static final int[] SECTIONS = { FrameParts.UNIT_OVERLAYS, FrameParts.BUILDING_BOXES,
            FrameParts.STRATEGIC_ICONS, FrameParts.UNIT_RINGS, FrameParts.MINIMAP, FrameParts.HUD, FrameParts.UNIT_BODIES,
            FrameParts.BUILDING_DECOR, FrameParts.WORLD_MARKERS };
    private static final int SUMMONS_PER_TICK = 25, HOUSES_PER_TICK = 100;

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static String me, enemy;
    private static Vec3 centre;
    private static final List<Vec3> squadCentres = new ArrayList<>();
    private static final List<BlockPos> houseSites = new ArrayList<>();
    private static int summoned = 0, placed = 0;
    private static BlockPos ourHouse = null;   // the house the building still selects

    private static int slice = -1;
    private static boolean measuring = false;
    private static long sliceNanos = 0, sliceFrames0 = 0;
    private static final long[] sliceNs0 = new long[SECTIONS.length];
    private static final List<Double> frames = new ArrayList<>();
    private static int frameCursor = -1;
    // per slice: zoom asked, zoom reached, strategic (0/1), frames, p50, p95, max, then ms/frame per SECTIONS entry
    private static final List<double[]> results = new ArrayList<>();

    private static double seconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    private static void next(int p) {
        phase = p;
        phaseNanos = System.nanoTime();
    }

    /** One client tick of the scene; true when it is over (the caller ends the tour). */
    static boolean tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 18_overlays: no level or player - skipped");
            return true;
        }
        switch (phase) {
            case 0 -> setUp(mc);
            case 1 -> {   // the camera moved: wait for the ground under every squad (the server summons only on loaded ground)
                boolean loaded = true;
                for (int[] xz : squadXZ())
                    loaded &= EyesDirector.surfaceY(mc, xz[0], xz[1]) != Integer.MIN_VALUE;
                if ((!loaded && seconds() < 25) || seconds() < 4)
                    return false;
                squadCentres.clear();
                for (int[] xz : squadXZ())
                    squadCentres.add(EyesDirector.ground(mc, xz[0], xz[1], (int) centre.y));
                centre = squadCentres.get(0);
                ReignOfNether.LOGGER.info("[Eyes] 18_overlays: {} squads of {} round {} (ring {}), ground {}, enemy '{}'",
                        SQUADS, PER_SQUAD, EyesDirector.fmt(centre), RING, loaded ? "loaded" : "NOT all loaded", enemy);
                next(2);
            }
            case 2 -> {   // the squads, a few commands a tick
                int end = Math.min(SQUADS * PER_SQUAD, summoned + SUMMONS_PER_TICK);
                for (; summoned < end; summoned++)
                    summon(mc, summoned);
                if (summoned >= SQUADS * PER_SQUAD)
                    next(3);
            }
            case 3 -> {   // wait for them, then the houses
                int n = ownedFighters();
                if (n < SQUADS * PER_SQUAD - 20 && seconds() < 40)
                    return false;
                if (seconds() < 4)
                    return false;
                ReignOfNether.LOGGER.info("[Eyes] 18_overlays: {} of our units in, {} units known to the client", n,
                        UnitClientEvents.getAllUnits().size());
                planHouses(mc);
                next(4);
            }
            case 4 -> {   // the houses, a hundred a tick
                int end = Math.min(houseSites.size(), placed + HOUSES_PER_TICK);
                for (; placed < end; placed++)
                    placeHouse(mc, placed);
                if (placed >= houseSites.size()) {
                    ReignOfNether.LOGGER.info("[Eyes] 18_overlays: {} client-only houses placed, {} buildings known to the client",
                            placed, BuildingClientEvents.getBuildings().size());
                    selectUnits(mc);
                    EyesDirector.setZoom(ZOOM_MID);
                    OrthoviewClientEvents.centreCameraOnPosInstant(centre);
                    next(5);
                }
            }
            case 5 -> {   // still: units selected, mid zoom
                if (seconds() < 5)
                    return false;
                EyesDirector.shot(mc, "18_overlays_mid");
                ReignOfNether.LOGGER.info("[Eyes] 18_overlays_mid: zoom {}, {} selected, {}", Math.round(OrthoviewClientEvents.getZoom()),
                        UnitClientEvents.getSelectedUnits().size(), scene());
                selectHouse(mc);
                next(6);
            }
            case 6 -> {   // still: one of our houses selected, same camera
                if (seconds() < 2)
                    return false;
                EyesDirector.shot(mc, "18_overlays_mid_building");
                ReignOfNether.LOGGER.info("[Eyes] 18_overlays_mid_building: {} building(s) selected at {}",
                        BuildingClientEvents.getSelectedBuildings().size(), ourHouse == null ? "-" : ourHouse.toShortString());
                selectUnits(mc);
                EyesDirector.setZoom(ZOOM_STRATEGIC);
                next(7);
            }
            case 7 -> {   // still: strategic zoom, units selected
                if (seconds() < 5)
                    return false;
                EyesDirector.shot(mc, "18_overlays_strategic");
                ReignOfNether.LOGGER.info("[Eyes] 18_overlays_strategic: zoom {}, strategic view {}, {}",
                        Math.round(OrthoviewClientEvents.getZoom()), StrategicViewClientEvents.isStrategicView(), scene());
                startSlice(mc, 0);
                next(8);
            }
            case 8 -> {   // the measurement
                if (measure(mc))
                    return false;
                summary();
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    private static void setUp(Minecraft mc) {
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        me = mc.player.getName().getString();
        enemy = EyesDirector.findEnemyName(mc);
        BlockPos home = EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre : EyesDirector.findCapitol(mc);
        // beside our base, across the line to the map's middle (the skirmish maps sit round the origin; eyes: ours at
        // x -196, the bot's at 244): out of the way of a bot attack; the ring's nearest squad stays 50 blocks off the
        // capitol, its furthest well inside the server's view distance round the camera
        double len = Math.sqrt((double) home.getX() * home.getX() + (double) home.getZ() * home.getZ());
        double dirX = 0, dirZ = 1;
        if (len > 32) {
            dirX = home.getZ() / len;
            dirZ = -home.getX() / len;
        }
        centre = new Vec3(Math.floor(home.getX() + dirX * (RING + 50)) + 0.5, home.getY(), Math.floor(home.getZ() + dirZ * (RING + 50)) + 0.5);
        EyesDirector.setZoom(ZOOM_NORMAL);
        OrthoviewClientEvents.centreCameraOnPosInstant(centre);
        summoned = 0;
        placed = 0;
        ourHouse = null;
        results.clear();
        next(1);
    }

    /** The squads' centres on the ground plan: the first under the camera, the rest on the ring. */
    private static List<int[]> squadXZ() {
        List<int[]> out = new ArrayList<>();
        out.add(new int[] { (int) Math.floor(centre.x), (int) Math.floor(centre.z) });
        for (int i = 0; i < SQUADS - 1; i++) {
            double a = Math.PI * 2 * i / (SQUADS - 1);
            out.add(new int[] { (int) Math.floor(centre.x + Math.cos(a) * RING), (int) Math.floor(centre.z + Math.sin(a) * RING) });
        }
        return out;
    }

    /** Unit k: squad k / 50, a 10 x 5 grid two blocks apart round the squad's centre. */
    private static void summon(Minecraft mc, int k) {
        Vec3 c = squadCentres.get(k / PER_SQUAD);
        int i = k % PER_SQUAD;
        int x = (int) Math.floor(c.x) + 2 * (i % SQUAD_COLS) - SQUAD_COLS + 1;
        int z = (int) Math.floor(c.z) + 2 * (i / SQUAD_COLS) - PER_SQUAD / SQUAD_COLS + 1;
        int y = EyesDirector.surfaceY(mc, x, z);
        if (y == Integer.MIN_VALUE)
            y = (int) c.y;
        EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:%s %d %d %d", me.replace("\"", ""),
                ReignOfNether.MOD_ID, TYPES[k % TYPES.length], x, y, z));
    }

    private static int ownedFighters() {
        int n = 0;
        for (LivingEntity le : UnitClientEvents.getAllUnits())
            if (le instanceof Unit u && le.isAlive() && me.equals(u.getOwnerName()))
                n++;
        return n;
    }

    /** The 1,700 grid cells nearest the centre whose footprint (plus a ring) clears every squad. */
    private static void planHouses(Minecraft mc) {
        Building house = Buildings.VILLAGER_HOUSE;
        BlockPos probe = BlockPos.containing(centre);
        BuildingPlacement p = BuildingUtils.getNewBuildingPlacement(house, mc.level, probe, Rotation.NONE, me, false);
        int w = p.maxCorner.getX() - p.minCorner.getX() + 1, d = p.maxCorner.getZ() - p.minCorner.getZ() + 1;
        int stepX = w + 2, stepZ = d + 2;
        int half = 40;   // 81 x 81 cells is plenty for the 1,700 nearest
        List<int[]> cells = new ArrayList<>();
        for (int i = -half; i <= half; i++)
            for (int j = -half; j <= half; j++) {
                int x = (int) Math.floor(centre.x) + i * stepX, z = (int) Math.floor(centre.z) + j * stepZ;
                if (!clearOfSquads(x - 1, z - 1, x + w, z + d))
                    continue;
                cells.add(new int[] { x, z, i * stepX * i * stepX + j * stepZ * j * stepZ });
            }
        cells.sort(Comparator.comparingInt(c -> c[2]));
        houseSites.clear();
        for (int k = 0; k < HOUSES && k < cells.size(); k++) {
            int x = cells.get(k)[0], z = cells.get(k)[1];
            int y = EyesDirector.surfaceY(mc, x, z);
            houseSites.add(new BlockPos(x, y == Integer.MIN_VALUE ? (int) centre.y : y, z));
        }
        ReignOfNether.LOGGER.info("[Eyes] 18_overlays: {} house sites, {} x {} footprint on a {} x {} grid", houseSites.size(),
                w, d, stepX, stepZ);
    }

    private static boolean clearOfSquads(int x0, int z0, int x1, int z1) {
        for (Vec3 c : squadCentres) {
            int cx0 = (int) Math.floor(c.x) - SQUAD_COLS - 1, cx1 = (int) Math.floor(c.x) + SQUAD_COLS + 1;
            int cz0 = (int) Math.floor(c.z) - PER_SQUAD / SQUAD_COLS - 1, cz1 = (int) Math.floor(c.z) + PER_SQUAD / SQUAD_COLS + 1;
            if (x1 >= cx0 && x0 <= cx1 && z1 >= cz0 && z0 <= cz1)
                return false;
        }
        return true;
    }

    /** House k, ours west of the centre and the bot's east of it; built and whole (no damage bars, no scaffolds). */
    private static void placeHouse(Minecraft mc, int k) {
        BlockPos site = houseSites.get(k);
        String owner = site.getX() < centre.x ? me : enemy;
        BuildingClientEvents.placeBuilding(Buildings.VILLAGER_HOUSE, site, Rotation.NONE, owner, 0, false, 0, true,
                PortalPlacement.PortalType.BASIC, site);
        List<BuildingPlacement> all = BuildingClientEvents.getBuildings();
        if (all.isEmpty())
            return;
        BuildingPlacement b = all.get(all.size() - 1);
        if (!b.originPos.equals(site))
            return;
        b.setServerBlocksPlaced(b.getBlocksTotal());
        if (ourHouse == null && owner.equals(me) && site.distSqr(BlockPos.containing(centre)) < 30 * 30)
            ourHouse = site;
    }

    /** Half the centre squad (its western columns) and four squads of the ring. */
    private static void selectUnits(Minecraft mc) {
        BuildingClientEvents.clearSelectedBuildings();
        UnitClientEvents.clearSelectedUnits();
        List<LivingEntity> picked = new ArrayList<>();
        for (LivingEntity le : new ArrayList<>(UnitClientEvents.getAllUnits())) {
            if (!(le instanceof Unit u) || !le.isAlive() || !me.equals(u.getOwnerName()))
                continue;
            int squad = nearestSquad(le.position());
            if ((squad == 0 && le.getX() < centre.x) || (squad >= 1 && squad <= 4))
                picked.add(le);
        }
        for (LivingEntity le : picked)
            UnitClientEvents.addSelectedUnitNoSort(le);
        UnitClientEvents.sortedSelectedUnits();
        if (!UnitClientEvents.getSelectedUnits().isEmpty())
            HudClientEvents.setHudSelectedEntity(UnitClientEvents.getSelectedUnits().get(0));
    }

    private static void selectHouse(Minecraft mc) {
        if (ourHouse == null)
            return;
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (b.originPos.equals(ourHouse)) {
                UnitClientEvents.clearSelectedUnits();
                BuildingClientEvents.clearSelectedBuildings();
                BuildingClientEvents.addSelectedBuilding(b);
                return;
            }
    }

    private static int nearestSquad(Vec3 pos) {
        int best = 0;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < squadCentres.size(); i++) {
            double dx = squadCentres.get(i).x - pos.x, dz = squadCentres.get(i).z - pos.z;
            if (dx * dx + dz * dz < bestD) {
                bestD = dx * dx + dz * dz;
                best = i;
            }
        }
        return best;
    }

    private static String scene() {
        return String.format("%d units and %d buildings known to the client, %d selected units, %d selected buildings",
                UnitClientEvents.getAllUnits().size(), BuildingClientEvents.getBuildings().size(),
                UnitClientEvents.getSelectedUnits().size(), BuildingClientEvents.getSelectedBuildings().size());
    }

    // ---- the measurement ----

    private static void startSlice(Minecraft mc, int i) {
        slice = i;
        measuring = false;
        sliceNanos = System.nanoTime();
        EyesDirector.setZoom(ORDER[i]);
        OrthoviewClientEvents.centreCameraOnPosInstant(centre);
    }

    /** True while the measurement runs. */
    private static boolean measure(Minecraft mc) {
        double s = (System.nanoTime() - sliceNanos) / 1e9;
        if (!measuring) {
            if (s < SETTLE_S)
                return true;
            measuring = true;
            sliceNanos = System.nanoTime();
            sliceFrames0 = FrameParts.framesLifetime();
            for (int k = 0; k < SECTIONS.length; k++)
                sliceNs0[k] = FrameParts.frameSectionLifetimeNs(SECTIONS[k]);
            frames.clear();
            frameCursor = mc.getFrameTimer().getLogEnd();
            return true;
        }
        sampleFrames(mc);
        if (s < SLICE_S)
            return true;
        endSlice();
        if (slice + 1 < ORDER.length) {
            startSlice(mc, slice + 1);
            return true;
        }
        return false;
    }

    /** Every frame drawn since the last tick (vanilla's 240-frame ring, read forward from where the slice began). */
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

    private static void endSlice() {
        long n = Math.max(1, FrameParts.framesLifetime() - sliceFrames0);
        double[] ms = frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double[] row = new double[7 + SECTIONS.length];
        row[0] = ORDER[slice];
        row[1] = OrthoviewClientEvents.getZoom();
        row[2] = StrategicViewClientEvents.isStrategicView() ? 1 : 0;
        row[3] = n;
        row[4] = pct(ms, 0.5);
        row[5] = pct(ms, 0.95);
        row[6] = ms.length == 0 ? 0 : ms[ms.length - 1];
        StringBuilder parts = new StringBuilder();
        for (int k = 0; k < SECTIONS.length; k++) {
            row[7 + k] = (FrameParts.frameSectionLifetimeNs(SECTIONS[k]) - sliceNs0[k]) / 1e6 / n;
            parts.append(k == 0 ? "" : ", ").append(FrameParts.name(SECTIONS[k])).append(' ').append(f2(row[7 + k]));
        }
        results.add(row);
        ReignOfNether.LOGGER.info("[Eyes] 18_overlays slice {}/{}: zoom {} (reached {}, {}), {} frames: p50 {} ms, p95 {} ms, "
                        + "max {} ms | ms/frame: {}", slice + 1, ORDER.length, ORDER[slice], Math.round(row[1]),
                row[2] > 0 ? "strategic" : "normal", n, f1(row[4]), f1(row[5]), f1(row[6]), parts);
    }

    /** Each zoom's medians over its slices: one line per zoom, the before / after numbers. */
    private static void summary() {
        for (int zoom : ZOOMS) {
            List<double[]> rows = new ArrayList<>();
            for (double[] r : results)
                if ((int) r[0] == zoom)
                    rows.add(r);
            if (rows.isEmpty())
                continue;
            StringBuilder parts = new StringBuilder();
            double ours = 0;
            for (int k = 0; k < SECTIONS.length; k++) {
                double m = median(rows, 7 + k);
                if (SECTIONS[k] != FrameParts.UNIT_BODIES && SECTIONS[k] != FrameParts.BUILDING_DECOR
                        && SECTIONS[k] != FrameParts.WORLD_MARKERS)
                    ours += m;   // the overlays+HUD total stays comparable with the runs before Oct 23
                parts.append(k == 0 ? "" : ", ").append(FrameParts.name(SECTIONS[k])).append(' ').append(f2(m));
            }
            ReignOfNether.LOGGER.info("[Eyes] 18_overlays zoom {} (medians of {} slices; {}): frame p50 {} ms, p95 {} ms | "
                            + "ms/frame: {} | overlays+HUD (no bodies) {} ms", zoom, rows.size(), scene(), f1(median(rows, 4)),
                    f1(median(rows, 5)), parts, f2(ours));
        }
    }

    private static double median(List<double[]> rows, int col) {
        double[] v = rows.stream().mapToDouble(r -> r[col]).sorted().toArray();
        return v.length % 2 == 1 ? v[v.length / 2] : (v[v.length / 2 - 1] + v[v.length / 2]) / 2;
    }

    private static double pct(double[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.round((sorted.length - 1) * p)];
    }

    private static String f1(double v) {
        return String.format("%.1f", v);
    }

    private static String f2(double v) {
        return String.format("%.2f", v);
    }
}
