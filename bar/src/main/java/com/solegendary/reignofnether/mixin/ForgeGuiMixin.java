package com.solegendary.reignofnether.mixin;

import com.mojang.blaze3d.platform.Window;
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
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(event.getPosX(), (double)(event.getPosY() - height + 40 + OrthoviewClientEvents.CHAT_Y_OFFSET) / this.chat.getScale(), 0.0);
        int mouseX = Mth.floor(this.minecraft.mouseHandler.xpos() * (double)window.getGuiScaledWidth() / (double)window.getScreenWidth());
        int mouseY = Mth.floor(this.minecraft.mouseHandler.ypos() * (double)window.getGuiScaledHeight() / (double)window.getScreenHeight());
        // RTS view: the unfocused chat shows at most RTS_CHAT_LINES lines, so a burst of announcements cannot stack
        // up over the map (clip: 10 lines of "has fielded..." / "Boon received" mid-left). Height 0 = the 40 px
        // minimum = 4 lines; the player's own setting is put back straight after, and typing the full chat is untouched.
        var heightOpt = this.minecraft.options.chatHeightUnfocused();
        Double keep = heightOpt.get();
        boolean shrink = !(this.minecraft.screen instanceof net.minecraft.client.gui.screens.ChatScreen) && keep > 0.0;
        if (shrink)
            heightOpt.set(0.0);
        try {
            this.chat.render(guiGraphics, this.tickCount, mouseX, mouseY);
        } finally {
            if (shrink)
                heightOpt.set(keep);
        }
        guiGraphics.pose().popPose();
        this.minecraft.getProfiler().pop();
    }
}