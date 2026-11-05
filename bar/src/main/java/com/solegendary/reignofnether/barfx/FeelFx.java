package com.solegendary.reignofnether.barfx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The game feel kit (hero-and-tier4 design section 2, slice H3): what a hosted ability calls so a hit lands with
 * weight. Three parts, all free of client classes so the math is testable headless:
 * <ul>
 *   <li>trauma shake: trauma 0..1 decays at 1.5/s, the offset is trauma^2 * strength * max (2.5 degrees);
 *       strength is the player's camera shake slider (0 = no camera change); only first/third-person cameras shake,
 *       never the RTS view;</li>
 *   <li>hit-stop: a 40-120 ms freeze by impact size, on the hosted body and the victim (local client only);</li>
 *   <li>impact sound: a punch + body + tail layer from vanilla sounds, +-5% pitch.</li>
 * </ul>
 * {@link #impact} is the one server call: it plays the sound layers and tells the nearby clients (BarFx FEEL event)
 * to shake and hit-stop. Client state lives in static fields and is allocation-free per frame
 * ({@code FeelFxClient} drives it).
 */
public final class FeelFx {
    private FeelFx() { }

    // ---- impact size classes (the design's 40 / 70 / 100 ms hit-stop)
    public static final int SMALL = 0, BIG = 1, ULTIMATE = 2;

    public static final float DECAY_PER_SECOND = 1.5f;
    public static final float MAX_YAW_DEGREES = 2.5f, MAX_PITCH_DEGREES = 2.5f;
    /** Shake reaches this far from the impact (distance falloff); the host itself is at 0. */
    public static final float SHAKE_RANGE = 24f;

    // ================================================================== trauma math (pure)

    /** Trauma gained by an impact of this size class. */
    public static float traumaFor(int size) {
        return size >= ULTIMATE ? 0.9f : size == BIG ? 0.55f : 0.25f;
    }

    /** Hit-stop length in ms for a size class: 40 small, 70 big, 100 ultimate (always within 40..120). */
    public static int hitStopMs(int size) {
        return size >= ULTIMATE ? 100 : size == BIG ? 70 : 40;
    }

    /** Trauma after {@code dtSeconds}, never below 0. */
    public static float decayed(float trauma, float dtSeconds) {
        return Math.max(0f, trauma - DECAY_PER_SECOND * Math.max(0f, dtSeconds));
    }

    /** Trauma that reaches a viewer {@code distance} blocks from the impact: linear falloff to 0 at SHAKE_RANGE. */
    public static float traumaAt(float trauma, float distance) {
        return trauma * Math.max(0f, 1f - Math.max(0f, distance) / SHAKE_RANGE);
    }

    /**
     * The shake magnitude 0..1 before the per-axis noise and the maximum: trauma^2, scaled by the slider.
     * Strength 0 (or less) always gives exactly 0 - the camera is not touched.
     */
    public static float shake(float trauma, float strength) {
        float t = Math.max(0f, Math.min(1f, trauma));
        float s = Math.max(0f, Math.min(1f, strength));
        return t * t * s;
    }

    /** May the camera shake? Only the local player's first/third-person view, never the RTS (ortho) view. */
    public static boolean canShake(boolean rtsView, boolean firstOrThirdPerson) {
        return !rtsView && firstOrThirdPerson;
    }

    /** Noise in -1..1 from a clock and an axis seed (two sines: no allocation, no Random). */
    public static float noise(float clockSeconds, int axis) {
        float f = 31f + axis * 11.7f;
        return (float) (Math.sin(clockSeconds * f + axis * 2.1) * 0.65 + Math.sin(clockSeconds * f * 1.73 + axis) * 0.35);
    }

    // ================================================================== client state

    private static float trauma = 0f;
    private static long freezeUntilMs = 0;
    private static int freezeA = -1, freezeB = -1;

    public static float trauma() {
        return trauma;
    }

    public static void addTrauma(float add) {
        trauma = Math.min(1f, trauma + Math.max(0f, add));
    }

    public static void reset() {
        trauma = 0f;
        freezeUntilMs = 0;
        freezeA = freezeB = -1;
    }

    /** Frame step. While a hit-stop is running the shake is held (the jolt freezes with the world). */
    public static void step(float dtSeconds, long nowMs) {
        if (nowMs < freezeUntilMs)
            return;
        trauma = decayed(trauma, dtSeconds);
    }

    /** Freeze the host's and the victim's animation (entity ids) for the size's hit-stop, from {@code nowMs}. */
    public static void startHitStop(int hostId, int victimId, int size, long nowMs) {
        freezeA = hostId;
        freezeB = victimId;
        freezeUntilMs = Math.max(freezeUntilMs, nowMs + hitStopMs(size));
    }

    /** Is this entity's animation held right now? (render hooks ask; false when no hit-stop runs). */
    public static boolean isFrozen(int entityId, long nowMs) {
        return nowMs < freezeUntilMs && (entityId == freezeA || entityId == freezeB);
    }

    public static boolean cameraHeld(long nowMs) {
        return nowMs < freezeUntilMs;
    }

    /** The player's slider as 0..1 (camera_shake, 0-100%); 1 when the config is not loaded yet. */
    public static float strength() {
        try {
            return com.solegendary.reignofnether.config.ReignOfNetherClientConfigs.CAMERA_SHAKE.get() / 100f;
        } catch (Throwable e) {
            return 1f;
        }
    }

    // ================================================================== impact sound layers (server)

    /** Layer table per size class: {punch, body, tail}. */
    private static final SoundEvent[][] LAYERS = {
        {SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundEvents.PLAYER_ATTACK_STRONG, SoundEvents.PLAYER_ATTACK_SWEEP},
        {SoundEvents.ANVIL_LAND, SoundEvents.IRON_GOLEM_ATTACK, SoundEvents.PLAYER_ATTACK_CRIT},
        {SoundEvents.GENERIC_EXPLODE, SoundEvents.ANVIL_LAND, SoundEvents.LIGHTNING_BOLT_THUNDER},
    };
    private static final float[][] LAYER_VOLUME = {{0.8f, 0.6f, 0.4f}, {1.0f, 0.9f, 0.5f}, {1.0f, 1.0f, 0.7f}};
    private static final float[][] LAYER_PITCH = {{0.9f, 1.2f, 1.4f}, {0.6f, 1.0f, 1.3f}, {0.7f, 0.8f, 0.8f}};

    /** +-5% pitch from a random value in [0,1). */
    public static float pitchVariance(float random01) {
        return 0.95f + 0.10f * random01;
    }

    /** Plays the punch + body + tail layers of an impact (same tick, vanilla sounds; +-5% pitch each). */
    public static void impactSound(ServerLevel level, Vec3 at, int size) {
        int s = Math.max(0, Math.min(ULTIMATE, size));
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < LAYERS[s].length; i++)
            level.playSound(null, pos, LAYERS[s][i], SoundSource.NEUTRAL, LAYER_VOLUME[s][i],
                LAYER_PITCH[s][i] * pitchVariance(level.random.nextFloat()));
    }

    /**
     * A hit lands with weight: the sound layers, then a FEEL event to every nearby client (they shake with distance
     * falloff and hit-stop the host and victim). {@code victim} may be null (ground slam).
     */
    public static void impact(ServerLevel level, Vec3 at, Entity host, Entity victim, int size) {
        impactSound(level, at, size);
        BarFx.emit(level, new BarFx.Event(BarFx.FEEL, (byte) size, (byte) 0, (float) at.x, (float) at.y, (float) at.z,
            host == null ? -1 : host.getId(), victim == null ? -1 : victim.getId(), 0));
    }
}
