package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.worldmemory.GroundMemoryClient;
import com.solegendary.reignofnether.worldmemory.GroundMemoryServer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Random;

/**
 * Trampled ground on the scars pad (EyesDirector scene 16, eyes.yml input `scars`; design/battlefield_effects.md).
 * The march is stamped with the battle through the entry every unit's walk goes through
 * (GroundMemoryServer.trampleSegment): a worn road (70 militia passes, north-south on the pad's east side), a squad's
 * trail (12 passes) crossing the snow row, and a ravager's single pass. Then, inside the same run, every view is shot
 * with the trample layer OFF (the look main has) and ON, so the control and the change come from one run and one
 * camera:
 * <pre>
 *   16_scars_trample_off_rts / 16_scars_trample_rts       RTS default zoom (30), over the road and the trail
 *   16_scars_trample_off_close / 16_scars_trample_close   zoom 15 on the trail crossing the snow
 *   16_scars_trample_off_fp / 16_scars_trample_fp         first person on the road, looking along it
 * </pre>
 * and the frame cost at zoom 30 (the decal mesh's build + draw ms per frame, GroundMemoryClient.costMean) in four
 * windows: off and on with the pad still, then off and on while a fresh squad keeps walking (the throttled
 * re-meshes). The "[Eyes] 16 trample A/B" line gives each window and on - off against the design's budget (0.30 ms).
 */
final class EyesTrampleScene {
    private EyesTrampleScene() { }

    static final double BUDGET_MS = 0.30;
    private static final double WINDOW_S = 3.0;

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static final double[] cost = new double[8];   // mean ms and frames: off, on, off marching, on marching
    private static int marchStep = 0;
    private static long lastMarchNanos = 0;

    private static double seconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    private static void next(int p) {
        phase = p;
        phaseNanos = System.nanoTime();
    }

    // ------------------------------------------------------------------ the march (server thread)

    /** The scripted march, on the server thread with the battle; {@code gy} is the first free block above the grass. */
    static void march(ServerLevel level, BlockPos site, int gy) {
        Random rnd = new Random(1106);
        double sx = site.getX() + 0.5, sz = site.getZ() + 0.5;
        // the road: 70 militia passes north to south at dx 13, gently winding, each walker a little off the line
        for (int pass = 0; pass < 70; pass++) {
            double off = rnd.nextGaussian() * 0.7;
            for (int dz = -15; dz < 15; dz += 2)
                GroundMemoryServer.trampleSegment(level, sx + 13 + wind(dz) + off, sz + dz, sx + 13 + wind(dz + 2) + off,
                    sz + dz + 2, gy, 0.6);
        }
        // a squad's trail: 12 passes from the south edge to the east side, across the snow row at dz 7
        for (int pass = 0; pass < 12; pass++) {
            double off = rnd.nextGaussian() * 0.6;
            GroundMemoryServer.trampleSegment(level, sx + 3 + off, sz + 15, sx + 8 + off, sz + 7, gy, 0.6);
            GroundMemoryServer.trampleSegment(level, sx + 8 + off, sz + 7, sx + 11 + off, sz + 2, gy, 0.6);
        }
        // one ravager across the south-west (its single pass reads like a squad's)
        GroundMemoryServer.trampleSegment(level, sx - 15, sz + 14.5, sx - 9, sz + 15.5, gy, 1.95);
    }

    private static double wind(double dz) {
        return Math.sin(dz * 0.25) * 0.8;
    }

