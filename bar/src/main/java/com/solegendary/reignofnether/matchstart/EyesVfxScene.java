package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.VfxClient;
import com.solegendary.reignofnether.barfx.vfx.Vfx;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.perf.FrameParts;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 19_vfx (Oct 22, the vfx foundation, design/vfx.md): the Dawn Paladin's sun_pillar - the showcase of the data-driven
 * effects - photographed the way players get it: a paladin and four of ours summoned beside our base, the effect
 * started through the real server path (Vfx.on -> BarFx event -> fog filter -> VfxClient) riding on the paladin.
 * The effects' clock runs at half speed here (VfxClient.timeScale) and each still waits for the effect's own age
 * (VfxClient.newestAge), so a software-rendered client at a few frames a second still lands one in each phase:
 * <ul>
 *   <li>19_vfx_rts_windup - the tiled sigil and the rune rings opening (RTS camera, zoom 22);</li>
 *   <li>19_vfx_rts_peak - the pillar in, the double helix, embers and the light pool on the ground and the units;</li>
 *   <li>19_vfx_34_peak - the same moment from a first-person 3/4 view (the effect is played again for it).</li>
 * </ul>
 * The log gets "[Eyes] 19_vfx cost": the [FrameParts] vfx ms per frame over the effect, live effects and particles.
 * Wall-clock timeline (like scenes 16-18). Its own run only (eyes.yml input `vfx`).
 */
final class EyesVfxScene {
    private EyesVfxScene() { }

    static final boolean VFX_ONLY = Boolean.getBoolean("bat.eyes.vfx");
    private static final float SLOW = 0.5f;
    /** Give up waiting for the effect after this long (s, wall clock) and take the still anyway. */
    private static final double PATIENCE = 45;
    private static final String EFFECT = "sun_pillar";

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static Vec3 spot;
    private static long vfxNs0, frames0;
    private static int maxParticles = 0;

    private static double seconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    private static void next(int p) {
        phase = p;
        phaseNanos = System.nanoTime();
    }

