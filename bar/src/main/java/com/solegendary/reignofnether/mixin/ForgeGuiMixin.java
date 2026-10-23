package com.solegendary.reignofnether.mixin;

import com.mojang.blaze3d.platform.Window;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.matchstart.MatchEndClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ForgeGui.class)
public class ForgeGuiMixin extends Gui {

    private static final int RTS_CHAT_LINES = 2;
    private static final double RTS_CHAT_SCALE = 0.75;
    // The unfocused feed fades after 6 s, not vanilla's 10: ChatComponent hides a line once (tickCount - addedTime) is 200,
    // so the feed is drawn "this many ticks late" and every line is that much older than it is.
    private static final int RTS_CHAT_AGE_SKIP = 80;

    public ForgeGuiMixin(Minecraft pMinecraft, ItemRenderer pItemRenderer) {
        super(pMinecraft, pItemRenderer);
    }

    // moves the chat history window up above bottom left hotkeys
    @Inject(
            method = "renderChat",
            at = @At("HEAD"),
            cancellable = true,
            remap=false
    )
    protected void renderChat(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        // victory/defeat moment: the chat log ("X has lost all their buildings...") covered the big title and the
        // results popup, so it's hidden until the popup is dismissed (still shown while typing)
        if (MatchEndClientEvents.shouldHideChat()) {
            ci.cancel();
            return;
        }
        if (!OrthoviewClientEvents.isEnabled())
            return;

        ci.cancel();

        this.minecraft.getProfiler().push("chat");
        Window window = this.minecraft.getWindow();
        CustomizeGuiOverlayEvent.Chat event = new CustomizeGuiOverlayEvent.Chat(window, guiGraphics, this.minecraft.getFrameTime(), 0, height - 40);
        MinecraftForge.EVENT_BUS.post(event);
        // RTS view, chat closed: the unfocused chat is a short feed, not a wall of text. At most RTS_CHAT_LINES lines
        // (a burst of announcements cannot stack up over the map: clip, 10 lines of "has fielded..." / "Boon received"
        // mid-left; the vanilla minimum height was still 4 lines), drawn at RTS_CHAT_SCALE (the clip of Oct 21 showed the
        // announcements in full-size type across a third of the screen). The player's own height setting is put back
        // straight after, and typing the full chat is untouched. The scale is a pose scale round the chat's own, not the
        // chatScale option: setting that re-wraps every line (rescaleChat), and this runs every frame.
        boolean slim = !(this.minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
        var heightOpt = this.minecraft.options.chatHeightUnfocused();
        Double keep = heightOpt.get();
        double own = this.chat.getScale();
        double extra = slim ? Math.min(1.0, RTS_CHAT_SCALE / own) : 1.0;   // on top of the player's own chat scale
        int bottomY = event.getPosY() + OrthoviewClientEvents.CHAT_Y_OFFSET;   // where the newest line's bottom edge sits
        // the feed rests on the command panel's top edge (the orders column is under its left half) instead of 7 px into it
        if (slim && HudClientEvents.bottomPanelTop != Integer.MAX_VALUE)
            bottomY = Math.min(bottomY, HudClientEvents.bottomPanelTop - 3);
        guiGraphics.pose().pushPose();
        if (slim)   // chat.render draws that bottom edge at (height - 40) in its own space, which the scale pulls up
            guiGraphics.pose().translate(event.getPosX(), bottomY - (event.getPosY()) * extra, 0.0);
        else
            guiGraphics.pose().translate(event.getPosX(), (double)(event.getPosY() - height + 40 + OrthoviewClientEvents.CHAT_Y_OFFSET) / own, 0.0);
        if (slim)
            guiGraphics.pose().scale((float) extra, (float) extra, 1.0f);
        int mouseX = Mth.floor(this.minecraft.mouseHandler.xpos() * (double)window.getGuiScaledWidth() / (double)window.getScreenWidth());
        int mouseY = Mth.floor(this.minecraft.mouseHandler.ypos() * (double)window.getGuiScaledHeight() / (double)window.getScreenHeight());
        boolean clipped = false;
        try {
            if (slim) {
                if (keep > 0.0)
                    heightOpt.set(0.0);
                double lineH = (int) (9.0 * (this.minecraft.options.chatLineSpacing().get() + 1.0)) * own * extra;
                guiGraphics.enableScissor(0, bottomY - (int) Math.ceil(lineH * RTS_CHAT_LINES), window.getGuiScaledWidth(), height);
                clipped = true;
            }
            this.chat.render(guiGraphics, slim ? this.tickCount + RTS_CHAT_AGE_SKIP : this.tickCount, mouseX, mouseY);
        } finally {
            if (clipped)
                guiGraphics.disableScissor();
            if (slim && keep > 0.0)
                heightOpt.set(keep);
        }
        guiGraphics.pose().popPose();
        this.minecraft.getProfiler().pop();
    }
}
