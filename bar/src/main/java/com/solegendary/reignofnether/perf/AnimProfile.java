package com.solegendary.reignofnether.perf;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.modelling.renderers.GeneratedBodies;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Arrays;

/**
 * The client's animation profile, the Life layer's cost in a real match (claude/animation-foundation-audit.md; the
 * companion of {@link FrameProfile}, which times whole frames). mixin/AnimProfileRendererMixin clocks, for every
 * unit LivingEntityRenderer draws, (a) its animation - prepareMobModel + setupAnim, where every rig poses its parts -
 * and (b) its whole render (the posed model into the buffers, its layers, its name tag). The sums per frame go into
 * a ring of the last 240 frames; every 10 s in a world one {@code [Anim]} log line gives units per frame, the
 * animation and render time per frame at p50 / p95, the per-unit averages, the zoom and the detail state. Reads and
 * four nanoTime calls per unit; nothing drawn, nothing changed. A shader pack's shadow pass draws units twice and is
 * counted twice: that is what the frame pays.
 *
 * lovish's latest.log and the eyes tour's log carry these lines; the CI bench (test/perf/AnimationCostBench) gives
 * the same split for 500 units without a window.
 */
public final class AnimProfile {
    private AnimProfile() { }

    static final int LOG_EVERY_TICKS = FrameProfile.LOG_EVERY_TICKS;
    private static final int RING = 240;

    // the frame being drawn
    private static int frameUnits = 0;
    private static long frameAnimNs = 0, frameRenderNs = 0;
    private static int depth = 0;
    private static long renderStart = 0, animStart = 0;
    private static boolean animSeen = false;   // a unit whose Pre event was cancelled (an icon) never poses: not counted

    // the last RING closed frames (only frames that drew at least one unit are kept)
    private static final int[] units = new int[RING];
    private static final long[] animNs = new long[RING], renderNs = new long[RING];
    private static int head = 0, filled = 0;
    private static int ticks = 0;

    /** The renderer starts drawing a unit (nested renders - a passenger drawn by its mount - count in the outer one). */
    public static void beginRender(LivingEntity entity) {
        if (!(entity instanceof Unit))
            return;
        if (depth++ == 0) {
            renderStart = System.nanoTime();
            animSeen = false;
        }
    }

    /** The renderer is about to pose the unit's model (prepareMobModel, then setupAnim). */
    public static void beginAnim(LivingEntity entity) {
        if (entity instanceof Unit && depth == 1)
            animStart = System.nanoTime();
    }

    public static void endAnim(LivingEntity entity) {
        if (entity instanceof Unit && depth == 1 && animStart != 0) {
            frameAnimNs += System.nanoTime() - animStart;
            animStart = 0;
            animSeen = true;
        }
    }

    public static void endRender(LivingEntity entity) {
        if (!(entity instanceof Unit))
            return;
        if (--depth == 0 && animSeen) {
            frameRenderNs += System.nanoTime() - renderStart;
            frameUnits++;
        }
        if (depth < 0)
            depth = 0;
    }

    /** The start of a frame closes the previous one. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        if (frameUnits > 0) {
            units[head] = frameUnits;
            animNs[head] = frameAnimNs;
            renderNs[head] = frameRenderNs;
            head = (head + 1) % RING;
            if (filled < RING)
                filled++;
        }
        frameUnits = 0;
        frameAnimNs = 0;
        frameRenderNs = 0;
        depth = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ticks = 0;
            return;
        }
        if (++ticks % LOG_EVERY_TICKS != 0)
            return;
        String line = summary();
        if (line != null)
            ReignOfNether.LOGGER.info(line);
    }

    /** The profile of the kept frames, or null when no unit was drawn since the last summary. */
    public static String summary() {
        int n = filled;
        if (n < 10)
            return null;
        double[] anim = new double[n], render = new double[n];
        long sumUnits = 0, sumAnim = 0, sumRender = 0;
        int maxUnits = 0;
        for (int i = 0; i < n; i++) {
            anim[i] = animNs[i] / 1.0e6;
            render[i] = renderNs[i] / 1.0e6;
            sumUnits += units[i];
            sumAnim += animNs[i];
            sumRender += renderNs[i];
            maxUnits = Math.max(maxUnits, units[i]);
        }
        Arrays.sort(anim);
        Arrays.sort(render);
        double animP50 = anim[Math.round((n - 1) * 0.50f)], animP95 = anim[Math.round((n - 1) * 0.95f)];
        double renderP50 = render[Math.round((n - 1) * 0.50f)], renderP95 = render[Math.round((n - 1) * 0.95f)];
        double perUnitAnimUs = sumUnits == 0 ? 0 : sumAnim / 1.0e3 / sumUnits;
        double perUnitRenderUs = sumUnits == 0 ? 0 : sumRender / 1.0e3 / sumUnits;
        String line = String.format(java.util.Locale.ROOT,
            "[Anim] %d units/frame (max %d): animation p50 %s ms, p95 %s ms; unit render p50 %s ms, p95 %s ms; per unit %.1f us anim, %.1f us render (anim %.0f%%); %s view, zoom %d, %s, over %d frames",
            Math.round((double) sumUnits / n), maxUnits, ServerTickProfile.fmt(animP50), ServerTickProfile.fmt(animP95),
            ServerTickProfile.fmt(renderP50), ServerTickProfile.fmt(renderP95), perUnitAnimUs, perUnitRenderUs,
            perUnitRenderUs == 0 ? 0 : 100.0 * perUnitAnimUs / perUnitRenderUs,
            OrthoviewClientEvents.isEnabled() ? "RTS" : "first-person", Math.round(OrthoviewClientEvents.getZoom()),
            StrategicViewClientEvents.isStrategicView() ? "icons" : (OrthoviewClientEvents.isEnabled()
                && OrthoviewClientEvents.getZoom() <= GeneratedBodies.DETAIL_ZOOM ? "detail on" : "detail off"), n);
        filled = 0;
        head = 0;
        return line;
    }
}
