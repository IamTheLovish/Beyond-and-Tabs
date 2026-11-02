package com.solegendary.reignofnether.config;

import com.solegendary.reignofnether.config.elements.ConfigSlider;
import com.solegendary.reignofnether.config.elements.Label;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Display settings (DisplaySettings; design/hud.md "Settings"): strategic icons (by zoom / always / never, the zoom
 * threshold, icon size), the health-bar rule, the fog of war style, the building ambience (design/lively_buildings.md), the valley mist (ambient/mist),
 * the building animation (design/moving_buildings.md) and the world look (postfx/WorldLookPass). Its own page, like Camera, because the main settings screen has no
 * spare row at GUI scale 4. Every control writes the client config at once and takes effect on the next frame
 * (the fog style on the next tick, with one chunk rebuild).
 */
public class DisplaySettingsScreen extends Screen {

    private final Screen parent;

    public DisplaySettingsScreen(Screen parent) {
        super(Component.literal("Display"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int colW = 150, gap = 6, rowH = 20, step = 23;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int y = 28;

        addRenderableOnly(new Label("Strategic icons").pos(left, y - 22).size(colW * 2 + gap, rowH));
        Button mode = addRenderableWidget(ReignOfNetherConfigScreen.button(left, y, colW, rowH, iconModeLabel(), b -> {
            ReignOfNetherClientConfigs.ICON_MODE.set((DisplaySettings.iconMode() + 1) % 3);
            b.setMessage(iconModeLabel());
        }));
        mode.setTooltip(Tooltip.create(Component.literal(
            "By zoom: units become icons past the zoom threshold. Always icons: icons at every zoom. Never icons: always the models.")));
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.ICON_ZOOM_THRESHOLD, 20, 70,
            v -> "Icon zoom threshold: " + v).pos(right, y).size(colW, rowH))
            .setTooltip(Tooltip.create(Component.literal(
                "How far you zoom out before units turn into icons (70 = default, lower = icons sooner)")));
        y += step;
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.ICON_SCALE, 50, 200,
            v -> "Icon size: " + v + "%").pos(left, y).size(colW, rowH));
        Button bars = addRenderableWidget(ReignOfNetherConfigScreen.button(right, y, colW, rowH, healthBarLabel(), b -> {
            ReignOfNetherClientConfigs.HEALTH_BAR_MODE.set((DisplaySettings.healthBarMode() + 1) % 3);
            b.setMessage(healthBarLabel());
        }));
        bars.setTooltip(Tooltip.create(Component.literal(
            "Always: a bar over every unit. Damaged (default): hurt, selected or hovered units (an enemy's bar drops once it has not been hit for 3 s). Selected only: selected or hovered units.")));
        y += step + 12;

        addRenderableOnly(new Label("Fog of war").pos(left, y - 4).size(colW * 2 + gap, rowH));
        y += 18;
        Button fog = addRenderableWidget(ReignOfNetherConfigScreen.button(left, y, colW, rowH, fogLabel(), b -> {
            ReignOfNetherClientConfigs.FOG_STYLE.set((DisplaySettings.fogStyle() + 1) % 3);
            b.setMessage(fogLabel());
        }));
        fog.setTooltip(Tooltip.create(Component.literal(
            "Subtle: light tint. Clear (default): the standard look. Strong: darker fog and hatched unexplored ground on the minimap.")));
        Button ambience = addRenderableWidget(ReignOfNetherConfigScreen.button(right, y, colW, rowH, ambienceLabel(), b -> {
            ReignOfNetherClientConfigs.BUILDING_AMBIENCE.set(!DisplaySettings.buildingAmbience());
            b.setMessage(ambienceLabel());
        }));
        ambience.setTooltip(Tooltip.create(Component.literal(
            "Chimney smoke, falling petals, soul flames, steam and sparks round capitols, energy buildings and labs near the camera.")));
        y += step;
        Button wind = addRenderableWidget(ReignOfNetherConfigScreen.button(left, y, colW, rowH, branchWindLabel(), b -> {
            // Off -> Near -> All -> Off
            ReignOfNetherClientConfigs.BRANCH_WIND.set((DisplaySettings.branchWind() + 1) % 3);
            b.setMessage(branchWindLabel());
        }));
        wind.setTooltip(Tooltip.create(Component.literal(
            "Tree limbs swaying in the wind. Near (default): limbs within 60 blocks of the camera sway, the rest stand still at no cost. All: every limb in view sways. Off: no sway.")));
        Button animation = addRenderableWidget(ReignOfNetherConfigScreen.button(right, y, colW, rowH, animationLabel(), b -> {
            // Off -> Low -> Full -> Off
            ReignOfNetherClientConfigs.BUILDING_ANIMATION.set((DisplaySettings.buildingAnimation() + 1) % 3);
            b.setMessage(animationLabel());
        }));
        animation.setTooltip(Tooltip.create(Component.literal(
            "Moving parts drawn on buildings: the metal extractors' sheave wheel, ore skip and owner pennant. Full (default; drops to Low by itself under 40 fps), Low (the nearest 24, simpler when small) or Off.")));
        y += step;
        Button mist = addRenderableWidget(ReignOfNetherConfigScreen.button(left, y, colW, rowH, mistLabel(), b -> {
            ReignOfNetherClientConfigs.VALLEY_MIST.set(!DisplaySettings.valleyMist());
            b.setMessage(mistLabel());
        }));
        mist.setTooltip(Tooltip.create(Component.literal(
            "Soft ground mist that pools in hollows, along water and under cliffs: thick at dawn, gone by late morning, back at dusk, drifting with the wind. None at strategic zoom or in rain.")));
        y += step + 12;

        addRenderableOnly(new Label("World look").pos(left, y - 4).size(colW * 2 + gap, rowH));
        y += 18;
        Button look = addRenderableWidget(ReignOfNetherConfigScreen.button(left, y, colW, rowH, worldLookLabel(), b -> {
            ReignOfNetherClientConfigs.WORLD_LOOK.set((DisplaySettings.worldLook() + 1) % 3);
            b.setMessage(worldLookLabel());
        }));
        look.setTooltip(Tooltip.create(Component.literal(
            "Smoothed edges, distance haze, a gentle film grade and a soft vignette over the world (never the HUD). Subtle (default) or Full. Switches itself off while a shader pack is on.")));
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.WORLD_LOOK_HAZE, 0, 200,
            v -> "Distance haze: " + v + "%").pos(right, y).size(colW, rowH))
            .setTooltip(Tooltip.create(Component.literal(
                "How strongly far ground fades into the sky colour (100% = default, 0% = no haze, the grade stays)")));

        addRenderableWidget(ReignOfNetherConfigScreen.button(right, this.height - 29, colW, 20,
            CommonComponents.GUI_DONE, b -> this.minecraft.setScreen(this.parent)));
    }

    private static Component iconModeLabel() {
        return Component.literal("Icons: " + DisplaySettings.ICON_MODE_NAMES[DisplaySettings.iconMode()]);
    }

    private static Component healthBarLabel() {
        return Component.literal("Health bars: " + DisplaySettings.HEALTH_BAR_MODE_NAMES[DisplaySettings.healthBarMode()]);
    }

    private static Component ambienceLabel() {
        return Component.literal("Building ambience: " + (DisplaySettings.buildingAmbience() ? "On" : "Off"));
    }

    private static Component mistLabel() {
        return Component.literal("Valley mist: " + (DisplaySettings.valleyMist() ? "On" : "Off"));
    }

    private static Component animationLabel() {
        return Component.literal("Building animation: " + DisplaySettings.BUILDING_ANIMATION_NAMES[DisplaySettings.buildingAnimation()]);
    }

    private static Component branchWindLabel() {
        return Component.literal("Branch wind: " + com.solegendary.reignofnether.environment.wind.BranchWindClient.MODE_NAMES[DisplaySettings.branchWind()]);
    }

    private static Component worldLookLabel() {
        return Component.literal("World look: " + DisplaySettings.WORLD_LOOK_NAMES[DisplaySettings.worldLook()]);
    }

    private static Component fogLabel() {
        return Component.literal("Fog style: " + DisplaySettings.FOG_STYLE_NAMES[DisplaySettings.fogStyle()]);
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