    /** One client tick; true when the scene is over. */
    static boolean tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 19_vfx: no level, player or integrated server - skipped");
            return true;
        }
        maxParticles = Math.max(maxParticles, VfxClient.liveParticles());
        // effect seconds = wall seconds x SLOW (the clock is the effects' own, see VfxClient.timeScale)
        switch (phase) {
            case 0 -> setUp(mc);
            case 1 -> {   // the units in and the camera settled
                if (seconds() > 8) {
                    play(mc);
                    next(2);
                }
            }
            case 2 -> {   // wind-up: rings opening (effect 0.55 s of its 0.9 s wind-up) - timed by the effect's own age
                if (VfxClient.newestAge() >= 0.55f || seconds() > PATIENCE) {
                    still(mc, "19_vfx_rts_windup");
                    next(3);
                }
            }
            case 3 -> {   // peak: the pillar has dropped in (effect 1.5 s, 0.6 s into the peak)
                if (VfxClient.newestAge() >= 1.5f || seconds() > PATIENCE) {
                    still(mc, "19_vfx_rts_peak");
                    next(4);
                }
            }
            case 4 -> {   // let it fade out, log the cost, then go first person
                if (VfxClient.newestAge() < 0 || seconds() > PATIENCE) {
                    long frames = FrameParts.framesLifetime() - frames0;
                    double ms = frames <= 0 ? 0 : (FrameParts.frameSectionLifetimeNs(FrameParts.VFX) - vfxNs0) / 1e6 / frames;
                    ReignOfNether.LOGGER.info("[Eyes] 19_vfx cost: {} ms/frame over {} frames (vfx_quality {}), up to {} particles; effects live now {}",
                        String.format("%.3f", ms), frames, VfxClient.quality(), maxParticles, VfxClient.liveEffects());
                    if (OrthoviewClientEvents.isEnabled())
                        OrthoviewClientEvents.toggleEnable();
                    next(5);
                }
            }
            case 5 -> {   // a 3/4 view: 11 blocks off, eye ~4 above the ground, looking at the paladin's chest
                if (seconds() > 0.5 && seconds() < 2.5) {
                    double px = spot.x - 8, pz = spot.z - 8;
                    double py = EyesDirector.ground(mc, (int) Math.floor(px), (int) Math.floor(pz), (int) spot.y).y + 2.4;
                    double dx = spot.x - px, dz = spot.z - pz, dy = spot.y + 2.5 - (py + mc.player.getEyeHeight());
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
                    mc.player.moveTo(px, py, pz, yaw, pitch);
                    mc.player.setDeltaMovement(Vec3.ZERO);
                }
                if (seconds() > 3) {
                    play(mc);
                    next(6);
                }
            }
            case 6 -> {
                if (VfxClient.newestAge() >= 1.5f || seconds() > PATIENCE) {
                    still(mc, "19_vfx_34_peak");
                    next(7);
                }
            }
            case 7 -> {
                if (VfxClient.newestAge() < 0 || seconds() > PATIENCE) {
                    VfxClient.timeScale = 1f;
                    ReignOfNether.LOGGER.info("[Eyes] 19_vfx: done");
                    return true;
                }
            }
            default -> { return true; }
        }
        return false;
    }

    /** A still, with what the effects pass drew in the frame before it (drawn-but-invisible vs not drawn). */
    private static void still(Minecraft mc, String name) {
        ReignOfNether.LOGGER.info("[Eyes] {}: effect age {} s, {} live, {} particles, {} quads drawn last frame, shader pack {}",
            name, String.format("%.2f", VfxClient.newestAge()), VfxClient.liveEffects(), VfxClient.liveParticles(),
            VfxClient.quadsLastFrame(), com.solegendary.reignofnether.postfx.ShaderPacks.inUse());
        EyesDirector.shot(mc, name);
    }

    private static void setUp(Minecraft mc) {
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        mc.options.hideGui = true;
        VfxClient.timeScale = SLOW;
        BlockPos home = EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre : EyesDirector.findCapitol(mc);
        double len = Math.sqrt((double) home.getX() * home.getX() + (double) home.getZ() * home.getZ());
        double dirX = len > 32 ? -home.getX() / len : 1, dirZ = len > 32 ? -home.getZ() / len : 0;
        spot = EyesDirector.ground(mc, (int) Math.floor(home.getX() + dirX * 30), (int) Math.floor(home.getZ() + dirZ * 30), home.getY());
        String owner = mc.player.getName().getString().replace("\"", "");
        EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:dawn_paladin_unit %d %d %d", owner, ReignOfNether.MOD_ID,
            (int) Math.floor(spot.x), (int) spot.y, (int) Math.floor(spot.z)));
        // four of ours round him at 3-5 blocks: the light should fall on them
        int[][] ring = { { 4, 0 }, { -3, 2 }, { 1, -4 }, { -2, -3 } };
        for (int[] r : ring) {
            Vec3 at = EyesDirector.ground(mc, (int) Math.floor(spot.x) + r[0], (int) Math.floor(spot.z) + r[1], (int) spot.y);
            EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:militia_unit %d %d %d", owner, ReignOfNether.MOD_ID,
                (int) Math.floor(at.x), (int) at.y, (int) Math.floor(at.z)));
        }
        EyesDirector.setZoom(22);
        OrthoviewClientEvents.centreCameraOnPosInstant(spot);
        ReignOfNether.LOGGER.info("[Eyes] 19_vfx: paladin and four militia summoned at {}", EyesDirector.fmt(spot));
        next(1);
    }

    /** The effect through the server path, on the paladin when he is there (else at the spot). */
    private static void play(Minecraft mc) {
        vfxNs0 = FrameParts.frameSectionLifetimeNs(FrameParts.VFX);
        frames0 = FrameParts.framesLifetime();
        Vec3 at = spot;
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var level = server.overworld();
                List<Entity> found = level.getEntities((Entity) null, new AABB(at, at).inflate(6),
                    e -> e.getType() == EntityRegistrar.DAWN_PALADIN_UNIT.get() && e.isAlive());
                if (found.isEmpty()) {
                    Vfx.at(level, EFFECT, at);
                    ReignOfNether.LOGGER.warn("[Eyes] 19_vfx: no paladin near {} - played at the spot", EyesDirector.fmt(at));
                } else {
                    Vfx.on(level, EFFECT, found.get(0));
                    ReignOfNether.LOGGER.info("[Eyes] 19_vfx: {} on the paladin at {}", EFFECT, EyesDirector.fmt(found.get(0).position()));
                }
            } catch (Exception e) {
                ReignOfNether.LOGGER.error("[Eyes] 19_vfx: play failed on the server", e);
            }
        });
    }
}
