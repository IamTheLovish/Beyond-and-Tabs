package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingHoverServerboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.custombuilding.CustomBuilding;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.unit.Relationship;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * The bottom-left corner when nothing is selected (BAR's unit-info area), from the reference clip lovish liked:
 *  - hovering a building shows its card - name, owner, health and what it is producing with the progress. For a
 *    building you don't own or share, the production comes from the server only while you can see it
 *    (BuildingHoverInfo); an unseen one shows no production at all;
 *  - otherwise a small controls cheat-sheet, localised, gone as soon as anything is selected or hovered.
 * Strings are rebuilt at most every REBUILD_MS (keys can be rebound), so drawing allocates nothing per frame.
 */
public final class HoverCards {

    private static final Minecraft MC = Minecraft.getInstance();

    static final int CARD_W = 136, CARD_H = 50, PAD = 4;
    static final float SHEET_SCALE = 0.75f;
    static final int SHEET_LINE = 8;
    static final int KEY_COLOUR = 0xFFFFC860, DESC_COLOUR = 0xFFC8CCD2, TITLE_COLOUR = 0xFFFFFFFF;

    // ---- the server's answer for the building being hovered ----
    private static final long ASK_EVERY_MS = 500, ANSWER_STALE_MS = 2000;
    private static BlockPos askedPos = null;
    private static long askedMs = 0;
    private static BlockPos infoPos = null;
    private static String infoKey = "";
    private static float infoProgress = 0;
    private static int infoQueued = 0;
    private static long infoMs = 0;

    private HoverCards() { }

    /** From BuildingHoverClientboundPacket. */
    public static void onHoverInfo(BlockPos pos, String key, float progress, int queued) {
        infoPos = pos;
        infoKey = key;
        infoProgress = progress;
        infoQueued = queued;
        infoMs = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ building card

    private static BuildingPlacement cardFor = null;
    private static String cardName = "", cardOwner = "";
    private static int cardOwnerText = 0xFFFFFF, cardOwnerSwatch = 0xFFFFFF;

    /** Height of the card, for the caller's layout. */
    public static int buildingCardHeight() {
        return CARD_H;
    }

    public static void renderBuildingCard(GuiGraphics gg, int x, int y, BuildingPlacement b) {
        Font font = MC.font;
        if (b != cardFor) {
            cardFor = b;
            cardName = buildingName(b);
            cardOwner = b.ownerName == null ? "" : b.ownerName;
            cardOwnerSwatch = cardOwner.isEmpty() ? 0xC8C8C8 : PlayerColors.getPlayerDisplayColorHex(cardOwner) & 0xFFFFFF;
            cardOwnerText = TextContrast.readable(cardOwnerSwatch, TextContrast.worstCaseBacking(0x101216, 0xD0));
        }
        gg.fill(x, y, x + CARD_W, y + CARD_H, UnitInfoCard.BG);
        gg.fill(x, y, x + CARD_W, y + 1, UnitInfoCard.EDGE);
        int ty = y + PAD;
        gg.drawString(font, cardName, x + PAD, ty, TITLE_COLOUR);
        ty += 11;
        if (!cardOwner.isEmpty()) {
            gg.fill(x + PAD, ty, x + PAD + 4, ty + 7, 0xFF000000 | cardOwnerSwatch);
            gg.drawString(font, cardOwner, x + PAD + 7, ty, 0xFF000000 | cardOwnerText);
        }
        ty += 11;
        // health, or build progress for a site
        int barW = CARD_W - PAD * 2;
        float frac = b.isBuilt ? (b.getMaxHealth() > 0 ? (float) b.getHealth() / b.getMaxHealth() : 1f)
            : b.getBlocksPlacedPercent();
        frac = Math.max(0f, Math.min(1f, frac));
        gg.fill(x + PAD, ty, x + PAD + barW, ty + 5, 0xFF000000);
        gg.fill(x + PAD, ty, x + PAD + Math.round(barW * frac), ty + 5,
            b.isBuilt ? UnitInfoCard.healthColour(frac) : 0xFF6AA8FF);
        ty += 8;
        // production: your own and allies' queues are on the client already; anyone else's comes from the server
        String producing = null;
        float progress = 0;
        Relationship rel = BuildingClientEvents.getPlayerToBuildingRelationship(b);
        if (b instanceof ProductionPlacement pp) {
            if (rel == Relationship.OWNED || rel == Relationship.FRIENDLY) {
                if (!pp.productionQueue.isEmpty()) {
                    ActiveProduction ap = pp.productionQueue.get(0);
                    float total = ap.item.getCost(true, pp.ownerName).ticks;
                    progress = total <= 0 ? 0 : 1f - ap.ticksLeft / total;
                    producing = ap.item.getItemName() + (pp.productionQueue.size() > 1 ? "  +" + (pp.productionQueue.size() - 1) : "");
                }
            } else {
                long now = System.currentTimeMillis();
                if (!b.originPos.equals(askedPos) || now - askedMs >= ASK_EVERY_MS) {
                    askedPos = b.originPos;
                    askedMs = now;
                    BuildingHoverServerboundPacket.ask(b.originPos);
                }
                if (b.originPos.equals(infoPos) && now - infoMs < ANSWER_STALE_MS && !infoKey.isEmpty()) {
                    producing = itemName(infoKey) + (infoQueued > 1 ? "  +" + (infoQueued - 1) : "");
                    progress = infoProgress;
                }
            }
        }
        if (producing != null) {
            String s = I18n.get("hud.reignofnether.hover.producing", producing, Math.round(Math.max(0, Math.min(1, progress)) * 100));
            gg.drawString(font, font.plainSubstrByWidth(s, barW), x + PAD, ty, 0xFFE0E4EA);
        } else if (b instanceof ProductionPlacement) {
            gg.drawString(font, I18n.get("hud.reignofnether.hover.not_producing"), x + PAD, ty, 0xFF8A9098);
        }
    }

    public static String buildingName(BuildingPlacement b) {
        if (b.getBuilding() instanceof CustomBuilding cb)
            return cb.name;
        ResourceLocation rl = ReignOfNetherRegistries.BUILDING.getKey(b.getBuilding());
        return rl != null ? I18n.get("buildings.reignofnether." + rl.getPath()) : b.getBuilding().name;
    }

    private static String itemName(String key) {
        ResourceLocation rl = ResourceLocation.tryParse(key);
        ProductionItem item = rl == null ? null : ReignOfNetherRegistries.PRODUCTION_ITEM.get(rl);
        return item != null ? item.getItemName() : key;
    }

    // ------------------------------------------------------------------ controls cheat-sheet

    private static final long REBUILD_MS = 2000;
    private static long builtMs = 0;
    private static String sheetTitle = "";
    private static String[] sheetKeys = new String[0], sheetDescs = new String[0];
    private static int sheetKeyW = 0, sheetW = 0;

    private static void rebuildSheet() {
        Font font = MC.font;
        sheetTitle = I18n.get("hud.reignofnether.controls.title");
        sheetKeys = new String[] {
            I18n.get("hud.reignofnether.controls.left_drag"),
            I18n.get("hud.reignofnether.controls.right_click"),
            I18n.get("hud.reignofnether.controls.right_drag"),
            Keybindings.attack.getDisplayName(),
            Keybindings.stop.getDisplayName(),
            Keybindings.hold.getDisplayName(),
            Keybindings.build.getDisplayName(),
            Keybindings.idleWorker.getDisplayName(),
            Keybindings.lastAlert.getDisplayName(),
            I18n.get("hud.reignofnether.controls.ctrl_right_drag"),
        };
        sheetDescs = new String[] {
            I18n.get("hud.reignofnether.controls.select"),
            I18n.get("hud.reignofnether.controls.move_attack"),
            I18n.get("hud.reignofnether.controls.line_formation"),
            I18n.get("hud.reignofnether.controls.attack_move"),
            I18n.get("hud.reignofnether.controls.stop"),
            I18n.get("hud.reignofnether.controls.hold"),
            I18n.get("hud.reignofnether.controls.build"),
            I18n.get("hud.reignofnether.controls.idle_worker"),
            I18n.get("hud.reignofnether.controls.last_alert"),
            I18n.get("hud.reignofnether.controls.area_command"),
        };
        sheetKeyW = 0;
        int descW = 0;
        for (int i = 0; i < sheetKeys.length; i++) {
            sheetKeyW = Math.max(sheetKeyW, font.width(sheetKeys[i]));
            descW = Math.max(descW, font.width(sheetDescs[i]));
        }
        sheetW = Math.max(font.width(sheetTitle), sheetKeyW + 6 + descW);
    }

    private static int sheetPxW = 0, sheetPxH = 0;

    /** Refreshes the sheet's strings when due; then cheatSheetWidth / cheatSheetHeight are current. */
    public static void prepareCheatSheet() {
        if (System.currentTimeMillis() - builtMs > REBUILD_MS || sheetKeys.length == 0) {
            rebuildSheet();
            builtMs = System.currentTimeMillis();
            sheetPxW = (int) Math.ceil(sheetW * SHEET_SCALE) + PAD * 2;
            sheetPxH = (sheetKeys.length + 1) * SHEET_LINE + PAD * 2;   // 11 lines of 8 px: 96 (tools/hud_layout.py)
        }
    }

    public static int cheatSheetWidth() {
        return sheetPxW;
    }

    public static int cheatSheetHeight() {
        return sheetPxH;
    }

    public static void renderCheatSheet(GuiGraphics gg, int x, int y, int w, int h) {
        Font font = MC.font;
        gg.fill(x, y, x + w, y + h, 0xA0101216);
        gg.pose().pushPose();
        gg.pose().translate(x + PAD, y + PAD, 0);
        gg.pose().scale(SHEET_SCALE, SHEET_SCALE, 1f);
        float line = SHEET_LINE / SHEET_SCALE;
        gg.drawString(font, sheetTitle, 0, 0, TITLE_COLOUR);
        for (int i = 0; i < sheetKeys.length; i++) {
            int ly = Math.round((i + 1) * line);
            gg.drawString(font, sheetKeys[i], 0, ly, KEY_COLOUR);
            gg.drawString(font, sheetDescs[i], sheetKeyW + 6, ly, DESC_COLOUR);
        }
        gg.pose().popPose();
    }
}
