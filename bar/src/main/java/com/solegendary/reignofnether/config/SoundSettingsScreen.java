package com.solegendary.reignofnether.config;

import com.solegendary.reignofnether.config.elements.ConfigCheckbox;
import com.solegendary.reignofnether.config.elements.ConfigSlider;
import com.solegendary.reignofnether.config.elements.Label;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Sound settings (design/audio.md): one volume per category of the mod's sound effects - Units (weapons, hits,
 * deaths), Buildings (construction, completion, destruction, gates, traps, reclaim), Abilities (heroes, possession,
 * beasts), Announcer (the voice, game-ender moments and stingers; the same slider as the main page's "Voice") and
 * UI (lobby, start-position phase) - plus the match-start fanfare toggle. Vanilla's own sliders (Master, Music,
 * Friendly/Hostile creatures, Blocks, Players) still apply on top. Its own page: the main screen has no spare row.
 */
public class SoundSettingsScreen extends Screen {

    private final Screen parent;

    public SoundSettingsScreen(Screen parent) {
        super(Component.literal("Sound"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int colW = 150, gap = 6, rowH = 20, step = 23;
        int left = this.width / 2 - colW - gap / 2;
        int right = this.width / 2 + gap / 2;
        int y = 28;

        addRenderableOnly(new Label("Sound effects (all original; big fights stay calm)").pos(left, y - 22).size(colW * 2 + gap, rowH));

        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.SFX_UNITS_VOLUME, 0, 100,
                v -> "Units: " + v + "%").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.SFX_BUILDINGS_VOLUME, 0, 100,
                v -> "Buildings: " + v + "%").pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.SFX_ABILITIES_VOLUME, 0, 100,
                v -> "Abilities: " + v + "%").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.VOICE_VOLUME, 0, 100,
                v -> "Announcer: " + v + "%").pos(right, y).size(colW, rowH));
        y += step;
        addRenderableWidget(new ConfigSlider(ReignOfNetherClientConfigs.SFX_UI_VOLUME, 0, 100,
                v -> "Interface: " + v + "%").pos(left, y).size(colW, rowH));
        addRenderableWidget(new ConfigCheckbox(ReignOfNetherClientConfigs.MUSIC_STINGERS,
                "Match fanfare on", "Match fanfare off").pos(right, y).size(colW, rowH));

        addRenderableWidget(ReignOfNetherConfigScreen.button(this.width / 2 - 75, this.height - 29, 150, 20,
                CommonComponents.GUI_DONE, b -> Minecraft.getInstance().setScreen(this.parent)));
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
