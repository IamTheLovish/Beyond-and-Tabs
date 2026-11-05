package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.BarFxClient;
import com.solegendary.reignofnether.barfx.PortalForming;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * 20_forming: the forming moment from the RTS camera (eyes.yml input {@code forming}: after 02, nothing else of the
 * tour runs). Eyes-only, off by default, no gameplay change: the stills lovish judges the effect by (barfx/FormingFx,
 * PortalForming; Nov 5: "our own version" of a portal that forms), which the GameTests cannot show.
 * <p>
 * Beside our base, a basic portal's frame is stood up block by block (not yet a building the game ticks) and a Sun
 * Spire beside it. Then the portal completes through the game's own path ({@code BuildingPlacement.tick}: onBuilt, the
 * shared hook, the fire that lights it), and the client's forming is photographed by its own age, so the still lands
 * in the phase it names whatever the frame rate:
 * <ul>
 *   <li>20_forming_before - the frame standing, nothing lit;</li>
 *   <li>20_forming_spark (0.15 s), _fill (0.6 s), _swirl (1.05 s), _settle (1.42 s) - one replay of the same site per
 *       still ({@link PortalForming#send}, the same event), since one screenshot can outlast a phase;</li>
 *   <li>20_forming_after - 3 s later: the lit portal and nothing of the effect left (the log prints the live count);</li>
 *   <li>20_forming_ender - the Sun Spire's forming in the Sunforged tint at 0.9 s;</li>
 *   <li>20_forming_far - the portal again at a strategic zoom at 0.9 s: does it still read.</li>
 * </ul>
 * Every still has its 10-second clip (eyes.yml), so the first one also shows the whole forming in motion. Wall-clock
 * timeline, like scenes 16-19 (a shader-lane client runs a few ticks a second).
 */
final class EyesFormingScene {
    private EyesFormingScene() { }

    private static final int ZOOM_MID = 24, ZOOM_FAR = 70, OFFSET = 60;
    /** The forming's age at each phase still (FormingTimeline: spark 0-.3, fill .2-.95, swirl .65-1.45, settle 1.3-1.6). */
    private static final float AT_SPARK = .15f, AT_FILL = .6f, AT_SWIRL = 1.05f, AT_SETTLE = 1.42f, AT_WHOLE = .9f;

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static Vec3 centre, spireAt;
    private static volatile BuildingPlacement portal, spire;
    private static volatile String error = null;
    /** FormingFx's started count before the last trigger: the next still waits for a newer forming. */
    private static int before = 0;
    private static float target = 0;
    private static String nextShot = null;

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
            ReignOfNether.LOGGER.warn("[Eyes] 20_forming: no level, player or server - skipped");
            return true;
        }
        if (error != null) {
            ReignOfNether.LOGGER.warn("[Eyes] 20_forming: ending early ({})", error);
            return true;
        }
        switch (phase) {
            case 0 -> setUp(mc);
            case 1 -> {   // the camera moved: wait for the ground, then stand the frames up
                boolean loaded = EyesDirector.surfaceY(mc, (int) Math.floor(centre.x), (int) Math.floor(centre.z)) != Integer.MIN_VALUE
                    && EyesDirector.surfaceY(mc, (int) Math.floor(spireAt.x), (int) Math.floor(spireAt.z)) != Integer.MIN_VALUE;
                if ((!loaded && seconds() < 25) || seconds() < 4)
                    return false;
                standFrames(mc);
                next(2);
            }
            case 2 -> {
                if ((portal == null && seconds() < 20) || seconds() < 5)
                    return false;
                if (portal == null) {
                    ReignOfNether.LOGGER.warn("[Eyes] 20_forming: the frames never stood - skipped");
                    return true;
                }
                EyesDirector.shot(mc, "20_forming_before");
                completePortal(mc);
                await("20_forming_spark", AT_SPARK, 3);
            }
            case 3, 4, 5, 6, 8, 9 -> {   // a forming is on its way: shoot when the newest one is old enough
                if (BarFxClient.formingsStarted() <= before) {
                    if (seconds() > 15) {
                        ReignOfNether.LOGGER.warn("[Eyes] {}: no forming reached the client in 15 s", nextShot);
                        return true;
                    }
                    return false;
                }
                if (BarFxClient.newestFormingAge() < target)
                    return false;
                EyesDirector.shot(mc, nextShot);
                ReignOfNether.LOGGER.info("[Eyes] {}: forming age {} s at the shot, {} live", nextShot,
                    String.format("%.2f", BarFxClient.newestFormingAge()), BarFxClient.liveFormings());
                switch (phase) {
                    case 3 -> { replay(mc, portal); await("20_forming_fill", AT_FILL, 4); }
                    case 4 -> { replay(mc, portal); await("20_forming_swirl", AT_SWIRL, 5); }
                    case 5 -> { replay(mc, portal); await("20_forming_settle", AT_SETTLE, 6); }
                    case 6 -> next(7);
                    case 8 -> {
                        OrthoviewClientEvents.centreCameraOnPosInstant(centre);
                        EyesDirector.setZoom(ZOOM_FAR);
                        next(10);
                    }
                    default -> { return true; }
                }
            }
            case 7 -> {   // the effect is over: the lit portal, nothing left behind
                if (seconds() < 3)
                    return false;
                EyesDirector.shot(mc, "20_forming_after");
                ReignOfNether.LOGGER.info("[Eyes] 20_forming_after: {} formings live 3 s after the last one (0 = nothing left behind)",
                    BarFxClient.liveFormings());
                OrthoviewClientEvents.centreCameraOnPosInstant(spireAt);
                replay(mc, spire);
                await("20_forming_ender", AT_WHOLE, 8);
            }
            case 10 -> {   // the zoom eases out: wait, then the strategic still
                if (seconds() < 3)
                    return false;
                replay(mc, portal);
                await("20_forming_far", AT_WHOLE, 9);
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    private static void await(String shot, float age, int p) {
        nextShot = shot;
        target = age;
        next(p);
    }

    private static void setUp(Minecraft mc) {
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        BlockPos home = EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre : EyesDirector.findCapitol(mc);
        // beside our base, across the line to the map's middle (as scenes 18 and 19): out of the bot's way
        double len = Math.sqrt((double) home.getX() * home.getX() + (double) home.getZ() * home.getZ());
        double dirX = 0, dirZ = 1;
        if (len > 32) {
            dirX = home.getZ() / len;
            dirZ = -home.getX() / len;
        }
        centre = new Vec3(Math.floor(home.getX() + dirX * OFFSET) + 0.5, home.getY(), Math.floor(home.getZ() + dirZ * OFFSET) + 0.5);
        spireAt = centre.add(dirX * 22, 0, dirZ * 22);
        // a photo booth, not a match: the site is beyond our base's vision, and the server's fog filter withheld both
        // the frame's blocks and the forming itself (the fog rule working: run 41982195607, "no forming reached the
        // client in 15 s"), so fog of war goes off on both sides, as for scene 15
        if (FogOfWarClientEvents.isEnabled())
            FogOfWarClientEvents.setEnabled(false);
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            if (FogOfWarServerEvents.isEnabled())
                FogOfWarServerEvents.setEnabled(false);
            ReignOfNether.LOGGER.info("[Eyes] 20_forming: fog of war {}",
                FogOfWarServerEvents.isEnabled() ? "STILL ON (force-fog rule)" : "off");
        });
        EyesDirector.setZoom(ZOOM_MID);
        OrthoviewClientEvents.centreCameraOnPosInstant(centre);
        next(1);
    }

    /** On the server's thread: both frames' blocks put down, neither placement yet a building the game ticks. */
    private static void standFrames(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        String me = mc.player.getName().getString();
        int py = EyesDirector.surfaceY(mc, (int) Math.floor(centre.x), (int) Math.floor(centre.z));
        int sy = EyesDirector.surfaceY(mc, (int) Math.floor(spireAt.x), (int) Math.floor(spireAt.z));
        BlockPos pAt = BlockPos.containing(centre.x - 2, py == Integer.MIN_VALUE ? centre.y : py, centre.z - 2);
        BlockPos sAt = BlockPos.containing(spireAt.x - 4, sy == Integer.MIN_VALUE ? spireAt.y : sy, spireAt.z - 4);
        server.execute(() -> {
            try {
                ServerLevel level = server.overworld();
                BuildingPlacement p = BuildingUtils.getNewBuildingPlacement(Buildings.PORTAL_BASIC, level, pAt, Rotation.NONE, me, false);
                BuildingPlacement s = BuildingUtils.getNewBuildingPlacement(Buildings.SUN_SPIRE, level, sAt, Rotation.NONE, me, false);
                for (BuildingPlacement bp : new BuildingPlacement[] { p, s }) {
                    for (BuildingBlock b : bp.getBlocks())
                        if (!b.getBlockState().isAir())
                            level.setBlock(b.getBlockPos(), b.getBlockState(), 3);
                    bp.initPlacedBlocks();
                    bp.tickAge = PortalForming.MIN_AGE_TICKS;   // it "rose" here: old enough for its moment
                }
                spire = s;
                portal = p;
                ReignOfNether.LOGGER.info("[Eyes] 20_forming: a basic portal at {} and a Sun Spire at {} stand, unlit",
                    p.minCorner.toShortString(), s.minCorner.toShortString());
            } catch (Exception e) {
                error = e.toString();
                ReignOfNether.LOGGER.error("[Eyes] 20_forming: the frames failed", e);
            }
        });
    }

    /** The portal completes through the game's own tick: onBuilt, the shared forming hook, the fire that lights it. */
    private static void completePortal(Minecraft mc) {
        before = BarFxClient.formingsStarted();
        var server = mc.getSingleplayerServer();
        BuildingPlacement p = portal;
        server.execute(() -> {
            try {
                p.tick(server.overworld());
                ReignOfNether.LOGGER.info("[Eyes] 20_forming: the portal completed (built {}, formed {})", p.isBuilt, p.formingShown);
            } catch (Exception e) {
                error = e.toString();
                ReignOfNether.LOGGER.error("[Eyes] 20_forming: the portal's completion failed", e);
            }
        });
    }

    /** The same site's FORMING event again (PortalForming.send), for the next phase's still. */
    private static void replay(Minecraft mc, BuildingPlacement bp) {
        before = BarFxClient.formingsStarted();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                PortalForming.send(server.overworld(), bp);
            } catch (Exception e) {
                error = e.toString();
                ReignOfNether.LOGGER.error("[Eyes] 20_forming: the replay failed", e);
            }
        });
    }
}
