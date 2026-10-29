package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Combat feel, part 1 (lovish approved Oct 28), the client half of {@link CameraShake}: holds the one trauma value,
 * steps it every frame, and puts this frame's shake on the camera - yaw, pitch and roll through Forge's
 * {@link ViewportEvent.ComputeCameraAngles} in both views, and in the RTS view also a small screen offset through the
 * orthographic projection (OrthoviewClientEvents asks {@link BarFxClient#shakeX()}). The player's Display setting
 * (Off / Subtle / Full) and the Camera page's strength slider scale it; the strategic (icon) view never shakes.
 *
 * Fed by BarFxClient (blasts, heavy shells, building collapses, T3 blows as minor feeds) and by the landings this class
 * watches itself: the camera's own body landing hard (a possessed hero off a ledge) and big units landing on screen.
 * Allocation-free per frame; render and client thread only.
 */
@OnlyIn(Dist.CLIENT)
public final class CameraShakeClient {

    private CameraShakeClient() {}

    private static final CameraShake SHAKE = new CameraShake();
    private static final float[] OUT = new float[CameraShake.OUT];
    private static long lastNanos = -1;
    private static float offX = 0f, offY = 0f;
    private static ClientLevel lastLevel = null;

    /** A big unit: one whose landing is felt (a T3 body, a dragon) - bounding box at least this wide and tall. */
    private static final float BIG_WIDTH = 1.4f, BIG_HEIGHT = 2.0f;
    /** Vertical speed of the last tick (blocks per tick, + = up) of the camera's body and of the big units seen. */
    private static final Int2FloatOpenHashMap FALLING = new Int2FloatOpenHashMap();
    private static int tick = 0;

    static void add(float trauma) {
        SHAKE.add(trauma);
    }

    static void addMinor(float trauma) {
        SHAKE.addMinor(trauma);
    }

    public static void reset() {
        SHAKE.reset();
        offX = offY = 0f;
        FALLING.clear();
    }

    public static float trauma() {
        return SHAKE.trauma();
    }

    /** This frame's RTS screen offset, blocks (0 outside the RTS view). */
    public static float offsetX() {
        return offX;
    }

    public static float offsetY() {
        return offY;
    }

    /** Once per frame (BarFxClientEvents): step the trauma, then shake the camera's angles. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles evt) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastNanos < 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (mc.isPaused())
            dt = 0f;
        SHAKE.step(dt);
        offX = offY = 0f;
        if (SHAKE.trauma() <= 0f)
            return;
        boolean rts = OrthoviewClientEvents.isEnabled();
        if (rts && StrategicViewClientEvents.isStrategicView())
            return;   // icons over a map: nothing to feel, and a moving map misleads
        SHAKE.output(rts, DisplaySettings.cameraShake(), BarFxClient.shakeSetting(),
            rts ? OrthoviewClientEvents.getZoom() : 0f, OUT);
        if (OUT[CameraShake.YAW] == 0f && OUT[CameraShake.PITCH] == 0f && OUT[CameraShake.ROLL] == 0f)
            return;
        evt.setYaw(evt.getYaw() + OUT[CameraShake.YAW]);
        evt.setPitch(evt.getPitch() + OUT[CameraShake.PITCH]);
        evt.setRoll(evt.getRoll() + OUT[CameraShake.ROLL]);
        if (rts) {
            offX = OUT[CameraShake.OFF_X];
            offY = OUT[CameraShake.OFF_Y];
        }
    }

    /** Hard landings: the camera's own body (full strength, it is you) and big units on screen (minor feeds). */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            reset();
            lastLevel = level;
        }
        if (level == null || mc.isPaused() || DisplaySettings.cameraShake() == CameraShake.OFF)
            return;
        tick++;
        Entity cam = mc.getCameraEntity();
        boolean rts = OrthoviewClientEvents.isEnabled();
        if (!rts && cam instanceof LivingEntity body) {
            float fall = landed(body);
            if (fall > 0f)
                add(CameraShake.traumaForLanding(fall));
        }
        if (rts && !StrategicViewClientEvents.isStrategicView()) {
            for (Entity e : level.entitiesForRendering())
                if (e instanceof Unit && e instanceof LivingEntity le && le.getBbWidth() >= BIG_WIDTH
                    && le.getBbHeight() >= BIG_HEIGHT && le.isAlive()) {
                    float fall = landed(le);
                    if (fall > 0f)
                        BarFxClient.shakeMinor(CameraShake.traumaForLanding(fall), (float) le.getX(), (float) le.getY(),
                            (float) le.getZ());
                }
        }
        if (tick % 200 == 0)
            FALLING.clear();   // forget units gone from view; a landing needs only the last tick
    }

    /** The fall speed (blocks per tick) of a body that touched down this tick after falling, else 0. */
    private static float landed(LivingEntity e) {
        float vy = (float) (e.getY() - e.yo);
        float before = FALLING.getOrDefault(e.getId(), 0f);
        FALLING.put(e.getId(), vy);
        return e.onGround() && before < -0.5f && vy > before * 0.5f ? -before : 0f;
    }
}
