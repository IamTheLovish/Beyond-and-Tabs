package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.resources.EconomyClientEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Contextual first-time tips in normal matches (design/onboarding.md): one line, at the moment it is useful, each tip
 * shown once ever (remembered in the client config), dismissible with a click, and off with one switch (Mod Settings ->
 * HUD..., or the Controls sheet). Never during the First Battle (it has its own prompts) and never more than one tip
 * per {@link #GAP_TICKS}.
 *
 * Cost: one check a second while a tip could still show; once every tip is seen (or tips are off) nothing runs. The
 * commander lookup scans the unit list once and then follows that one entity.
 */
public class OnboardingTips {

    static final int CHECK_TICKS = 20;
    static final int SHOW_TICKS = 20 * 14;
    static final int GAP_TICKS = 20 * 40;

    /** Tip ids, in the order they are checked each second. Each has onboarding.reignofnether.tip.&lt;id&gt; in lang. */
    enum Tip { CONTROLS, COMMANDER_ATTACKED, OVERFLOW, POSSESS, FORMATION, STATS_TABLE, CLEAN_VIEW }

    private static final Minecraft MC = Minecraft.getInstance();
    private static Set<String> seen = null;   // loaded lazily from the config
    private static Tip showing = null;
    private static String showingText = "";
    private static int showTicks = 0;
    private static int sinceLast = GAP_TICKS;
    private static int tick = 0;
    // state the triggers watch
    private static int commanderId = -1;
    private static float commanderHp = -1;
    private static int overflowSeconds = 0;
    private static int singleSelectedSeconds = 0;
    // the tip's box this frame, for the dismiss click
    private static int boxX, boxY, boxW, boxH;

    static boolean enabled() {
        // the eyes run (-Dbat.eyes) photographs the battle, not the onboarding: a tip banner sat over the army's
        // front row in every 08_box_select still on Oct 14. No eyes scene shoots a tip, so they are off there.
        if (com.solegendary.reignofnether.matchstart.EyesDirector.ENABLED)
            return false;
        return ReignOfNetherClientConfigs.ONBOARDING_TIPS.get();
    }

    private static Set<String> seen() {
        if (seen == null) {
            seen = new HashSet<>();
            for (String s : ReignOfNetherClientConfigs.ONBOARDING_SEEN_TIPS.get().split(","))
                if (!s.isBlank())
                    seen.add(s.trim());
        }
        return seen;
    }

    private static boolean allSeen() {
        for (Tip t : Tip.values())
            if (unseen(t))
                return false;
        return true;
    }

    /** Seen-list id of the camera controls hint (shown by OrthoviewClientEvents on the first RTS view, not a Tip). */
    static final String CAMERA_HINT_ID = "camera_hint";

    /** True once ever (and again after "Reset tips"): the first call marks the camera controls hint seen. */
    public static boolean takeCameraHint() {
        if (!seen().add(CAMERA_HINT_ID))
            return false;
        ReignOfNetherClientConfigs.ONBOARDING_SEEN_TIPS.set(String.join(",", seen()));
        return true;
    }

    static void markSeen(Tip t) {
        if (seen().add(t.name().toLowerCase()))
            ReignOfNetherClientConfigs.ONBOARDING_SEEN_TIPS.set(String.join(",", seen()));
    }

    /** "Reset tips" on the Controls sheet: every tip can show again. */
    public static void resetSeen() {
        seen().clear();
        ReignOfNetherClientConfigs.ONBOARDING_SEEN_TIPS.set("");
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (showing != null && --showTicks <= 0)
            showing = null;
        if (sinceLast < GAP_TICKS)
            sinceLast++;
        if (++tick % CHECK_TICKS != 0)
            return;
        if (!enabled() || allSeen() || MC.player == null || !OrthoviewClientEvents.isEnabled()
                || !PlayerClientEvents.isRTSPlayer() || OnboardingClient.isTutorialActive()) {
            return;
        }
        Tip t = trigger();
        if (t != null && showing == null && sinceLast >= GAP_TICKS)
            show(t);
    }

    private static boolean unseen(Tip t) {
        return !seen().contains(t.name().toLowerCase());
    }

    /** The first unseen tip whose moment has come (also advances the watchers, so call once a second). */
    private static Tip trigger() {
        String me = MC.player.getName().getString();
        long seconds = PlayerClientEvents.rtsGameTicks / 20;

        // commander under attack: its health dropped since the last look
        Tip found = null;
        if (unseen(Tip.COMMANDER_ATTACKED)) {
            LivingEntity com = ownCommander(me);
            if (com != null) {
                float hp = com.getHealth();
                if (commanderHp >= 0 && hp < commanderHp - 0.5f)
                    found = Tip.COMMANDER_ATTACKED;
                commanderHp = hp;
            }
        }
        // overflowing for five seconds running (the economy bar's own rule: >95% full with a positive net)
        if (unseen(Tip.OVERFLOW)) {
            Resources r = ResourcesClientEvents.getOwnResources();
            EconomyClientEvents.ClientEconomy eco = EconomyClientEvents.getEconomy(me);
            boolean over = r != null && ((r.ore > eco.metalStorage * 0.95f && eco.metalIncome > eco.metalExpense)
                || (r.wood > eco.energyStorage * 0.95f && eco.energyIncome > eco.energyExpense));
            overflowSeconds = over ? overflowSeconds + 1 : 0;
            if (found == null && overflowSeconds >= 5)
                found = Tip.OVERFLOW;
        }
        int selected = UnitClientEvents.getSelectedUnits().size();
        singleSelectedSeconds = selected == 1 ? singleSelectedSeconds + 1 : 0;
        if (found == null && unseen(Tip.CONTROLS) && seconds >= 8)
            found = Tip.CONTROLS;
        if (found == null && unseen(Tip.POSSESS) && singleSelectedSeconds >= 3)
            found = Tip.POSSESS;
        if (found == null && unseen(Tip.FORMATION) && selected >= 5)
            found = Tip.FORMATION;
        if (found == null && unseen(Tip.STATS_TABLE) && seconds >= 180)
            found = Tip.STATS_TABLE;
        if (found == null && unseen(Tip.CLEAN_VIEW) && seconds >= 480)
            found = Tip.CLEAN_VIEW;
        return found;
    }

    private static LivingEntity ownCommander(String me) {
        if (MC.level == null)
            return null;
        Entity e = commanderId >= 0 ? MC.level.getEntity(commanderId) : null;
        if (e instanceof LivingEntity le && le.isAlive())
            return le;
        commanderId = -1;
        commanderHp = -1;
        for (LivingEntity le : UnitClientEvents.getAllUnits())
            if (le instanceof Unit u && me.equals(u.getOwnerName()) && CommanderServerEvents.looksLikeCommander(le)) {
                commanderId = le.getId();
                return le;
            }
        return null;
    }

    static String text(Tip t) {
        String key = "onboarding.reignofnether.tip." + t.name().toLowerCase();
        return switch (t) {
            case COMMANDER_ATTACKED -> I18n.get(key, Keybindings.lastAlert.getDisplayName());
            case POSSESS -> I18n.get(key, Keybindings.toggleCamera.getDisplayName());
            case STATS_TABLE -> I18n.get(key, Keybindings.statsTable.getDisplayName());
            case CLEAN_VIEW -> I18n.get(key, Keybindings.cleanView.getDisplayName());
            default -> I18n.get(key);
        };
    }

    static void show(Tip t) {
        showing = t;
        showingText = text(t);
        showTicks = SHOW_TICKS;
        sinceLast = 0;
        markSeen(t);   // shown = seen: a tip never comes back, even if the game closes while it is up
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRender(ScreenEvent.Render.Post evt) {
        if (showing == null || !(evt.getScreen() instanceof TopdownGui) || !OrthoviewClientEvents.isEnabled())
            return;
        GuiGraphics g = evt.getGuiGraphics();
        Font font = MC.font;
        int sw = evt.getScreen().width, sh = evt.getScreen().height;
        String label = I18n.get("onboarding.reignofnether.tip.label");
        int textW = Math.min(font.width(showingText), sw - 80);
        boxW = font.width(label) + textW + 26;
        boxH = 16;
        boxX = (sw - boxW) / 2;
        int bottom = Math.min(HudClientEvents.bottomPanelTop, sh - 102);
        boxY = Math.max(40, bottom - boxH - 6);
        float alpha = Math.min(1f, showTicks / 20f);
        OnboardingUi.panel(g, boxX, boxY, boxX + boxW, boxY + boxH, alpha);
        g.drawString(font, label, boxX + 5, boxY + 4, OnboardingUi.withAlpha(OnboardingUi.GOLD_HOT, alpha), false);
        g.drawString(font, font.plainSubstrByWidth(showingText, textW), boxX + 9 + font.width(label), boxY + 4,
            OnboardingUi.withAlpha(OnboardingUi.TEXT, alpha), false);
        g.drawString(font, "x", boxX + boxW - 9, boxY + 4, OnboardingUi.withAlpha(OnboardingUi.TEXT_DIM, alpha), false);
    }

    /** A click on the tip dismisses it and goes no further (no selection box under it). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (showing == null || !(evt.getScreen() instanceof TopdownGui) || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        if (OnboardingUi.inside(evt.getMouseX(), evt.getMouseY(), boxX, boxY, boxW, boxH)) {
            showing = null;
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        showing = null;
        commanderId = -1;
        commanderHp = -1;
        overflowSeconds = 0;
        singleSelectedSeconds = 0;
        sinceLast = GAP_TICKS;
    }
}
