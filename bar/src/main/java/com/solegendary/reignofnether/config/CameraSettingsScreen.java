package com.solegendary.reignofnether.config;

import com.solegendary.reignofnether.config.elements.ConfigCheckbox;
import com.solegendary.reignofnether.config.elements.ConfigSlider;
import com.solegendary.reignofnether.config.elements.Label;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Camera settings (BAR Spring camera, design/camera.md): speeds, edge scroll, middle-drag, smoothing and the
 * zoom-driven tilt. Its own page because the main settings screen has no spare row left at GUI scale 4.
 * Every control writes the client config at once; the camera reads it each frame.
 */
public class CameraSettingsScreen extends Screen {

    private final Screen parent;

    public CameraSettingsScreen(Screen parent) {
        super(Component.literal("Camera"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int colW = 150, gap = 6, rowH = 20, step = 23;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int y = 28;

        addRenderableOnly(new Label("Camera (BAR Spring camera)").pos(left, y - 22).size(colW * 2 + gap, rowH));

        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.CAMERA_ZOOM_SPEED, 1, 30,
                v -> "Zoom speed: " + v).pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.CAMERA_SENSITIVITY, 1, 30,
                v -> "Pan speed: " + v).pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.CAMERA_MMB_SPEED, 1, 30,
                v -> "Middle-drag speed: " + v).pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.CAMERA_MMB_INVERT,
                "Middle-drag inverted", "Middle-drag grabs map").pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.CAMERA_EDGE_SCROLL,
                "Edge scroll on", "Edge scroll off").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.CAMERA_EDGE_SPEED, 1, 30,
                v -> "Edge scroll speed: " + v).pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.CAMERA_SMOOTHING, 0, 30,
                v -> v == 0 ? "Smoothing: off" : "Smoothing: " + v).pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.CAMERA_ZOOM_OUT_FROM_CURSOR,
                "Zoom out from cursor", "Zoom out from centre").pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.CAMERA_TILT_FOLLOWS_ZOOM,
                "Tilt follows zoom", "Fixed tilt").pos(left, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.CAMERA_PITCH_CLOSE, 12, 90,
                v -> "Tilt zoomed in: " + v + "°").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.CAMERA_PITCH_FAR, 12, 90,
                v -> "Tilt zoomed out: " + v + "°").pos(right, y).size(colW, rowH));

        // every camera key is a normal Controls binding ("Beyond and Tabs - Camera")
        addRenderableWidget(ReignOfNetherConfigScreen.button(left, this.height - 29, colW, 20,
                Component.literal("Camera keys..."),
                b -> this.minecraft.setScreen(new KeyBindsScreen(this, this.minecraft.options))));
        addRenderableWidget(ReignOfNetherConfigScreen.button(right, this.height - 29, colW, 20,
                CommonComponents.GUI_DONE, b -> this.minecraft.setScreen(this.parent)));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderDirtBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
