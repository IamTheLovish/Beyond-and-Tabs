package com.solegendary.reignofnether.unit.modelling.instancing;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The instanced unit bodies' switch (big-army performance, lovish Nov 6). This first commit only measures: the path is
 * not built yet, so {@link #available()} is false and the eyes army scene (matchstart/EyesArmyScene) runs its slices on
 * today's renderer alone - the baseline the target is set from before anything is built.
 */
@OnlyIn(Dist.CLIENT)
public final class UnitInstancing {
    private UnitInstancing() { }

    private static Boolean override = null;

    /** The instanced path can draw this frame (built, the GPU can, no shader pack). Not built yet: false. */
    public static boolean available() {
        return false;
    }

    /** The eyes scene's A/B: true / false force the path on / off, null leaves it to the config. */
    public static void setOverride(Boolean on) {
        override = on;
    }

    public static Boolean override() {
        return override;
    }

    /** Unit renders the instanced path drew since the game started (none yet). */
    public static long instancedLifetime() {
        return 0;
    }

    /** The [FrameParts] section of the instanced draw, or -1 while there is none. */
    public static int frameSection() {
        return -1;
    }

    /** The same-frame vertex check (the instanced path's pose and colour against the old path's): not built yet. */
    public static void startVertexParity(int frames) {
    }

    /** The check's verdict line, or null while it runs (or was never started). */
    public static String vertexParityReport() {
        return "not built";
    }
}
