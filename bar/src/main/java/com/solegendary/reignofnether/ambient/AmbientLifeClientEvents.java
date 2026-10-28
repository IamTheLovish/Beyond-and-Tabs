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
}
