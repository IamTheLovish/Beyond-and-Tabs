package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.SplashArt;
import com.solegendary.reignofnether.matchstart.FactionPicker;
import com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Title screen -> First Battle: what the tutorial is (one short paragraph), which faction to learn with - with that
 * faction's info card, so the choice is informed - and Start, Resume (when a tutorial was left half-way), Controls and
 * Back. The Sunforged Kingdom is preselected and marked as the gentlest first faction.
 */
public class FirstBattleScreen extends Screen {

    private final Screen parent;
    private final List<Faction> factions = new ArrayList<>();
    private int index = 0;
    private final List<FormattedCharSequence> intro = new ArrayList<>();
    private int introWidth = -1;

    public FirstBattleScreen(Screen parent) {
        super(Component.translatable("onboarding.reignofnether.screen.title"));
        this.parent = parent;
        for (FactionPicker.Entry e : FactionPicker.skirmishEntries())
            if (e.selectable() && e.faction() != Factions.RANDOM)
                factions.add(e.faction());
        for (int i = 0; i < factions.size(); i++)
            if (factions.get(i).equals(Factions.VILLAGERS))
                index = i;
    }

    private Faction faction() {
        return factions.isEmpty() ? Factions.VILLAGERS : factions.get(index);
    }

    private int columnW() {
        return Math.min(200, (width - 24) / 2);
    }

    @Override
    protected void init() {
        int col = columnW();
        int left = width / 2 - col - 6;
        int y = height - 28;
        // faction cycler above the buttons, on the left column
        addRenderableWidget(Button.builder(Component.literal("<"), b -> cycle(-1)).bounds(left, y - 26, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> cycle(1)).bounds(left + col - 20, y - 26, 20, 20).build());

        int bw = (col - 4) / 2;
        Button start = addRenderableWidget(Button.builder(Component.translatable("onboarding.reignofnether.screen.start"), b -> {
            int code = SkirmishServerboundPacket.codeOf(faction());
            OnboardingClient.launch(code < 0 ? 0 : code);
        }).bounds(left, y, bw, 20).build());
        start.setTooltip(Tooltip.create(Component.translatable("onboarding.reignofnether.screen.start.tooltip")));
        Button resume = addRenderableWidget(Button.builder(Component.translatable("onboarding.reignofnether.screen.resume"),
            b -> OnboardingClient.resume(this)).bounds(left + bw + 4, y, bw, 20).build());
        resume.active = OnboardingClient.resumableWorld() != null;
        resume.setTooltip(Tooltip.create(Component.translatable(resume.active
            ? "onboarding.reignofnether.screen.resume.tooltip" : "onboarding.reignofnether.screen.resume.none")));
        int right = width / 2 + 6;
        addRenderableWidget(Button.builder(Component.translatable("onboarding.reignofnether.screen.controls"),
            b -> minecraft.setScreen(new ControlsReferenceScreen(this))).bounds(right, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
            .bounds(right + bw + 4, y, bw, 20).build());
    }

    private void cycle(int d) {
        if (factions.isEmpty())
            return;
        index = (index + d + factions.size()) % factions.size();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (SplashArt.ensureLoaded()) {
            g.flush();
            SplashArt.renderScene(g.pose().last().pose(), width, height, mouseX, mouseY, 1f);
        } else {
            renderBackground(g);
        }
        int col = columnW();
        int left = width / 2 - col - 6, right = width / 2 + 6;
        int top = 10;
        int bottom = height - 60;

        // left column: what the First Battle is, and the faction choice
        OnboardingUi.panel(g, left - 4, top, left + col + 4, bottom, 1f);
        g.drawString(font, title, left + 4, top + 6, OnboardingUi.GOLD_HOT, false);
        OnboardingUi.rule(g, left + 4, left + col - 4, top + 18);
        if (introWidth != col - 8) {
            intro.clear();
            intro.addAll(font.split(Component.translatable("onboarding.reignofnether.screen.intro"), col - 8));
            introWidth = col - 8;
        }
        int y = top + 24;
        for (FormattedCharSequence line : intro) {
            if (y > bottom - 34)
                break;
            g.drawString(font, line, left + 4, y, OnboardingUi.TEXT, false);
            y += 10;
        }
        // faction row (between the < > buttons, which sit just below the panel)
        Faction f = faction();
        String name = com.solegendary.reignofnether.util.MiscUtil.getFactionName(f);
        int rowY = height - 54 + 6;
        FactionPicker.renderIcon(g, f, left + 26, rowY - 4, 16, false);
        g.drawString(font, font.plainSubstrByWidth(name, col - 70), left + 46, rowY, OnboardingUi.TEXT_HOT, false);
        if (f.equals(Factions.VILLAGERS))
            g.drawString(font, I18n.get("onboarding.reignofnether.screen.recommended"), left + 4, bottom - 12,
                OnboardingUi.GOOD, false);

        // right column: the faction's card
        FactionInfoCard.renderFitted(g, font, f, right, top, col, bottom - top);

        super.render(g, mouseX, mouseY, partialTick);
        if (SplashArt.ensureLoaded())
            SplashArt.restyleButtons(g, font, children(), 1f);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
