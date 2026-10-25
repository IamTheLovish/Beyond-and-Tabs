package com.solegendary.reignofnether.hud.buttons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.healthbars.HealthBarClientEvents;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Class for creating buttons that consist of an icon inside of a frame which is selectable
 * All functionality that occurs on click/hover/etc. is enforced by HudClientEvents
 */

public class Button {

    public String name;
    public int x; // top left
    public int y;
    public int iconSize;
    public int iconFrameSize;
    public int iconSelectedFrameSize;
    public static int DEFAULT_ICON_SIZE = 14;
    public static int DEFAULT_ICON_FRAME_SIZE = 22;
    public int tooltipOffsetY = 0;
    public int imageSize = DEFAULT_ICON_SIZE;
    public static final int itemIconSize = DEFAULT_ICON_SIZE;
    public int innerIconSizeModifier = 0;
    public String playerNameForHeadIcon = "";
    public int bgColour = 0x64000000;
    public Supplier<String> bottomLeftText = null;
    public int bottomLeftTextColor = 0xFFFFFF;

    public ResourceLocation iconResource;
    public ResourceLocation bgIconResource = null; // for rendering a background icon (eg. for mounted unit passengers)
    public ResourceLocation frameResource = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame.png");

    public ItemStack iconItem = null;
    public float iconItemScale = 1.0f;

    public Keybinding hotkey = null; // for action/ability buttons
    public LivingEntity entity = null; // for selected unit buttons
    public BuildingPlacement building = null; // for selected building buttons

    /** https://stackoverflow.com/questions/29945627/java-8-lambda-void-argument
     * Supplier       ()    -> x
     * Consumer       x     -> ()
     * Runnable       ()    -> ()
     * Predicate      x     -> boolean
     */
    public Supplier<Boolean> isSelected; // controls selected frame rendering
    public Supplier<Boolean> isHidden; // special highlighting for an on-state (eg. auto-cast/auto-producing)
    public Supplier<Boolean> isEnabled; // is the button allowed to be used right now? (eg. off cooldown)
    public Runnable onLeftClick;
    public Runnable onRightClick;
    public Runnable onLeftClickRelease = null;
    public List<FormattedCharSequence> tooltipLines;
    public boolean lightUpOnHover = true;

    public Supplier<Boolean> isFlashing = () -> false;
    /**
     * BAR state toggles (hold position, repeat, fire state): while true the frame is lit - an amber glow inside and a
     * bright edge - so an active toggle reads at a glance, unlike isSelected's transient "being pressed" frame.
     */
    public Supplier<Boolean> isActive = null;
    public static final int ACTIVE_GLOW = 0x48FFC040, ACTIVE_EDGE = 0xFFFFC040;

    // used for cooldown indication, productionItem progress, etc.
    // @ 0.0, appears clear and normal
    // @ 0.5, bottom half is greyed out
    // @ 1.0, whole button is greyed out
    public float greyPercent = 0.0f;

    public Supplier<Float> getGreyPercent = null;
    public boolean greyWhenDisabled = true;
    public boolean greyInverted = false;
    public boolean showSelectedFrameWhenDisabled = false;

    protected Minecraft MC = Minecraft.getInstance();

    // constructor for ability/action/production buttons
    public Button(String name, int iconSize, ResourceLocation iconRl, @Nullable Keybinding hotkey, Supplier<Boolean> isSelected,
                  Supplier<Boolean> isHidden, Supplier<Boolean> isEnabled, @Nullable Runnable onLeftClick,
                  @Nullable Runnable onRightClick, @Nullable List<FormattedCharSequence> tooltipLines) {
        this.name = name;
        this.iconResource = iconRl;
        this.iconSize = iconSize;
        this.iconFrameSize = iconSize + 8;
        this.iconSelectedFrameSize = iconSize + 10;
        this.hotkey = hotkey;
        this.isSelected = isSelected;
        this.isHidden = isHidden;
        this.isEnabled = isEnabled;
        this.onLeftClick = onLeftClick;
        this.onRightClick = onRightClick;
        this.tooltipLines = tooltipLines;
    }

