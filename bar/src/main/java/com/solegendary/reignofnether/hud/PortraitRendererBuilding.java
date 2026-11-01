package com.solegendary.reignofnether.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.NightSourceAddon;
import com.solegendary.reignofnether.building.buildings.monsters.SculkCatalyst;
import com.solegendary.reignofnether.building.buildings.placements.SculkCatalystPlacement;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.healthbars.HealthBarClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.util.LanguageUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.joml.Quaternionf;

// Renders a selected Building's block: its HUD icon (the one its buttons use) in a frame, its name above, and a slim
// health bar with the numbers under it (Oct 22, lovish: "units don't need their models to pop up, just a picture of
// their icon" - the spinning block portrait was 60 x 60 px). A frame beside the icon holds the Repeat / Steward
// toggles of an own production building. The block is ICON + SIDE_W wide and frameHeight tall, bottom-anchored by the
// caller; HudLayout.BUILDING_BLOCK_W mirrors it.

public class PortraitRendererBuilding {
    public static final int ICON = 32, SIDE_W = 52, HEALTH_ROW_H = 10;
    public int frameWidth = ICON;                       // the icon frame (square)
    public int frameHeight = ICON + HEALTH_ROW_H + 1;   // icon frame + the health row under it
    public int sideWidth = SIDE_W;
    /** The old portrait's height: the production queue and the multi-building grid are still laid out from it. */
    public static final int LAYOUT_TOP = 60;
    public int xBlock = 31;
    public int yBlock = 25;

    /** Width of the whole block: the icon frame plus the frame beside it. */
    public int blockWidth() {
        return frameWidth + sideWidth;
    }

    public PortraitRendererBuilding() {
    }

    // Render the portrait including:
    // - background frame
    // - block model representing the building
    // - healthbar
    // - building name
    // Must be called from DrawScreenEvent
    public RectZone render(GuiGraphics guiGraphics, int x, int y, BuildingPlacement building) {
        return render(guiGraphics, x, y, building, Integer.MAX_VALUE, false);
    }

    // maxNameWidth: pixels available for the title line (the command panel's width) - long names like
    // "Conversion Pylon (+2 metal/s)" are cut with an ellipsis (NameFit) instead of running over
    // the panel edge or neighbouring widgets
    public RectZone render(GuiGraphics guiGraphics, int x, int y, BuildingPlacement building, int maxNameWidth) {
        return render(guiGraphics, x, y, building, maxNameWidth, false);
    }

    /** @param sideFrame draw the frame beside the icon (the caller puts the Repeat / Steward buttons in it) */
    public RectZone render(GuiGraphics guiGraphics, int x, int y, BuildingPlacement building, int maxNameWidth,
                           boolean sideFrame) {
        Relationship rs = BuildingClientEvents.getPlayerToBuildingRelationship(building);

        String name = "";
        if (building.getBuilding() instanceof CustomBuilding customBuilding) {
            name = customBuilding.name;
        } else {
            ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(building.getBuilding());
            if (key != null) {
                name = LanguageUtil.getTranslation("buildings." + key.getNamespace() + "." + key.getPath());
            }
        }

        if (building.getUpgradeLevel() > 0)
            name = building.getUpgradedName();

        name += com.solegendary.reignofnether.building.BuildingStats.titleSuffix(building);   // " (Lv 2)" on a levelled lab
        if (!building.isBuilt)
            name += " (" + (int) (building.getBlocksPlacedPercent() * 100) + "%)";

        // the owner goes on its own small line above the title instead of a " (Owner)" suffix, which pushed long
        // names like "Metal Extractor (Bot3 (medium))" into an ellipsis that hid both the owner and the income
        boolean showOwner = rs != Relationship.OWNED && !building.ownerName.isBlank();

        // BAR-style income readout for economy buildings (extractors, generators, farms, houses)
        if (building.isBuilt) {
            float mIn = building.getMetalIncome();
            float eIn = building.getEnergyIncome();
            if (mIn > 0 || eIn > 0) {
                StringBuilder sb = new StringBuilder(" (");
                if (mIn > 0)
                    sb.append("+").append(trimFloat(mIn)).append(" ").append(I18n.get("hud.reignofnether.metal_per_s"));
                if (mIn > 0 && eIn > 0)
                    sb.append(", ");
                if (eIn > 0)
                    sb.append("+").append(trimFloat(eIn)).append(" ").append(I18n.get("hud.reignofnether.energy_per_s"));
                name += sb.append(")").toString();
            }
        }

        if (building instanceof SculkCatalystPlacement sc && building.getBuilding().hasActiveAddon(NightSourceAddon.class) && building.isBuilt)
            name += " (" + I18n.get("hud.buildings.reignofnether.sculk_catalyst.range", building.getBuilding().getActiveAddon(NightSourceAddon.class).getNightRange(building), SculkCatalyst.MAX_NIGHT_RANGE) + ")";

        // draw name
        drawFittedName(guiGraphics, name, x+4, y-9, maxNameWidth);
        if (showOwner)
            drawOwnerLine(guiGraphics, building.ownerName, x+4, y-9, maxNameWidth);
        int bgCol = PlayerColors.getPlayerPortraitDisplayColorHex(building.ownerName);
        MyRenderer.renderFrameWithBg(guiGraphics, x, y, frameWidth, frameWidth, bgCol);

        ResourceLocation icon = building.getBuilding().icon;
        if (icon != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 1);
            MyRenderer.renderIcon(guiGraphics, icon, x + 2, y + 2, frameWidth - 4);
            guiGraphics.pose().popPose();
        } else {
            drawBlockOnScreen(x - 15, y - 12, building.getBuilding().portraitBlock, 3.2f);   // no icon: the old block, smaller
        }
        if (sideFrame)
            MyRenderer.renderFrameWithBg(guiGraphics, x + frameWidth, y, sideWidth, frameWidth, 0xA0000000);

