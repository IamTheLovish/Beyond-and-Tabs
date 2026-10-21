package com.solegendary.reignofnether.sounds;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;

/**
 * BAR-style volume by zoom (lovish Oct 21 02:28: "sound should get louder the more zoomed in you are and quieter the
 * more zoomed out ... kinda like bar"). The RTS camera is orthographic: zooming changes how much ground is shown, not
 * the camera's distance, so Minecraft's own distance falloff never changed - a fight sounded as loud from the whole-map
 * view as from up close. In the RTS view every world sound (blocks, units, weather...) is scaled by {@link ZoomVolumeCurve#factor}
 * of the zoom; the UI, the announcer (MASTER) and music are left alone. The maths lives in {@link ZoomVolumeCurve}
 * (no client imports) so the dedicated GameTest server can check it; this class is client-only.
 */
public final class ZoomVolume {
    private ZoomVolume() { }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent evt) {
        SoundInstance s = evt.getSound();
        if (s == null || s instanceof TickableSoundInstance || !ZoomVolumeCurve.scaled(s.getSource())
                || !com.solegendary.reignofnether.orthoview.OrthoviewClientEvents.isEnabled())
            return;
        float f = ZoomVolumeCurve.factor(com.solegendary.reignofnether.orthoview.OrthoviewClientEvents.getZoom());
        if (f < 0.999f)
            evt.setSound(new Scaled(s, f));
    }

    /** A sound played as it is, at a fraction of its volume. */
    record Scaled(SoundInstance inner, float factor) implements SoundInstance {
        @Override public ResourceLocation getLocation() { return inner.getLocation(); }
        @Override @Nullable public WeighedSoundEvents resolve(SoundManager m) { return inner.resolve(m); }
        @Override public Sound getSound() { return inner.getSound(); }
        @Override public SoundSource getSource() { return inner.getSource(); }
        @Override public boolean isLooping() { return inner.isLooping(); }
        @Override public boolean isRelative() { return inner.isRelative(); }
        @Override public int getDelay() { return inner.getDelay(); }
        @Override public float getVolume() { return inner.getVolume() * factor; }
        @Override public float getPitch() { return inner.getPitch(); }
        @Override public double getX() { return inner.getX(); }
        @Override public double getY() { return inner.getY(); }
        @Override public double getZ() { return inner.getZ(); }
        @Override public Attenuation getAttenuation() { return inner.getAttenuation(); }
        @Override public boolean canStartSilent() { return inner.canStartSilent(); }
        @Override public boolean canPlaySound() { return inner.canPlaySound(); }
    }
}
