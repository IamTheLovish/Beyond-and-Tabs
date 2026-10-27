package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.hud.SplashArt;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The controls reference (design/onboarding.md): every control on one scrolling sheet, with the player's ACTUAL keys -
 * read from keybinds/Keybindings each time the sheet opens, so a rebind shows here (marked "changed") and "Change
 * keys..." goes straight to the vanilla Controls page to rebind. Mouse controls that have no binding are listed too,
 * with the alternatives a Mac mouse or a trackpad needs (no middle button: WASD / edges pan, Alt + WASD rotates).
 *
 * Reachable from the title screen (the "?" beside Skirmish), the First Battle screen and the Match Menu (Controls).
 * Also carries the first-time tips switch.
 */
public class ControlsReferenceScreen extends Screen {

    private record Row(String label, String keys, boolean header, boolean changed) { }

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private double scroll = 0;
    private Button tipsButton;

    public ControlsReferenceScreen(Screen parent) {
        super(Component.translatable("onboarding.reignofnether.controls.title"));
        this.parent = parent;
    }

    private static String t(String key, Object... args) {
        return I18n.get("onboarding.reignofnether.controls." + key, args);
    }

    private static String k(Keybinding b) {
        String s = b.getDisplayName();
        return s == null || s.isEmpty() ? I18n.get("onboarding.reignofnether.controls.unbound") : s;
    }

    private static boolean changed(Keybinding b) {
        return b.getTranslationKey() != null && b.getKey() != b.getDefaultKey();
    }

    private void header(String key) {
        rows.add(new Row(t(key), "", true, false));
    }

    private void mouse(String key, String keys) {
        rows.add(new Row(t(key), keys, false, false));
    }

    private void bind(String labelKey, Keybinding b) {
        rows.add(new Row(t(labelKey), k(b), false, changed(b)));
    }

