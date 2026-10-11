package com.solegendary.reignofnether.config.elements;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.function.IntFunction;

/**
 * A vanilla slider bound to an integer client-config value (camera speeds, smoothing). The value is written to the
 * config on every step, so the camera picks it up on the next frame - no Apply button, like BAR's options panel.
 */
public class ConfigSlider extends AbstractSliderButton {

    private final ForgeConfigSpec.ConfigValue<Integer> configValue;
    private final int min;
    private final int max;
    private final IntFunction<String> label;

    public ConfigSlider(ForgeConfigSpec.ConfigValue<Integer> configValue, int min, int max, IntFunction<String> label) {
        super(0, 0, 1, 1, Component.empty(), toSlider(configValue.get(), min, max));
        this.configValue = configValue;
        this.min = min;
        this.max = max;
        this.label = label;
        updateMessage();
    }

    private static double toSlider(int v, int min, int max) {
        return max <= min ? 0 : Mth.clamp((v - min) / (double) (max - min), 0, 1);
    }

    private int current() {
        return min + (int) Math.round(this.value * (max - min));
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(label.apply(current())));
    }

    @Override
    protected void applyValue() {
        int v = current();
        if (!configValue.get().equals(v))
            configValue.set(v);
    }

    public ConfigSlider pos(int x, int y) {
        super.setPosition(x, y);
        return this;
    }

    public ConfigSlider size(int w, int h) {
        super.setWidth(w);
        super.setHeight(h);
        return this;
    }
}
