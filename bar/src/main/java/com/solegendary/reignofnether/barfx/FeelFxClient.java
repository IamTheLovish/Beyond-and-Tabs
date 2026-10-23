package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * The client hooks of the feel kit (FeelFx holds the state and the math): a FEEL event from the server adds trauma
 * (with distance falloff from the camera) and starts the hit-stop; every frame the camera angles get the decayed,
 * squared trauma. Allocation-free per frame.
 */
@OnlyIn(Dist.CLIENT)
public final class FeelFxClient {
    private FeelFxClient() { }

    private static long lastNanos = -1;
    private static float clock = 0;

    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /** FEEL event: kind = size class, a = host entity id, b = victim entity id. */
    static void onFeel(BarFx.Event e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        Entity cam = mc.getCameraEntity();
        if (cam == null)
            return;
        int size = e.kind;
        float dx = (float) (e.x - cam.getX()), dy = (float) (e.y - cam.getY()), dz = (float) (e.z - cam.getZ());
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean mine = (int) e.a == cam.getId();
        if (FeelFx.canShake(OrthoviewClientEvents.isEnabled(), mc.options.getCameraType() != null))
            FeelFx.addTrauma(FeelFx.traumaAt(FeelFx.traumaFor(size), mine ? 0f : dist));
        // the freeze is the host's own view: only when this client is looking through the host (or is it)
        if (mine || (mc.player != null && (int) e.a == mc.player.getId()))
            FeelFx.startHitStop((int) e.a, (int) e.b, size, nowMs());
    }

    /** Camera angles once per frame: yaw, pitch and roll noise of trauma^2 * slider * 2.5 degrees. */
    public static void applyCamera(ViewportEvent.ComputeCameraAngles evt) {
        long now = nowMs();
        long n = System.nanoTime();
        float dt = lastNanos < 0 ? 0 : Math.min(0.1f, (n - lastNanos) / 1e9f);
        lastNanos = n;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused())
            dt = 0;
        if (!FeelFx.cameraHeld(now))
            clock += dt;
        FeelFx.step(dt, now);
        if (FeelFx.trauma() <= 0f || !FeelFx.canShake(OrthoviewClientEvents.isEnabled(), mc.options.getCameraType() != null))
            return;
        float s = FeelFx.shake(FeelFx.trauma(), FeelFx.strength());
        if (s <= 0f)
            return;
        evt.setYaw(evt.getYaw() + s * FeelFx.MAX_YAW_DEGREES * FeelFx.noise(clock, 0));
        evt.setPitch(evt.getPitch() + s * FeelFx.MAX_PITCH_DEGREES * FeelFx.noise(clock, 1));
        evt.setRoll(evt.getRoll() + s * FeelFx.MAX_YAW_DEGREES * 0.5f * FeelFx.noise(clock, 2));
    }
}
