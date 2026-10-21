package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.fogofwar.FogRemesh;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.perf.SectionRebuilds;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.worldborder.WorldBorderServerEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FrameTimer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 17_fog: fog of war over a marching army, and the A/B of the fog re-mesh (Oct 21 perf plan, PR R1: fog of war
 * re-meshes only the surface band, fogofwar.FogRemesh). The tour had no fog until now ("fog of war on the client:
 * false" in every log), so neither its look nor its cost had ever been seen on CI.
 *
 * Fog needs a bounded map (WorldBorderServerEvents.isRtsOptimisedMap): the scene fits a 1000-block border round the
 * march, turns fog on, summons a dozen of our units off the capitol's apron and marches them out into the dark towards
 * the map's middle, the camera following at zoom 45. The march is cut in eight 6-second slices that alternate the
 * re-mesh mode ABBA-wise (surface, full, full, surface, ...), so the column spreading and the terrain changing weigh both
 * modes the same; each slice logs the sections fog marked, the renderer's rebuilds (perf.SectionRebuilds) and the
 * frame times (p50 / p95 / max of the frames drawn in it), and the A/B line gives each mode's medians.
 *
 * Then the look: the army halts in surface mode and the fog edge round it is photographed (17_fog_edge); every section is
 * re-meshed from scratch (LevelRenderer.allChanged, the ground truth) and the same frame taken again
 * (17_fog_edge_remeshed) - a section the band left with an old tint would differ between the two. 17_fog_trail shows the
 * explored trail at a far zoom. Last, the first-person view: FogRemesh re-meshes the underground it left stale.
 *
 * Wall-clock timeline, like scene 16 (the shader lane's client runs a few ticks a second). Last scene of the full tour.
 */
final class EyesFogScene {
    private EyesFogScene() { }

    private static final int ZOOM = 45;
    private static final int BORDER = 1000;
    private static final double SLICE_S = 6;
    private static final FogRemesh.Mode S = FogRemesh.Mode.SURFACE, F = FogRemesh.Mode.FULL;
    // ABBA, and the last slice in surface mode: the edge photographed after it was meshed by the shipped path
    private static final FogRemesh.Mode[] ORDER = { S, F, F, S, S, F, F, S };
    private static final int START_OUT = 26, WAYPOINT_STEP = 40, MAX_OUT = 360;
    private static final String[] MARCHERS = { "militia_unit", "militia_unit", "militia_unit", "militia_unit",
            "militia_unit", "militia_unit", "pillager_unit", "pillager_unit", "pillager_unit", "pillager_unit",
            "vindicator_unit", "vindicator_unit" };

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static BlockPos home;
    private static double dirX = 1, dirZ = 0;
    private static Vec3 start;
    private static int[] ids = new int[0];
    private static int waypoint = 0;
    private static Vec3 waypointPos = null;
    private static volatile String serverNote = null;

    private static int slice = 0;
    private static long sliceNanos = 0;
    private static FogRemesh.Totals sliceStart;
    private static final List<Double> frames = new ArrayList<>();
    private static int frameCursor = -1;
    // per slice: mode (0 surface, 1 full), seconds, fog updates, marked, skipped, rebuilt, rebuilt air, p50, p95, max
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
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 17_fog: no level, player or integrated server - skipped");
            return true;
        }
        switch (phase) {
            case 0 -> setUp(mc);
            case 1 -> {   // fog on at the client, the marchers in: give the fog's full re-mesh a few seconds to settle
                List<LivingEntity> near = EyesDirector.unitsNear(mc, start, 14, true);
                boolean fog = FogOfWarClientEvents.isEnabled();
                if ((!fog || near.size() < MARCHERS.length - 2) && seconds() < 40)
                    return false;
                if (seconds() < 8)
                    return false;
                if (!fog || near.isEmpty()) {
                    ReignOfNether.LOGGER.warn("[Eyes] 17_fog: gave up - fog on the client {}, {} marchers ({})", fog,
                            near.size(), serverNote);
                    return true;
                }
                ids = near.stream().mapToInt(Entity::getId).toArray();
                ReignOfNether.LOGGER.info("[Eyes] 17_fog: {} marchers at {}, heading {}/{} from the capitol {}; {}", ids.length,
                        EyesDirector.fmt(start), String.format("%.2f", dirX), String.format("%.2f", dirZ),
                        home.toShortString(), serverNote);
                nextWaypoint(mc);
                startSlice(mc, 0);
                next(2);
            }
            case 2 -> march(mc);
            case 3 -> {   // halted in surface mode: the fog edge as the shipped path left it
                follow(mc);
                if (seconds() < 5)
                    return false;
                EyesDirector.shot(mc, "17_fog_edge");
                FogOfWarClientEvents.resetFogChunks();   // every section re-meshed from scratch: the ground truth
                next(4);
            }
            case 4 -> {
                if (seconds() < 12)
                    return false;
                EyesDirector.shot(mc, "17_fog_edge_remeshed");
                Vec3 c = centre(mc);
                Vec3 mid = c == null ? start : new Vec3((home.getX() + c.x) / 2, c.y, (home.getZ() + c.z) / 2);
                EyesDirector.setZoom(110);
                OrthoviewClientEvents.centreCameraOnPosInstant(mid);
                next(5);
            }
            case 5 -> {
                if (seconds() < 6)
                    return false;
                EyesDirector.shot(mc, "17_fog_trail");
                // first person: the columns the band left with an old tint underground are re-meshed now (FogRemesh)
                sliceStart = FogRemesh.totals();
                if (OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                next(6);
            }
            case 6 -> {
                if (seconds() < 3)
                    return false;
                FogRemesh.Totals d = FogRemesh.totals().minus(sliceStart);
                ReignOfNether.LOGGER.info("[Eyes] 17_fog: first person: {} sections re-meshed from the stale columns, {} stale left",
                        d.flushed(), FogRemesh.staleChunks());
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
        mc.options.hideGui = true;
        FogRemesh.mode = S;
        home = EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre : EyesDirector.findCapitol(mc);
        // out of our base towards the map's middle (the skirmish maps sit round the origin; eyes: ours at x -196, the bot's at 244)
        double len = Math.sqrt((double) home.getX() * home.getX() + (double) home.getZ() * home.getZ());
        if (len > 32) {
            dirX = -home.getX() / len;
            dirZ = -home.getZ() / len;
        }
        start = EyesDirector.ground(mc, (int) Math.floor(home.getX() + dirX * START_OUT),
                (int) Math.floor(home.getZ() + dirZ * START_OUT), home.getY());
        double borderX = home.getX() + dirX * 150, borderZ = home.getZ() + dirZ * 150;
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                WorldBorder wb = server.overworld().getWorldBorder();
                double was = wb.getSize();
                if (was > WorldBorderServerEvents.RTS_OPTIMIZED_BORDER) {
                    wb.setCenter(borderX, borderZ);
                    wb.setSize(BORDER);
                }
                FogOfWarServerEvents.setEnabled(true);
                serverNote = String.format("border %d -> %d, fog on the server: %s", (long) was, (long) wb.getSize(),
                        FogOfWarServerEvents.isEnabled());
            } catch (Exception e) {
                serverNote = "fog setup failed: " + e;
                ReignOfNether.LOGGER.error("[Eyes] 17_fog: fog setup failed on the server", e);
            }
        });
        // a dozen of ours in a 4 x 3 grid, across the heading
        String me = mc.player.getName().getString().replace("\"", "");
        int[] cols = { -3, -1, 1, 3 };
        for (int i = 0; i < MARCHERS.length; i++) {
            double side = cols[i % 4], back = -2.0 * (i / 4);
            int x = (int) Math.floor(start.x - dirZ * side + dirX * back), z = (int) Math.floor(start.z + dirX * side + dirZ * back);
            Vec3 at = EyesDirector.ground(mc, x, z, (int) start.y);
            EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:%s %d %d %d", me, ReignOfNether.MOD_ID,
                    MARCHERS[i], x, (int) at.y, z));
        }
        EyesDirector.setZoom(ZOOM);
        OrthoviewClientEvents.centreCameraOnPosInstant(start);
        results.clear();
        ReignOfNether.LOGGER.info("[Eyes] 17_fog: fog of war on, {} marchers summoned at {}", MARCHERS.length, EyesDirector.fmt(start));
        next(1);
    }

    private static void march(Minecraft mc) {
        sampleFrames(mc);
        Vec3 c = follow(mc);
        if (c != null && waypointPos != null && c.distanceToSqr(waypointPos.x, c.y, waypointPos.z) < 18 * 18)
            nextWaypoint(mc);
        if ((System.nanoTime() - sliceNanos) / 1e9 < SLICE_S)
            return;
        endSlice();
        if (slice + 1 < ORDER.length) {
            startSlice(mc, slice + 1);
            return;
        }
        FogRemesh.mode = S;
        UnitClientEvents.sendUnitCommandManual(UnitAction.STOP, ids);
        next(3);
    }

    /** The camera on the marchers' middle; their centre, or null when none is left. */
    private static Vec3 follow(Minecraft mc) {
        Vec3 c = centre(mc);
        if (c != null)
            OrthoviewClientEvents.centreCameraOnPosInstant(c);
        return c;
    }

    private static Vec3 centre(Minecraft mc) {
        List<LivingEntity> alive = new ArrayList<>();
        for (int id : ids)
            if (mc.level.getEntity(id) instanceof LivingEntity le && le.isAlive())
                alive.add(le);
        return EyesDirector.centroid(alive);
    }

    private static void nextWaypoint(Minecraft mc) {
        int out = Math.min(MAX_OUT, START_OUT + WAYPOINT_STEP * ++waypoint);
        int x = (int) Math.floor(home.getX() + dirX * out), z = (int) Math.floor(home.getZ() + dirZ * out);
        waypointPos = EyesDirector.ground(mc, x, z, home.getY());
        UnitClientEvents.sendUnitCommandManual(UnitAction.MOVE, -1, ids, BlockPos.containing(waypointPos));
    }

    private static void startSlice(Minecraft mc, int i) {
        slice = i;
        FogRemesh.mode = ORDER[i];
        sliceStart = FogRemesh.totals();
        sliceNanos = System.nanoTime();
        frames.clear();
        frameCursor = mc.getFrameTimer().getLogEnd();
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
        double secs = (System.nanoTime() - sliceNanos) / 1e9;
        FogRemesh.Totals d = FogRemesh.totals().minus(sliceStart);
        double[] ms = frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double p50 = pct(ms, 0.5), p95 = pct(ms, 0.95), max = ms.length == 0 ? 0 : ms[ms.length - 1];
        results.add(new double[] { ORDER[slice] == F ? 1 : 0, secs, d.updates(), d.marked() / secs, d.skipped() / secs,
                d.rebuilt() / secs, d.rebuiltEmpty() / secs, p50, p95, max });
        ReignOfNether.LOGGER.info("[Eyes] 17_fog slice {}/{} ({}): {} s, {} fog updates, marked {}/s (skipped {}/s), rebuilt "
                        + "{}/s (+{}/s air) on {}, frames {}: p50 {} ms, p95 {} ms, max {} ms",
                slice + 1, ORDER.length, ORDER[slice].name().toLowerCase(), f1(secs), d.updates(), Math.round(d.marked() / secs),
                Math.round(d.skipped() / secs), Math.round(d.rebuilt() / secs), Math.round(d.rebuiltEmpty() / secs),
                SectionRebuilds.renderer(), ms.length, f1(p50), f1(p95), f1(max));
    }

    /** The A/B: each mode's medians over its slices. */
    private static void summary() {
        double[][] m = new double[2][];
        for (int mode = 0; mode < 2; mode++) {
            List<double[]> rows = new ArrayList<>();
            for (double[] r : results)
                if ((int) r[0] == mode)
                    rows.add(r);
            if (rows.isEmpty())
                return;
            m[mode] = new double[] { median(rows, 3), median(rows, 5), median(rows, 6), median(rows, 7), median(rows, 8),
                    rows.stream().mapToDouble(r -> r[9]).max().orElse(0) };
        }
        double[] full = m[1], surf = m[0];
        ReignOfNether.LOGGER.info("[Eyes] 17_fog A/B (medians of {} slices each, {}): full column: marked {}/s, rebuilt {}/s "
                        + "(+{}/s air), frame p50 {} / p95 {} / max {} ms | surface band: marked {}/s, rebuilt {}/s (+{}/s air), "
                        + "frame p50 {} / p95 {} / max {} ms | marks {}x fewer, rebuilds {}x fewer",
                ORDER.length / 2, SectionRebuilds.renderer(),
                Math.round(full[0]), Math.round(full[1]), Math.round(full[2]), f1(full[3]), f1(full[4]), f1(full[5]),
                Math.round(surf[0]), Math.round(surf[1]), Math.round(surf[2]), f1(surf[3]), f1(surf[4]), f1(surf[5]),
                f1(full[0] / Math.max(1, surf[0])), f1(full[1] / Math.max(1, surf[1])));
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
}
