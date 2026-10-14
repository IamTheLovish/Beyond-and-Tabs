package com.solegendary.reignofnether.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The big "You are victorious!" / "You have been defeated" banner, drawn above every RTS panel.
 *
 * It used to be a vanilla title (Gui.setTitle), which Minecraft draws inside Gui.render - before the TopdownGui
 * screen whose ScreenEvent.Render.Post pass carries the whole RTS HUD. Clip study Oct 13 (b1; 21-41-11 1:02): the
 * stats table drew straight over "You are vict[orious!]". So the banner is ours now: the same look and timing as
 * the vanilla title (10 ticks in, 70 up, 20 out, 4x text), but drawn from the lowest-priority Render.Post listener
 * on the RTS screen, i.e. after every panel, and from RenderGuiEvent.Post when no screen is open (the first-person
 * view). While another screen is up (pause menu, results) the clock pauses, so the banner is never missed.
 */
public class MatchBanner {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int FADE_IN = 10, STAY = 70, FADE_OUT = 20;
    private static final int TOTAL = FADE_IN + STAY + FADE_OUT;

    private static Component text = null;
    private static int ticksLeft = 0;
    private static int width = 0;

    public static void show(Component component) {
        text = component;
        ticksLeft = TOTAL;
        width = MC.font.width(component);
    }

    public static boolean isShowing() {
        return text != null && ticksLeft > 0;
    }

    private static boolean rtsScreen() {
        return MC.screen instanceof TopdownGui;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || ticksLeft <= 0)
            return;
        if (MC.screen == null || rtsScreen())
            ticksLeft--;
        if (ticksLeft <= 0)
            text = null;
    }

    // after every HUD panel: this listener runs last among the RTS screen's Render.Post handlers
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (evt.getScreen() instanceof TopdownGui)
            render(evt.getGuiGraphics(), evt.getPartialTick());
    }

    // no screen at all (first-person view): nothing of ours draws later, so the vanilla slot is fine
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post evt) {
        if (MC.screen == null)
            render(evt.getGuiGraphics(), evt.getPartialTick());
    }

    private static void render(GuiGraphics gg, float partialTick) {
        if (!isShowing() || MC.level == null)
            return;
        float t = ticksLeft - partialTick;
        int alpha = 255;
        if (ticksLeft > FADE_OUT + STAY)
            alpha = (int) ((TOTAL - t) * 255f / FADE_IN);
        else if (ticksLeft <= FADE_OUT)
            alpha = (int) (t * 255f / FADE_OUT);
        alpha = Mth.clamp(alpha, 0, 255);
        if (alpha <= 8)
            return;
        int sw = MC.getWindow().getGuiScaledWidth();
        int sh = MC.getWindow().getGuiScaledHeight();
        gg.pose().pushPose();
        gg.pose().translate(sw / 2f, sh / 2f, 1000f);   // above the panels' own z lifts (tooltips sit at 3000)
        RenderSystem.enableBlend();
        gg.pose().pushPose();
        gg.pose().scale(4f, 4f, 1f);
        gg.drawString(MC.font, text, -width / 2, -10, 0xFFFFFF | (alpha << 24), true);
        gg.pose().popPose();
        gg.pose().popPose();
    }
}
