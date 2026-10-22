package com.solegendary.reignofnether.unit.modelling.renderers;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.postfx.ShaderPacks;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The unit render level of detail, live side (the rules and their reasons: {@link UnitLodRules}).
 *
 * <ul>
 *   <li>{@link #beginUnit} - called by mixin/AnimProfileRendererMixin at the head of every LivingEntityRenderer.render
 *       of a unit - asks the shader pack once whether this draw is its shadow pass; the unit's layers and
 *       perf/AnimProfile read the answer ({@link #inShadowPass}).</li>
 *   <li>{@link #skipOverlay} - the overlay layers (TeamColourLayer, GeneratedBodies.glow, UpgradeGlowLayer and every
 *       vanilla EyesLayer on a unit through mixin/UnitLodEyesLayerMixin) return at once in the shadow pass.</li>
 *   <li>{@link #firstPersonDetail} - GeneratedBodies.wantsDetail outside the RTS view: detail parts by on-screen size.</li>
 * </ul>
 *
 * A/B toggle: {@code unit_render_lod} in config/reignofnether-client.toml (Forge reloads the file while the game
 * runs), or {@code -Dreignofnether.unitLod=false} on the command line, which wins. Off = exactly the old behaviour.
 * The [FrameParts] line splits the unit draw into {@code unitBodies} (the view) and {@code unitShadow} (the shader
 * pack's shadow map), so the two settings can be compared from latest.log.
 */
@OnlyIn(Dist.CLIENT)
public final class UnitLod {
    private UnitLod() { }

    private static final String PROPERTY = System.getProperty("reignofnether.unitLod", "");

    private static boolean on = true;            // read once per frame from the config
    private static boolean shadowPass = false;   // the unit being drawn is drawn into the shader pack's shadow map
    private static double fovDeg = UnitLodRules.REF_FOV_DEG;   // the world view's real FOV this frame

    /** The LOD is on (the config toggle, or the system property when set). */
    public static boolean enabled() {
        return on;
    }

    private static boolean readToggle() {
        if (!PROPERTY.isEmpty())
            return !PROPERTY.equalsIgnoreCase("false") && !PROPERTY.equals("0") && !PROPERTY.equalsIgnoreCase("off");
        try {
            return ReignOfNetherClientConfigs.UNIT_RENDER_LOD.get();
        } catch (RuntimeException e) {   // the config is not loaded yet
            return true;
        }
    }

    /** A unit's render starts (any pass). One reflective ask of the shader pack per unit draw. */
    public static void beginUnit(Entity entity) {
        if (entity instanceof Unit)
            shadowPass = ShaderPacks.renderingShadowPass();
    }

    /** The unit being drawn now is drawn into the shader pack's shadow map (false without a pack). */
    public static boolean inShadowPass() {
        return shadowPass;
    }

    /** An overlay layer of this entity draws nothing now (see UnitLodRules.skipOverlayLayer). */
    public static boolean skipOverlay(Entity entity) {
        return UnitLodRules.skipOverlayLayer(on, entity instanceof Unit, shadowPass);
    }

    /** Outside the RTS view: the unit's small detail parts are drawn ({@code distance} from the camera, in blocks). */
    public static boolean firstPersonDetail(double distance) {
        if (!on)
            return UnitLodRules.legacyFirstPersonDetail(distance);
        int screenHeight = Minecraft.getInstance().getWindow().getHeight();
        return UnitLodRules.firstPersonDetail(UnitLodRules.pxPerBlockPerspective(screenHeight, fovDeg, distance));
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        on = readToggle();
        shadowPass = false;
    }

    /** The world view's FOV after every modifier (sprint, spyglass, possession zoom): the last handler's answer. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFov(ViewportEvent.ComputeFov evt) {
        if (evt.usedConfiguredFov())
            fovDeg = evt.getFOV();
    }
}