    /** A run of slot bindings on one line ("Q Z E R ..."), marked changed if any of them is. */
    private void slots(String labelKey, Keybinding... bs) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (Keybinding b : bs) {
            String s = b.getCurrentLabel();
            if (sb.length() > 0)
                sb.append(' ');
            sb.append(s.isEmpty() ? "-" : s);
            any |= changed(b);
        }
        rows.add(new Row(t(labelKey), sb.toString(), false, any));
    }

    private void buildRows() {
        rows.clear();
        String alt = t("alt");
        header("h_essentials");
        bind("possess", Keybindings.toggleCamera);
        mouse("possess_hover", t("ctrl") + " + " + k(Keybindings.toggleCamera));
        mouse("possess_hop", k(Keybindings.tab) + " " + t("while_possessing"));
        bind("stats", Keybindings.statsTable);
        bind("clean", Keybindings.cleanView);
        bind("hide_hud", Keybindings.getFnum(1));
        bind("last_alert", Keybindings.lastAlert);
        bind("overview", Keybindings.overview);
        bind("idle_worker", Keybindings.idleWorker);
        bind("select_army", Keybindings.selectArmy);
        mouse("select_army_ctrl_w", t("ctrl") + " + W");   // HudClientEvents: hardcoded Ctrl+W, BAR's army key
        bind("return_resources", Keybindings.returnResources);

        header("h_camera");
        mouse("zoom", t("wheel") + "  /  " + k(Keybindings.zoomIn) + " " + k(Keybindings.zoomOut));
        mouse("pan", t("middle_drag") + "  /  " + k(Keybindings.panUp) + k(Keybindings.panLeft) + k(Keybindings.panDown)
            + k(Keybindings.panRight) + "  /  " + t("edges"));
        mouse("rotate", alt + " + " + t("middle_drag") + "  /  " + alt + " + " + k(Keybindings.panLeft) + k(Keybindings.panRight));
        mouse("tilt", t("ctrl") + " + " + t("wheel"));
        bind("reset", Keybindings.reset);
        bind("toggle_camera", Keybindings.toggleCamera);
        mouse("mac", t("mac_keys"));

        header("h_select");
        mouse("select", t("left_click"));
        mouse("box", t("left_drag"));
        // Smart Select (SmartSelect, after BAR): the box's modifiers, read while the box is dragged
        mouse("box_shift", t("shift") + " + " + t("left_drag"));
        mouse("box_ctrl", t("ctrl") + " + " + t("left_drag"));
        mouse("box_alt", alt + " + " + t("left_drag"));
        mouse("box_z", "Z + " + t("left_drag"));
        mouse("box_space", t("space") + " + " + t("left_drag"));
        mouse("double", t("double_click"));
        mouse("add", t("shift") + " + " + t("left_click"));
        mouse("remove", t("ctrl") + " + " + t("left_click"));
        bind("deselect", Keybindings.deselect);
        bind("cycle", Keybindings.tab);
        mouse("groups", t("ctrl") + " + 0-9  /  0-9");

        header("h_orders");
        mouse("move", t("right_click"));
        mouse("formation", t("right_drag"));
        mouse("attack_move", k(Keybindings.attack) + ", " + t("then_click"));
        bind("attack", Keybindings.attack);
        bind("fight", Keybindings.fight);
        bind("stop", Keybindings.stop);
        bind("move_key", Keybindings.move);
        bind("hold", Keybindings.hold);
        bind("patrol", Keybindings.patrol);
        bind("guard", Keybindings.guard);
        mouse("queue", t("shift") + " + " + t("any_order"));
        // BAR queue editing (unit/command/QueueEditKeys): Space (or Ctrl) + order inserts, N skips, Ctrl+N cancels last
        mouse("queue_insert", k(Keybindings.queueInsert) + " / " + t("ctrl") + " + " + t("any_order"));
        bind("queue_skip", Keybindings.queueSkip);
        mouse("queue_cancel_last", t("ctrl") + " + " + k(Keybindings.queueSkip));
        mouse("area", t("ctrl") + " + " + t("right_drag"));

        header("h_build");
        bind("build", Keybindings.build);
        bind("build_metal", Keybindings.buildMetal);
        bind("build_energy", Keybindings.buildEnergy);
        bind("build_defence", Keybindings.buildDefence);
        bind("build_factory", Keybindings.buildFactory);
        mouse("area_mex", t("left_drag"));
        mouse("place", t("left_click") + "  /  " + t("shift") + " + " + t("left_drag"));
        bind("rotate_ccw", Keybindings.rotateBuildingCCW);
        bind("rotate_cw", Keybindings.rotateBuildingCW);
        bind("cancel", Keybindings.cancelBuild);
        slots("ability_slots", Keybindings.abilitySlot1, Keybindings.abilitySlot2, Keybindings.abilitySlot3,
            Keybindings.abilitySlot4, Keybindings.abilitySlot5, Keybindings.abilitySlot6, Keybindings.abilitySlot7,
            Keybindings.abilitySlot8, Keybindings.abilitySlot9, Keybindings.abilitySlot10);
        slots("menu_slots", Keybindings.hotkey1, Keybindings.hotkey2, Keybindings.hotkey3, Keybindings.hotkey4,
            Keybindings.hotkey5, Keybindings.hotkey6, Keybindings.hotkey7, Keybindings.hotkey8, Keybindings.hotkey9,
            Keybindings.hotkey10, Keybindings.hotkey11, Keybindings.hotkey12);
        bind("overcharge", Keybindings.overcharge);

        header("h_other");
        bind("minimap", Keybindings.minimapToggle);
        bind("chat", Keybindings.chat);
        bind("command_bar", Keybindings.commandBar);
        mouse("menu", "Esc");
    }

    private int panelW() {
        return Math.min(440, width - 16);
    }

    private int listTop() {
        return 34;
    }

    private int listBottom() {
        return height - 34;
    }

    private int contentHeight() {
        int h = 0;
        for (Row r : rows)
            h += r.header ? 16 : 11;
        return h;
    }

    @Override
    protected void init() {
        buildRows();
        int bw = Math.min(104, (panelW() - 12) / 4);
        int total = bw * 4 + 12;
        int x = (width - total) / 2, y = height - 26;
        Button keys = addRenderableWidget(Button.builder(Component.translatable("onboarding.reignofnether.controls.change"),
            b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(x, y, bw, 20).build());
        keys.setTooltip(Tooltip.create(Component.translatable("onboarding.reignofnether.controls.change.tooltip")));
        tipsButton = addRenderableWidget(Button.builder(tipsLabel(), b -> {
            ReignOfNetherClientConfigs.ONBOARDING_TIPS.set(!ReignOfNetherClientConfigs.ONBOARDING_TIPS.get());
            tipsButton.setMessage(tipsLabel());
        }).bounds(x + bw + 4, y, bw, 20).build());
        tipsButton.setTooltip(Tooltip.create(Component.translatable("onboarding.reignofnether.tips.toggle.tooltip")));
        Button reset = addRenderableWidget(Button.builder(Component.translatable("onboarding.reignofnether.tips.reset"), b -> {
            OnboardingTips.resetSeen();
            b.active = false;
        }).bounds(x + (bw + 4) * 2, y, bw, 20).build());
        reset.setTooltip(Tooltip.create(Component.translatable("onboarding.reignofnether.tips.reset.tooltip")));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(x + (bw + 4) * 3, y, bw, 20).build());
    }

    private static Component tipsLabel() {
        return Component.translatable(ReignOfNetherClientConfigs.ONBOARDING_TIPS.get()
            ? "onboarding.reignofnether.tips.on" : "onboarding.reignofnether.tips.off");
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int max = Math.max(0, contentHeight() - (listBottom() - listTop()));
        scroll = Mth.clamp(scroll - delta * 22, 0, max);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the cathedral backdrop from the title screen; in a match a plain dimmed world, as other menus
        if (minecraft.level == null && SplashArt.ensureLoaded()) {
            g.flush();
            SplashArt.renderScene(g.pose().last().pose(), width, height, mouseX, mouseY, 1f);
        } else {
            renderBackground(g);
        }
        int pw = panelW();
        int x0 = (width - pw) / 2, x1 = x0 + pw;
        OnboardingUi.panel(g, x0, 6, x1, height - 30, 1f);
        g.drawCenteredString(font, title, width / 2, 12, OnboardingUi.GOLD_HOT);
        String sub = t("subtitle");
        g.drawCenteredString(font, sub, width / 2, 22, OnboardingUi.TEXT_DIM);

        int top = listTop(), bottom = listBottom();
        g.enableScissor(x0 + 1, top, x1 - 1, bottom);
        int y = top - (int) scroll;
        int keyColX = x0 + pw / 2 + 10;
        for (Row r : rows) {
            int rh = r.header ? 16 : 11;
            if (y + rh >= top && y <= bottom) {
                if (r.header) {
                    g.drawString(font, r.label, x0 + 8, y + 5, OnboardingUi.GOLD_HOT, false);
                    g.fill(x0 + 8, y + 14, x1 - 8, y + 15, OnboardingUi.GOLD_DIM);
                } else {
                    g.drawString(font, font.plainSubstrByWidth(r.label, keyColX - x0 - 14), x0 + 12, y + 1, OnboardingUi.TEXT, false);
                    String keys = r.changed ? r.keys + "  " + t("changed") : r.keys;
                    g.drawString(font, font.plainSubstrByWidth(keys, x1 - keyColX - 8), keyColX, y + 1,
                        r.changed ? OnboardingUi.GOOD : OnboardingUi.TEXT_HOT, false);
                }
            }
            y += rh;
        }
        g.disableScissor();
        // scroll thumb
        int content = contentHeight(), view = bottom - top;
        if (content > view) {
            int th = Math.max(12, view * view / content);
            int ty = top + (int) ((view - th) * (scroll / (content - view)));
            g.fill(x1 - 5, top, x1 - 3, bottom, 0x40FFFFFF);
            g.fill(x1 - 5, ty, x1 - 3, ty + th, OnboardingUi.GOLD);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (SplashArt.ensureLoaded())
            SplashArt.restyleButtons(g, font, children(), 1f);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