        // the health bar spans the icon and the frame beside it; the numbers sit on it
        int rowW = blockWidth();
        int rowY = y + frameWidth + 1;
        HealthBarClientEvents.renderForBuilding(guiGraphics.pose(), building,
                x + (rowW / 2f), rowY, rowW, HealthBarClientEvents.RenderMode.GUI_INFO);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 10);
        guiGraphics.drawCenteredString(
                Minecraft.getInstance().font,
                building.getHealth() + "/" + building.getMaxHealth(),
                x + (rowW / 2), rowY + 1,
                0xFFFFFFFF
        );
        guiGraphics.pose().popPose();

        return RectZone.getZoneByLW(x, y, frameWidth, frameHeight);
    }

    // Draws the title within maxWidth at full size: cut to the width and ended with an ellipsis (NameFit), never
    // drawn smaller (BAC-008: es_es "Centro Urbano (Capital)" and long income suffixes).
    static void drawFittedName(GuiGraphics guiGraphics, String name, int x, int y, int maxWidth) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        guiGraphics.drawString(font, NameFit.fit(name, maxWidth, font::width), x, y, 0xFFFFFFFF);
    }

    private static final float OWNER_SCALE = 0.75f;

    // Draws the owner's name small and grey on the line just above a title drawn at (x, titleY), so the title
    // keeps its full width. Shared with the unit portrait.
    static void drawOwnerLine(GuiGraphics guiGraphics, String owner, int x, int titleY, int maxWidth) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        int scaledMax = (int) (maxWidth / OWNER_SCALE);
        String text = font.width(owner) <= scaledMax ? owner : font.plainSubstrByWidth(owner, scaledMax);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, titleY - 7, 0);
        guiGraphics.pose().scale(OWNER_SCALE, OWNER_SCALE, 1.0f);
        guiGraphics.drawString(font, text, 0, 0, 0xFFBBBBBB);
        guiGraphics.pose().popPose();
    }

    /** "2.0" -> "2", "1.5" -> "1.5" */
    private static String trimFloat(float f) {
        return f == Math.floor(f) ? String.valueOf((int) f) : String.valueOf(f);
    }

    public void drawBlockOnScreen(int x, int y, Block block, float blockScale) {
        ItemStack item = new ItemStack(block);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.translate(x+xBlock, y+yBlock, 100.0F);
        poseStack.scale(blockScale, -blockScale, blockScale);
        RenderSystem.applyModelViewMatrix();

        float angle = (System.currentTimeMillis() / 100) % 360;
        Quaternionf quaternion = Axis.XP.rotationDegrees(25);
        Quaternionf quaternion2 = Axis.YP.rotationDegrees(angle);
        quaternion.mul(quaternion2);
        PoseStack blockPoseStack = new PoseStack();
        blockPoseStack.pushPose();
        blockPoseStack.mulPose(quaternion);
        blockPoseStack.scale(8, 8, 8);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        Minecraft.getInstance().getItemRenderer().renderStatic(
                item, ItemDisplayContext.FIXED,
                15728880, OverlayTexture.NO_OVERLAY,
                blockPoseStack, bufferSource, null, 0);
        bufferSource.endBatch();

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
