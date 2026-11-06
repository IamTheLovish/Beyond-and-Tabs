package com.solegendary.reignofnether.unit.modelling.instancing;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.perf.FrameParts;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Instanced unit bodies (big-army performance, lovish Nov 6): the switch and the frame hooks; the runtime is
 * {@link Instancer}, the GPU side {@link InstancedBodies}, the CPU side {@link Rig} / {@link RigCache} /
 * {@link InstanceBatch}.
 * <p>
 * Switch: {@code unit_instancing} in config/reignofnether-client.toml (re-read while the game runs), or
 * {@code -Dreignofnether.unitInstancing=false} on the command line, which wins; the eyes army scene forces it per slice
 * ({@link #setOverride}). Off = exactly the old renderer. With a shader pack the old renderer draws (the pack replaces the
 * shaders this path reproduces). The draw is the [FrameParts] section {@code unitInstancing}; units still pay their
 * posing and recording under {@code unitBodies}.
 */
@OnlyIn(Dist.CLIENT)
public final class UnitInstancing {
    private UnitInstancing() { }

    /** The [FrameParts] section the instanced draw is timed under. */
    public static final int FRAME_SECTION = FrameParts.UNIT_INSTANCING;
    private static final String PROPERTY = System.getProperty("reignofnether.unitInstancing", "");
    private static Boolean override = null;

    /** The switch: the eyes scene's override, else the command line, else the config. */
    static boolean wanted() {
        if (override != null)
            return override;
        if (!PROPERTY.isEmpty())
            return !PROPERTY.equalsIgnoreCase("false") && !PROPERTY.equals("0") && !PROPERTY.equalsIgnoreCase("off");
        try {
            return ReignOfNetherClientConfigs.UNIT_INSTANCING.get();
        } catch (RuntimeException e) {   // the config is not loaded yet
            return true;
        }
    }

    /** The instanced path can draw (built, its shaders loaded, nothing failed, no shader pack). */
    public static boolean available() {
        return InstancedBodies.ready() && !com.solegendary.reignofnether.postfx.ShaderPacks.inUse();
    }

    /** The eyes scene's A/B: true / false force the path on / off, null leaves it to the switch. */
    public static void setOverride(Boolean on) {
        override = on;
    }

    public static Boolean override() {
        return override;
    }

    /** Unit renders the instanced path drew since the game started. */
    public static long instancedLifetime() {
        return Instancer.instancedLifetime();
    }

    /** The [FrameParts] section of the instanced draw. */
    public static int frameSection() {
        return FRAME_SECTION;
    }

    /** The same-frame vertex check over the next {@code frames} frames (the eyes army scene). */
    public static void startVertexParity(int frames) {
        Instancer.startParity(frames);
    }

    /** The check's verdict line, or null while it runs. */
    public static String vertexParityReport() {
        return Instancer.parityReport();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase == TickEvent.Phase.START)
            Instancer.frameStart(wanted());
    }

    /** Right after the game's entity batches, before anything else drawn at this stage (outlines and markers test our depth). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLevelStage(RenderLevelStageEvent evt) {
        if (evt.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            Instancer.flush();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase == TickEvent.Phase.END)
            Instancer.logTick();
    }
}