    /** A fresh squad walking down the pad's east edge at dx 15, one step every half second (the marching windows). */
    private static void marchStep(Minecraft mc, BlockPos site) {
        if (System.nanoTime() - lastMarchNanos < 500_000_000L)
            return;
        lastMarchNanos = System.nanoTime();
        final int step = marchStep++;
        final int gy = site.getY();
        var server = mc.getSingleplayerServer();
        if (server == null)
            return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            double sx = site.getX() + 0.5, sz = site.getZ() + 0.5;
            double z0 = -15 + (step % 15) * 2;
            for (int w = 0; w < 10; w++)
                GroundMemoryServer.trampleSegment(level, sx + 15 + (w % 3 - 1) * 0.5, sz + z0, sx + 15 + (w % 3 - 1) * 0.5,
                    sz + z0 + 2, gy, 0.6);
        });
    }

    // ------------------------------------------------------------------ the stills and the cost (client)

    /** One frame of the scene; true when it is done (the caller goes on to the next scene). */
    static boolean step(Minecraft mc, BlockPos site) {
        Vec3 over = new Vec3(site.getX() + 9.5, site.getY(), site.getZ() + 3.5);
        switch (phase) {
            case 0 -> {
                if (!OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                mc.options.hideGui = true;
                EyesDirector.setZoom(30);
                OrthoviewClientEvents.centreCameraOnPosInstant(over);
                GroundMemoryClient.setDrawTrample(false);
                next(1);
            }
            case 1, 2 -> {   // 1: off, 2: on - wait for a settled mesh, then a still window
                if (seconds() < 3 || (!GroundMemoryClient.settled() && seconds() < 15))
                    return false;
                if (seconds() < 3.2) {
                    GroundMemoryClient.costReset();
                    return false;
                }
                if (seconds() < 3.2 + WINDOW_S)
                    return false;
                record(phase == 1 ? 0 : 1);
                EyesDirector.shot(mc, phase == 1 ? "16_scars_trample_off_rts" : "16_scars_trample_rts");
                GroundMemoryClient.setDrawTrample(phase == 1);
                next(phase + 1);
                if (phase == 3) {
                    GroundMemoryClient.setDrawTrample(false);
                    GroundMemoryClient.costReset();
                }
            }
            case 3, 4 -> {   // 3: off, 4: on - a fresh squad keeps walking (throttled re-meshes)
                marchStep(mc, site);
                if (seconds() < 1.5) {
                    GroundMemoryClient.costReset();
                    return false;
                }
                if (seconds() < 1.5 + WINDOW_S)
                    return false;
                record(phase == 3 ? 2 : 3);
                if (phase == 3) {
                    GroundMemoryClient.setDrawTrample(true);
                    next(4);
                } else {
                    logCost();
                    // the close views: the trail crossing the snow, then first person on the road
                    EyesDirector.setZoom(15);
                    OrthoviewClientEvents.centreCameraOnPosInstant(new Vec3(site.getX() + 8.5, site.getY(), site.getZ() + 7.5));
                    GroundMemoryClient.setDrawTrample(false);
                    next(5);
                }
            }
            case 5, 6 -> {   // zoom 15: off, then on
                if (seconds() < 4 || (!GroundMemoryClient.settled() && seconds() < 15))
                    return false;
                EyesDirector.shot(mc, phase == 5 ? "16_scars_trample_off_close" : "16_scars_trample_close");
                GroundMemoryClient.setDrawTrample(phase == 5);
                if (phase == 6) {
                    if (OrthoviewClientEvents.isEnabled())
                        OrthoviewClientEvents.toggleEnable();
                    stand(mc, site);
                    GroundMemoryClient.setDrawTrample(false);
                }
                next(phase + 1);
            }
            case 7, 8 -> {   // first person on the road: off, then on
                if (seconds() > 1.5 && seconds() < 2.5)
                    stand(mc, site);   // again: the server's own position update may land in between
                if (seconds() < 5 || (!GroundMemoryClient.settled() && seconds() < 15))
                    return false;
                EyesDirector.shot(mc, phase == 7 ? "16_scars_trample_off_fp" : "16_scars_trample_fp");
                GroundMemoryClient.setDrawTrample(true);
                if (phase == 8) {
                    ReignOfNether.LOGGER.info("[Eyes] 16 trample: done, client: {}", GroundMemoryClient.status());
                    next(9);
                    return true;
                }
                next(8);
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    /**
     * Standing on the road's south end, a person's eye height, looking north along it and 30 degrees down, so the road
     * runs from the feet to mid-frame (run 42185251420's first try looked at the horizon: the road was a sliver).
     */
    private static void stand(Minecraft mc, BlockPos site) {
        double px = site.getX() + 13.5, pz = site.getZ() + 14.5, py = site.getY();
        float yaw = (float) Math.toDegrees(Math.atan2(0, -14));
        mc.player.moveTo(px, py, pz, yaw, 30f);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.options.hideGui = true;
    }

    private static void record(int window) {
        double[] m = GroundMemoryClient.costMean();
        cost[window * 2] = m[0];
        cost[window * 2 + 1] = m[1];
    }

    private static void logCost() {
        double still = cost[2] - cost[0], marching = cost[6] - cost[4];
        String line = String.format(Locale.ROOT,
            "[Eyes] 16 trample A/B zoom 30 (decal build + draw ms per frame): still off %.3f (%d frames), on %.3f (%d), on-off %+.3f; "
                + "marching off %.3f (%d), on %.3f (%d), on-off %+.3f; budget %.2f: %s",
            cost[0], (int) cost[1], cost[2], (int) cost[3], still, cost[4], (int) cost[5], cost[6], (int) cost[7], marching,
            BUDGET_MS, still <= BUDGET_MS && marching <= BUDGET_MS ? "inside" : "OVER");
        ReignOfNether.LOGGER.info(line);
    }
}
