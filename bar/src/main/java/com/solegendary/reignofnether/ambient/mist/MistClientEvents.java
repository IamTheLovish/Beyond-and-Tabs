package com.solegendary.reignofnether.ambient.mist;

import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.matchstart.EyesPhotoBooth;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.registrars.ParticleRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Random;

/**
 * The living valley mist (lovish Oct 28: "can we make the fog more animated and alive or rich"; the north star's "a
 * world that remembers ... valley fog"). Vanilla fog is a flat fade with distance that the RTS camera hardly shows,
 * and shader packs' volumetric fog only exists with a shader pack (and Bliss fogged the RTS view out); this is our own
 * ground mist that works with or without shaders: soft flat wisps (MistParticle) that pool in hollows, along water and
 * at the foot of cliffs and terrace walls, drift with the wind the trees sway in, gather at dawn, burn off by late
 * morning and rise again at dusk (MistMath holds every rule; MistMathTest).
 *
 * The ambient life's pattern (#334): a pass every PERIOD ticks samples a few columns round the ground point the RTS
 * camera looks at (or round the player in first person), scores each from a 7 x 7 height grid 2 blocks apart, and
 * spawns a wisp with that column's chance - capped by count, the cap times the hour's density, shrinking as the camera
 * zooms out and zero at strategic zoom. Client only and cheap: at most SAMPLES columns a pass, 49 height + 49 block
 * reads each, and under BASE_CAP sprites. None in rain, none outside the overworld, none on a column whose top is not
 * natural ground or water (never over a building's footprint). Display setting "Valley mist" (default on).
 */
public class MistClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Random RANDOM = new Random();
    static final int PERIOD = 8;          // ticks between spawn passes
    static final int SAMPLES = 5;         // columns tried per pass
    static final int PER_PASS = 2;        // wisps spawned per pass at most
    static final int BASE_CAP = 28;       // alive at once at full density, close up
    /** -Dbat.eyes.mist=true: the eyes rig's mist pilot photos (eyes.yml input `mist`), read once. */
    private static final String EYES_MIST = System.getProperty("bat.eyes.mist");
    private static ClientLevel lastLevel = null;

    /** The setting is on and the eyes rig is not keeping the measured structure stills clear. */
    public static boolean enabled() {
        return DisplaySettings.valleyMist() && MistMath.allowed(EyesPhotoBooth.ambientLifeOff(), EYES_MIST);
    }

    /** Whether mist may show now: wisps alive leave over a second when this turns false. */
    public static boolean mayShow() {
        ClientLevel level = MC.level;
        return level != null && enabled() && level.dimension() == Level.OVERWORLD && !level.isRaining()
            && !StrategicViewClientEvents.isStrategicView();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || MC.level == null || MC.player == null || MC.isPaused())
            return;
        ClientLevel level = MC.level;
        if (level != lastLevel) {
            // a new level clears the particle engine without calling remove(): start the count again, or a stale count
            // would hold the spawner at its cap for good
            lastLevel = level;
            MistParticle.alive = 0;
        }
        if (level.getGameTime() % PERIOD != 0 || !mayShow())
            return;
        double density = MistMath.density(level.getDayTime());
        if (density <= 0)
            return;
        boolean ortho = OrthoviewClientEvents.isEnabled();
        float zoom = ortho ? OrthoviewClientEvents.getZoom() : 20f;
        int cap = MistMath.cap(BASE_CAP, zoom, StrategicViewClientEvents.isStrategicView());
        int want = MistMath.toSpawn(MistParticle.alive, MistMath.wanted(cap, density), PER_PASS);
        if (want <= 0)
            return;
        double[] f = ortho
            ? MistMath.focus(MC.player.getX(), MC.player.getEyeY(), MC.player.getZ(),
                OrthoviewClientEvents.getCamRotX(), OrthoviewClientEvents.getCamRotY(), OrthoviewClientEvents.getGroundY())
            : new double[] { MC.player.getX(), MC.player.getY(), MC.player.getZ() };
        double r = MistMath.radius(zoom);
        int[][] h = new int[MistMath.GRID_N][MistMath.GRID_N];
        boolean[][] water = new boolean[MistMath.GRID_N][MistMath.GRID_N];
        for (int i = 0; i < SAMPLES && want > 0; i++) {
            double a = RANDOM.nextDouble() * Mth.TWO_PI, d = Math.sqrt(RANDOM.nextDouble()) * r;
            int x = Mth.floor(f[0] + Math.cos(a) * d), z = Mth.floor(f[2] + Math.sin(a) * d);
            if (!naturalTop(level, x, z))
                continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            sampleGrid(level, x, z, top, h, water);
            double score = MistMath.score(h, water, level.getSeaLevel());
            if (score <= 0 || RANDOM.nextDouble() > score)
                continue;
            double y = top + MistMath.hover(RANDOM.nextDouble());
            MC.particleEngine.createParticle(ParticleRegistrar.MIST.get(), x + RANDOM.nextDouble(), y,
                z + RANDOM.nextDouble(), MistMath.peakAlpha(score, density), 0, 0);
            want--;
        }
    }

    /**
     * The height grid round (x, z): each cell's MOTION_BLOCKING height and whether its top is water. A cell whose top
     * is not natural ground or water (a tree, a wall, a roof) reads as the centre's own height: a tree trunk is not a
     * cliff and a building's wall should not gather mist at its foot.
     */
    private static void sampleGrid(ClientLevel level, int x, int z, int centreTop, int[][] h, boolean[][] water) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < MistMath.GRID_N; i++)
            for (int j = 0; j < MistMath.GRID_N; j++) {
                int cx = x + (i - MistMath.GRID_R) * MistMath.GRID_STEP, cz = z + (j - MistMath.GRID_R) * MistMath.GRID_STEP;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
                BlockState s = level.getBlockState(pos.set(cx, top - 1, cz));
                boolean wet = s.getFluidState().is(FluidTags.WATER);
                water[i][j] = wet;
                h[i][j] = wet || naturalGround(s) ? top : centreTop;
            }
    }

    /** The column's top (under any grass or flowers) is natural ground or water: mist may lie over it. */
    public static boolean naturalTop(Level level, int x, int z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (top <= level.getMinBuildHeight())
            return false;
        BlockState s = level.getBlockState(new BlockPos(x, top - 1, z));
        return s.getFluidState().is(FluidTags.WATER) || naturalGround(s);
    }

    /** Soil, stone, sand, gravel, clay, snow, ice - ArenaStructureClearing.natural's ground, plus ice. */
    static boolean naturalGround(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND)
            || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.ICE)
            || s.is(Blocks.PACKED_ICE);
    }
}
