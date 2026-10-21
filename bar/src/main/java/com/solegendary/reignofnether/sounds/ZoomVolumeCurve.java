package com.solegendary.reignofnether.sounds;

import net.minecraft.sounds.SoundSource;

/**
 * The maths behind {@link ZoomVolume} (BAR-style volume by zoom), with no client imports, so the dedicated GameTest
 * server can check it. ZoomVolume itself touches client-only sound classes (SoundInstance, PlaySoundEvent): loading it on
 * a dedicated server throws "Attempted to load class ... for invalid dist DEDICATED_SERVER" - which is how PR #64's
 * world_sounds_get_quieter_as_the_camera_zooms_out failed (CI run job 119075990157).
 */
public final class ZoomVolumeCurve {
    private ZoomVolumeCurve() { }

    /** Full volume at or under this zoom (blocks of view height). */
    public static final float NEAR = 12f;
    /** Zoom of the quietest volume; past it the volume holds. */
    public static final float FAR = 150f;
    public static final float QUIETEST = 0.15f;

    /** Volume multiplier for a zoom (blocks of view height): 1 close in, falling smoothly (log scale) to QUIETEST. */
    public static float factor(float zoom) {
        if (!(zoom > NEAR))
            return 1f;
        if (zoom >= FAR)
            return QUIETEST;
        double t = Math.log(zoom / NEAR) / Math.log(FAR / NEAR);   // 0 near .. 1 far
        return (float) (1 - (1 - QUIETEST) * t);
    }

    /** World sounds are scaled; the UI / announcer (MASTER), music, records and voice are not. */
    public static boolean scaled(SoundSource source) {
        return source != SoundSource.MASTER && source != SoundSource.MUSIC && source != SoundSource.RECORDS
            && source != SoundSource.VOICE;
    }
}