    // constructor for ability/action/production buttons with non-default frame
    public Button(String name, int iconSize, ResourceLocation iconRl, ResourceLocation frameRl, @Nullable Keybinding hotkey, Supplier<Boolean> isSelected,
                  Supplier<Boolean> isHidden, Supplier<Boolean> isEnabled, @Nullable Runnable onLeftClick,
                  @Nullable Runnable onRightClick, @Nullable List<FormattedCharSequence> tooltipLines) {
        this.name = name;
        this.iconResource = iconRl;
        this.frameResource = frameRl;
        this.iconSize = iconSize;
        this.iconFrameSize = iconSize + 8;
        this.iconSelectedFrameSize = iconSize + 10;
        this.hotkey = hotkey;
        this.isSelected = isSelected;
        this.isHidden = isHidden;
        this.isEnabled = isEnabled;
        this.onLeftClick = onLeftClick;
        this.onRightClick = onRightClick;
        this.tooltipLines = tooltipLines;
    }

    public void renderHealthBar(PoseStack poseStack) {
        if (entity != null)
            HealthBarClientEvents.renderForEntity(poseStack, entity,
                    x + ((float) iconFrameSize / 2), y - 5,
                    iconFrameSize - 1,
                    HealthBarClientEvents.RenderMode.GUI_ICON);
        else if (building != null)
            HealthBarClientEvents.renderForBuilding(poseStack, building,
                    x + ((float) iconFrameSize / 2), y - 5,
                    iconFrameSize - 1,
                    HealthBarClientEvents.RenderMode.GUI_ICON);
    }

    /** Corner of the icon frame a badge sits in. */
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    public static final float BADGE_SCALE = 0.75f;
    public static final int BADGE_BG = 0xC8000000;
    public static final int BADGE_H = 8;

