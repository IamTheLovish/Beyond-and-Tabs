package com.solegendary.reignofnether.config;

import com.solegendary.reignofnether.config.elements.ConfigCheckbox;
import com.solegendary.reignofnether.config.elements.ConfigSlider;
import com.solegendary.reignofnether.config.elements.Label;
import com.solegendary.reignofnether.hud.HudPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * HUD settings (design/hud.md, "Human-POV audit"): which panels start collapsed, how long alert toasts stay up and
 * how opaque the strategic view's plates are. Every panel can also be folded in game with the chevron in its corner
 * (the stats table with I, clean view with =); this page sets the same remembered state. Its own page because the
 * main settings screen has no spare row left at GUI scale 4.
 */
public class HudSettingsScreen extends Screen {

    private final Screen parent;

    public HudSettingsScreen(Screen parent) {
        super(Component.literal("HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // every in-game toggle already wrote the config (HudPanels.toggle), so the boxes read the current state
        int colW = 150, gap = 6, rowH = 20, step = 23;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int y = 28;

        addRenderableOnly(new Label("HUD (chevrons fold panels in game; I stats, = clean view)").pos(left, y - 22).size(colW * 2 + gap, rowH));

        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.HUD_STATS_OPEN,
                "Stats table open", "Stats table collapsed").pos(left, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.HUD_ALERTS_COLLAPSED,
                "Alerts as icons", "Alerts as text").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.HUD_CHEATSHEET_COLLAPSED,
                "Controls sheet: ? button", "Controls sheet shown").pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.HUD_UNITCARD_COMPACT,
                "Unit card: one line", "Unit card: full").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.HUD_ECONOMY_COMPACT,
                "Economy bar: compact", "Economy bar: full").pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.HUD_COMMAND_COMPACT,
                "Command card: compact", "Command card: full").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.HUD_OBSERVER_BAR,
                "Observer bar on", "Observer bar off").pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.HUD_TOAST_SECONDS, 1, 10,
                v -> "Alert toasts: " + v + " s").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.HUD_PLATE_OPACITY, 10, 100,
                v -> "Strategic plates: " + v + "%").pos(right, y).size(colW, rowH));
        y += step;
        // onboarding (design/onboarding.md): the one-line first-time tips in matches
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.ONBOARDING_TIPS,
                "First-time tips on", "First-time tips off").pos(left, y).size(colW, rowH));
        // the hero fights by itself while you are not possessing it (HeroBot); the server hears of a change at once
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.HERO_AI,
                "Hero AI on", "Hero AI off") {
            @Override
            public void onPress() {
                super.onPress();
                com.solegendary.reignofnether.hero.HeroServerboundPacket.sendHeroAi(this.selected());
            }
        }.pos(right, y).size(colW, rowH));

        addRenderableWidget(ReignOfNetherConfigScreen.button(left, this.height - 29, colW, 20,
                Component.literal("HUD keys..."),
                b -> this.minecraft.setScreen(new KeyBindsScreen(this, this.minecraft.options))));
        addRenderableWidget(ReignOfNetherConfigScreen.button(right, this.height - 29, colW, 20,
                CommonComponents.GUI_DONE, b -> close()));
    }

    private void close() {
        HudPanels.reload();   // pick up what the boxes changed
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderDirtBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
