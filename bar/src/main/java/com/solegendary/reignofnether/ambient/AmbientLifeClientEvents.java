package com.solegendary.reignofnether.ambient;

import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.registrars.ParticleRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * Ambient life (lovish Oct 28: "butterflies, fireflies, birds" approved; the ambient mods we looked at spawn round the
 * player entity, which the RTS camera carries ~30 blocks above the ground, so their critters barely show from the RTS
 * view): butterflies over grass and flowers by day, fireflies over meadows at night, round the ground point the camera
 * looks at (AmbientLifeMath.focus) - or round the player in first person. Client only and cheap: a pass every 5 ticks
 * samples a few columns, caps by type that shrink as the camera zooms out (AmbientLifeMath.cap: none at strategic
 * zoom), none in rain. Butterflies favour flowers; neither type spawns over stone, sand, water or buildings.
 * By day a flock of birds now and then crosses the view high up (maybeFlock, AmbientBirdParticle).
 */
public class AmbientLifeClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Random RANDOM = new Random();
    private static final int PERIOD = 5;           // ticks between spawn passes
    private static final int BUTTERFLY_CAP = 18;   // alive at once, close up
    private static final int FIREFLY_CAP = 45;
    private static final int SAMPLES = 8;          // columns tried per pass

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.level == null || MC.player == null || MC.isPaused())
            return;
        if (MC.level.getGameTime() % PERIOD != 0 || !DisplaySettings.ambientLife()
            || StrategicViewClientEvents.isStrategicView() || MC.level.isRaining())
            return;
        long time = MC.level.getDayTime();
        boolean day = AmbientLifeMath.isDay(time), night = AmbientLifeMath.isNight(time);
        if (!day && !night)
            return;
        boolean ortho = OrthoviewClientEvents.isEnabled();
        float zoom = ortho ? OrthoviewClientEvents.getZoom() : 20f;
        double[] f = ortho
            ? AmbientLifeMath.focus(MC.player.getX(), MC.player.getEyeY(), MC.player.getZ(),
                OrthoviewClientEvents.getCamRotX(), OrthoviewClientEvents.getCamRotY(), OrthoviewClientEvents.getGroundY())
            : new double[] { MC.player.getX(), MC.player.getY(), MC.player.getZ() };
        double r = AmbientLifeMath.radius(zoom);
        if (day)
            maybeFlock(f, r, zoom);
        int alive = day ? AmbientCritterParticle.butterflies : AmbientCritterParticle.fireflies;
        int want = AmbientLifeMath.toSpawn(alive, AmbientLifeMath.cap(day ? BUTTERFLY_CAP : FIREFLY_CAP, zoom), day ? 1 : 2);
        for (int i = 0; i < SAMPLES && want > 0; i++) {
            double a = RANDOM.nextDouble() * Mth.TWO_PI, d = Math.sqrt(RANDOM.nextDouble()) * r;
            int x = Mth.floor(f[0] + Math.cos(a) * d), z = Mth.floor(f[2] + Math.sin(a) * d);
            int top = MC.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos at = new BlockPos(x, top - 1, z);
            BlockState s = MC.level.getBlockState(at);
            BlockState below = MC.level.getBlockState(at.below());
            boolean plant = s.is(BlockTags.FLOWERS) || s.is(Blocks.GRASS) || s.is(Blocks.TALL_GRASS) || s.is(Blocks.FERN)
                || s.is(Blocks.LARGE_FERN);
            boolean grassy = plant ? below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.TALL_GRASS) : s.is(Blocks.GRASS_BLOCK);
            if (!grassy)
                continue;
            // butterflies favour flowers: bare grass passes a third of the time
            if (day && !s.is(BlockTags.FLOWERS) && RANDOM.nextFloat() > 0.33f)
                continue;
            MC.particleEngine.createParticle(day ? ParticleRegistrar.BUTTERFLY.get() : ParticleRegistrar.FIREFLY.get(),
                x + RANDOM.nextDouble(), top + 0.6 + RANDOM.nextDouble(), z + RANDOM.nextDouble(), 0, 0, 0);
            want--;
        }
    }

    private static final int FLOCK_ODDS = 160;      // one pass in this many starts a flock: about one every 40 s
    private static final int BIRD_CAP = 14;         // two flocks at most
    private static final float FLOCK_ZOOM = 120;    // past this a bird is a pixel

    /**
     * Now and then a flock - 3 to 7 birds in a V, sometimes one alone - crosses the whole view in a straight line
     * through the focus, 18-26 blocks over the ground the camera looks at (above the trees and most buildings), at
     * 0.3-0.4 blocks a tick.
     */
    private static void maybeFlock(double[] f, double r, float zoom) {
        if (zoom > FLOCK_ZOOM || AmbientBirdParticle.birds > BIRD_CAP - 3 || RANDOM.nextInt(FLOCK_ODDS) != 0)
            return;
        flock(f, r, 0);
    }

    /**
     * A flock now, its leader `along` (0-1) of the way down its path (0.5: over the focus) - for the eyes rig, so a
     * still can show birds without waiting on the odds. RTS view only; does nothing at night or in rain.
     */
    public static void flockNow(double along) {
        if (MC.level == null || MC.player == null || !OrthoviewClientEvents.isEnabled() || MC.level.isRaining()
            || !AmbientLifeMath.isDay(MC.level.getDayTime()))
            return;
        float zoom = OrthoviewClientEvents.getZoom();
        double[] f = AmbientLifeMath.focus(MC.player.getX(), MC.player.getEyeY(), MC.player.getZ(),
            OrthoviewClientEvents.getCamRotX(), OrthoviewClientEvents.getCamRotY(), OrthoviewClientEvents.getGroundY());
        flock(f, AmbientLifeMath.radius(zoom), Math.max(0, Math.min(0.9, along)));
    }

    private static void flock(double[] f, double r, double along) {
        double angle = RANDOM.nextDouble() * Mth.TWO_PI;
        double[] path = AmbientLifeMath.flockPath(f[0], f[2], r, angle);
        double speed = 0.3 + RANDOM.nextDouble() * 0.1;
        double vx = Math.cos(angle) * speed, vz = Math.sin(angle) * speed;
        double len = Math.hypot(path[2] - path[0], path[3] - path[1]);
        double sx = path[0] + (path[2] - path[0]) * along, sz = path[1] + (path[3] - path[1]) * along;
        len *= 1 - along;
        double y = f[1] + 18 + RANDOM.nextDouble() * 8;
        int n = RANDOM.nextFloat() < 0.15f ? 1 : 3 + RANDOM.nextInt(5);
        // behind = against the heading; left of the heading (+ side) is (sin, -cos) in Minecraft's x / z
        double bx = -Math.cos(angle), bz = -Math.sin(angle), lx = Math.sin(angle), lz = -Math.cos(angle);
        for (int i = 0; i < n; i++) {
            double[] slot = AmbientLifeMath.vSlot(i);
            double back = slot[0] + (RANDOM.nextDouble() - 0.5) * 0.4, side = slot[1] + (RANDOM.nextDouble() - 0.5) * 0.4;
            double x = sx + bx * back + lx * side, z = sz + bz * back + lz * side;
            int life = (int) Math.ceil((len + back) / speed);
            MC.particleEngine.createParticle(ParticleRegistrar.BIRD.get(), x, y + (RANDOM.nextDouble() - 0.5) * 0.6, z,
                vx, life, vz);
        }
    }
}