    /**
     * A small label in a corner of the frame on a dark backing: the hotkey (top-right), ability charges
     * (bottom-right), the selection strip's "xN" count (bottom-left), a trade rate (top-left). RoN drew the hotkey 1x
     * straight over the 14 px icon's bottom-right, so on 22 px tiles "Q", "Z" and "E A" (hotkey plus charge count)
     * clipped each other and the icon in lovish's clip at GUI scale 2. The badge is 0.75x, hugs the frame edge,
     * never crosses the tile's centre and never leaves the frame, so neither the icon's middle nor the neighbouring
     * tile is touched. The label string is the only allocation; nothing else per frame.
     */
    protected void renderBadge(GuiGraphics guiGraphics, String text, int x, int y, Corner corner, int colour) {
        if (text == null || text.isEmpty())
            return;
        int xyDiff = (DEFAULT_ICON_SIZE - iconSize) / 2;
        int fx = x + xyDiff, fy = y + xyDiff, fs = iconFrameSize;
        int tw = Math.round(MC.font.width(text) * BADGE_SCALE);
        int bw = Math.min(tw + 3, fs - 2);
        int bx = (corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT) ? fx + fs - 1 - bw : fx + 1;
        int by = (corner == Corner.BOTTOM_LEFT || corner == Corner.BOTTOM_RIGHT) ? fy + fs - 1 - BADGE_H : fy + 1;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 2);
        guiGraphics.fill(bx, by, bx + bw, by + BADGE_H, BADGE_BG);
        guiGraphics.pose().translate(bx + 2, by + 1, 0);
        guiGraphics.pose().scale(BADGE_SCALE, BADGE_SCALE, 1.0f);
        guiGraphics.drawString(MC.font, text, 0, 0, colour, false);
        guiGraphics.pose().popPose();
    }

    protected void renderHotkey(GuiGraphics guiGraphics, int x, int y) {
        // hotkey letter(s), a badge in the top-right corner of the frame
        if (this.hotkey != null) {
            String hotkeyStr = hotkey.getCurrentLabel();
            hotkeyStr = hotkeyStr.substring(0, Math.min(3, hotkeyStr.length()));
            renderBadge(guiGraphics, hotkeyStr, x, y, Corner.TOP_RIGHT, 0xFFFFFF);
        }
    }

    public void render(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        this.x = x;
        this.y = y;

        int xyDiff = (DEFAULT_ICON_SIZE - iconSize) / 2;

        if (this.frameResource != null) {
            guiGraphics.pose().translate(0,0,1);
            MyRenderer.renderIconFrameWithBg(guiGraphics, this.frameResource, x + xyDiff, y + xyDiff, iconFrameSize, bgColour);
        }

        if (!playerNameForHeadIcon.isBlank()) {
            guiGraphics.pose().translate(0,0,1);
            MyRenderer.renderPlayerHead(guiGraphics, playerNameForHeadIcon, x + 5, y + 5, 12, bgIconResource);
        } else if (bgIconResource != null) {
            guiGraphics.pose().translate(0,0,1);
            int iconX = x+4 + (7 - xyDiff - iconSize/2);
            int iconY = y+4 + (7 - xyDiff - iconSize/2);
            iconX += ((DEFAULT_ICON_SIZE - imageSize) / 2) - innerIconSizeModifier;
            iconY += ((DEFAULT_ICON_SIZE - imageSize) / 2) - innerIconSizeModifier;
            MyRenderer.renderIcon(
                    guiGraphics,
                    bgIconResource,
                    iconX,
                    iconY,
                    imageSize + (innerIconSizeModifier * 2)
            );
        }

        // item/unit icon
        if (iconResource != null) {
            int iconX = x+4 + (7 - xyDiff - iconSize/2);
            int iconY = y+4 + (7 - xyDiff - iconSize/2);
            iconX += ((DEFAULT_ICON_SIZE - imageSize) / 2) - innerIconSizeModifier;
            iconY += ((DEFAULT_ICON_SIZE - imageSize) / 2) - innerIconSizeModifier;
            guiGraphics.pose().translate(0,0,1);
            MyRenderer.renderIcon(
                    guiGraphics,
                    iconResource,
                    iconX, iconY,
                    imageSize + (innerIconSizeModifier * 2)
            );
        }
        if (iconItem != null) {
            guiGraphics.pose().translate(0,0,1);
            int offset = 2 + Math.round((1.0f - iconItemScale) * 8f);
            MyRenderer.renderItem(guiGraphics, iconItem, x+offset + (7 - xyDiff - iconSize/2), y+offset + (7 - xyDiff - iconSize/2), iconItemScale);
        }

        // user is holding click or hotkey down over the button and render frame if so
        if ((isEnabled.get() || showSelectedFrameWhenDisabled) &&
            (isSelected.get() || (hotkey != null && hotkey.isDown()) || (isMouseOver(mouseX, mouseY) &&
                    ((MiscUtil.isLeftClickDown(MC) && onLeftClick != null) ||
                    (MiscUtil.isRightClickDown(MC) && onRightClick != null))
            ))) {

            if (frameResource != null) {
                ResourceLocation iconFrameSelectedResource = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame_selected.png");
                guiGraphics.pose().translate(0,0,1);
                MyRenderer.renderIcon(
                        guiGraphics,
                        iconFrameSelectedResource,
                        x-1 + xyDiff,y-1 + xyDiff,
                        iconSelectedFrameSize
                );
            }
        }
        if (isActive != null && isActive.get()) {
            guiGraphics.pose().translate(0,0,1);
            int fx = x + xyDiff, fy = y + xyDiff, fs = iconFrameSize;
            guiGraphics.fill(fx, fy, fx + fs, fy + fs, ACTIVE_GLOW);
            guiGraphics.fill(fx, fy, fx + fs, fy + 1, ACTIVE_EDGE);
            guiGraphics.fill(fx, fy + fs - 1, fx + fs, fy + fs, ACTIVE_EDGE);
            guiGraphics.fill(fx, fy, fx + 1, fy + fs, ACTIVE_EDGE);
            guiGraphics.fill(fx + fs - 1, fy, fx + fs, fy + fs, ACTIVE_EDGE);
        }
        renderHotkey(guiGraphics, x, y);

        // light up on hover
        if (isEnabled.get() && isMouseOver(mouseX, mouseY) && lightUpOnHover) {
            guiGraphics.pose().translate(0,0,1);
            guiGraphics.fill( // x1,y1, x2,y2,
                    x + xyDiff, y + xyDiff,
                    x + xyDiff + iconFrameSize,
                    y + xyDiff + iconFrameSize,
                    0x32FFFFFF); //ARGB(hex); note that alpha ranges between ~0-16, not 0-255
        }

        if (getGreyPercent != null) {
            greyPercent = getGreyPercent.get();
        }

        if (greyPercent > 0 || (!isEnabled.get() && greyWhenDisabled)) {
            int greyHeightPx = Math.round(greyPercent * iconFrameSize);
            if (!isEnabled.get())
                greyHeightPx = 0;

            guiGraphics.pose().translate(0,0,1);
            guiGraphics.fill( // x1,y1, x2,y2,
                    x + xyDiff,
                    y + xyDiff + (greyInverted ? 0 : greyHeightPx),
                    x + xyDiff + iconFrameSize,
                    y + xyDiff + (greyInverted ? greyHeightPx : iconFrameSize),
                    0x99000000); //ARGB(hex); note that alpha ranges between ~0-16, not 0-255
        }


        if (isFlashing.get()) {
            guiGraphics.fill(x, y,
                x + iconFrameSize,
                y + iconFrameSize,
                (0xFFFFFF | ((int) (0x80 * MiscUtil.getOscillatingFloat(0,1)) << 24))
            ); //ARGB(hex); note that alpha ranges between ~0-16, not 0-255
        }

        if (bottomLeftText != null)
            renderBadge(guiGraphics, bottomLeftText.get(), x, y, Corner.BOTTOM_LEFT, bottomLeftTextColor);
    }

    public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (tooltipLines != null)
            MyRenderer.renderTooltip(guiGraphics, withHotkeyLine(tooltipLines), mouseX, mouseY + tooltipOffsetY);
    }

    /** The tooltip plus a last gray "Hotkey: Q" line; a new list, the button's own lines are never changed. */
    protected List<FormattedCharSequence> withHotkeyLine(List<FormattedCharSequence> lines) {
        if (hotkey == null)
            return lines;
        List<String> plain = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            StringBuilder sb = new StringBuilder();
            line.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
            plain.add(sb.toString());
        }
        String text = hotkeyLineText(hotkey.getCurrentLabel(), I18n.get("hud.reignofnether.tooltip_hotkey"), plain);
        if (text == null)
            return lines;
        List<FormattedCharSequence> out = new ArrayList<>(lines);
        out.add(FormattedCharSequence.forward(text, Style.EMPTY.withColor(ChatFormatting.GRAY)));
        return out;
    }

    /**
     * The hotkey line's text ("Hotkey: Q"), or null for no line: no key bound, or the tooltip already names it
     * ("[Q]" or an earlier hotkey line). pattern has one %s for the label.
     */
    public static String hotkeyLineText(String label, String pattern, List<String> existingLines) {
        if (label == null || label.isBlank())
            return null;
        String text = String.format(pattern, label);
        for (String line : existingLines) {
            if (line.contains("[" + label + "]") || line.equals(text))
                return null;
        }
        return text;
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        int xyDiff = (DEFAULT_ICON_SIZE - iconSize) / 2;
        return (mouseX >= x + xyDiff &&
                mouseY >= y + xyDiff &&
                mouseX < x + xyDiff + iconFrameSize &&
                mouseY < y + xyDiff + iconFrameSize);
    }

    // must be done from mouse press event
    public void checkClicked(int mouseX, int mouseY, boolean leftClick) {
        if (!OrthoviewClientEvents.isEnabled() || !isEnabled.get())
            return;

        if (isMouseOver(mouseX, mouseY) && MC.player != null) {
            if (leftClick && this.onLeftClick != null) {
                MC.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.2f, 1.0f);
                this.onLeftClick.run();
            }
            else if (!leftClick && this.onRightClick != null) {
                MC.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.2f, 1.0f);
                this.onRightClick.run();
            }
        }
    }

    // must be done from mouse press event
    public void checkClickedReleased(int mouseX, int mouseY, boolean leftClick) {
        if (!OrthoviewClientEvents.isEnabled() || !isEnabled.get())
            return;

        if (isMouseOver(mouseX, mouseY) && MC.player != null) {
            if (leftClick && this.onLeftClickRelease != null) {
                this.onLeftClickRelease.run();
            }
        }
    }

    // must be done from key press event
    public void checkPressed(int key) {
        if (!OrthoviewClientEvents.isEnabled() || !isEnabled.get())
            return;

        if (hotkey != null && hotkey.matches(key)) {
            if (MC.player != null)
                MC.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.2f, 1.0f);
            if (this.onLeftClick != null)
                this.onLeftClick.run();
            if (this.onLeftClickRelease != null)
                this.onLeftClickRelease.run();
        }
    }
}
