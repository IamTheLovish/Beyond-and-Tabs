package com.solegendary.reignofnether.postfx;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link CanopyShade} read from the level a grass colour call is given (mixin/fogofwar/BiomeColorsMixin): on a meshing
 * thread the meshing task's own region - Embeddium's WorldSlice (its own copy of the sections' light; outside its
 * reach it answers 0) or vanilla's RenderChunkRegion (light as vanilla's own meshing reads it). Never the client level:
 * lovish's crash of Oct 31 03:07 ("Missing Palette entry for index 21") was its getBlockState from an Embeddium
 * worker while the render thread wrote that chunk's palette. Light, not blocks: Embeddium's slice unpacks its
 * neighbours' blocks only within 2 of the section (and reuses the arrays between tasks), but holds the light of the
 * section and all its neighbours, so the taps (2 round), Embeddium's per-corner samples (1 more) and the cell over the
 * ground (1 up) are always inside it. A light read is a byte-array read: no palette. A read that throws leaves the
 * sample unshaded: plain grass, never a crash - and is logged once a session (meshing runs per colour sample: a
 * warning per failure would flood the log), so a next report shows it happened.
 */
public final class CanopyRegion {
    private CanopyRegion() { }

    /** The sky light of a cell, read from the meshing region. */
    @FunctionalInterface
    interface SkyLight {
        int at(int x, int y, int z);
    }

    private static final Logger LOG = LogManager.getLogger("reignofnether/canopy");
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final AtomicInteger LOGGED = new AtomicInteger();

    /** rgb darkened by what stands over the ground round pos, read from {@code level} only. */
    public static int shade(BlockAndTintGetter level, BlockPos pos, int rgb) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        return shade((x, y, z) -> level.getBrightness(LightLayer.SKY, m.set(x, y, z)),
                pos.getX(), pos.getY(), pos.getZ(), rgb);
    }

    /** rgb darkened by what stands over the ground at (x, y, z): a column counts by the sky light of its cell at y + 1. */
    static int shade(SkyLight sky, int x, int y, int z, int rgb) {
        try {
            return CanopyShade.apply(rgb, (cx, cy, cz) -> CanopyShade.covered(sky.at(cx, cy + 1, cz)), x, y, z);
        } catch (RuntimeException e) {
            // several meshing workers can fail at once: only the first says it
            if (WARNED.compareAndSet(false, true)) {
                LOGGED.incrementAndGet();
                LOG.warn("Canopy shade: a sky-light read failed on {} ({}); that grass is left unshaded. Said once a session.",
                        Thread.currentThread().getName(), e.toString());
            }
            return rgb;
        }
    }

    /** How many times a failed read has been logged (it is said once a session). */
    static int warnings() {
        return LOGGED.get();
    }
}
