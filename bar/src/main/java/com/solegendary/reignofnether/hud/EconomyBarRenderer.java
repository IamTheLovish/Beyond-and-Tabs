package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.mana.ManaClientEvents;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.resources.EconomyClientEvents.ClientEconomy;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR's top bar: metal on the left, energy on the right, each a wide fill bar with "current / storage" above it and
 * the income, expense and net per second at its end; a small population block sits between them and a build-speed
 * warning hangs under the bar while the player is stalling. Centred at the top of the screen, like BAR.
 * Stormcallers (Mana): a pulsing gold OVERCHARGE frame on the energy bar while it is on, the Overcharge toggle chip
 * under the bar (own Mana players only; ManaClientEvents handles the click) and a mana-drain line in the tooltip.
 */
public final class EconomyBarRenderer {
    private EconomyBarRenderer() { }

    static final int METAL_FILL = 0xFFB4BEC8, METAL_DIM = 0xFF2A2E33;
    static final int ENERGY_FILL = 0xFFF0C83C, ENERGY_DIM = 0xFF33301E;
    static final int BG = 0xC0101216, PANEL_H = 34, POP_W = 58;
    static final int PANEL_W_MAX = 210, PANEL_W_MIN = 130;
    static final int OVERCHARGE_GOLD = 0xFFFFD24A;

    public record Layout(int left, int right, int bottom) { }

    /**
     * @return the bar's layout, so callers can keep other HUD elements clear of it
     */
    public static Layout render(GuiGraphics gg, Resources resources, ClientEconomy eco, String owner,
                                int screenWidth, int mouseX, int mouseY, List<RectZone> zones) {
        return render(gg, resources, eco, owner, screenWidth, mouseX, mouseY, zones, 0);
    }

    /** @param reservedLeft pixels at the left edge to stay clear of (the control-group buttons) */
    public static Layout render(GuiGraphics gg, Resources resources, ClientEconomy eco, String owner,
                                int screenWidth, int mouseX, int mouseY, List<RectZone> zones, int reservedLeft) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int panelW = Math.max(PANEL_W_MIN, Math.min(PANEL_W_MAX, (screenWidth - 200 - POP_W) / 2));
        int totalW = panelW * 2 + POP_W;
        int left = screenWidth / 2 - totalW / 2;
        if (left < reservedLeft) {   // slide right past the control groups; shrink the panels if that runs off-screen
            left = reservedLeft;
            int room = screenWidth - 40 - left;   // leave the right-edge buttons alone
            if (totalW > room) {
                panelW = Math.max(90, (room - POP_W) / 2);
                totalW = panelW * 2 + POP_W;
            }
        }
        int top = 0;

        // one dark strip for the whole bar
        gg.fill(left, top, left + totalW, top + PANEL_H, BG);
        gg.fill(left, top + PANEL_H, left + totalW, top + PANEL_H + 1, 0xFF3A3F46);
        zones.add(RectZone.getZoneByLW(left, top, totalW, PANEL_H + 1));

        renderResource(gg, font, left, top, panelW, true, resources.ore, eco.metalStorage, eco.metalIncome, eco.metalExpense);
        renderResource(gg, font, left + panelW + POP_W, top, panelW, false, resources.wood, eco.energyStorage, eco.energyIncome, eco.energyExpense);
        if (eco.overcharge)
            renderOverchargeFrame(gg, font, left + panelW + POP_W, top, panelW);

        // population block in the middle: bed icon in the player's colour, "used/supply", worker count under it
        int px = left + panelW;
        gg.fill(px, top + 3, px + 1, top + PANEL_H - 3, 0xFF3A3F46);
        gg.fill(px + POP_W - 1, top + 3, px + POP_W, top + PANEL_H - 3, 0xFF3A3F46);
        ResourceLocation bed = PlayerColors.getPlayerColorBedIcon(owner);
        MyRenderer.renderIcon(gg, bed, px + 4, top + 4, 12);
        int pop = UnitClientEvents.getCurrentPopulation(owner);
        int supply = BuildingClientEvents.getTotalPopulationSupply(owner);
        String popStr = pop + "/" + supply;
        gg.drawString(font, popStr, px + 19, top + 4, pop >= supply ? 0xFF7A6A : 0xFFFFFF);
        int workers = (int) UnitClientEvents.getAllUnits().stream()
            .filter(u -> u instanceof WorkerUnit && u instanceof Unit un && owner.equals(un.getOwnerName())).count();
        String wStr = workers + " workers";
        gg.drawString(font, wStr, px + POP_W / 2 - font.width(wStr) / 2, top + 18, 0xA8B0B8);

        int bottom = top + PANEL_H + 1;
        // stall warning under the bar
        if (eco.stall < 0.99f) {
            String stallStr = "Build speed " + Math.round(eco.stall * 100) + "%  -  short on "
                + (shortOnMetal(resources, eco) ? "metal" : "energy");
            int w = font.width(stallStr) + 10;
            int sx = screenWidth / 2 - w / 2;
            gg.fill(sx, bottom, sx + w, bottom + 12, 0xC0401010);
            gg.drawString(font, stallStr, sx + 5, bottom + 2, eco.stall < 0.5f ? 0xFF6A6A : 0xFFD24A);
            zones.add(RectZone.getZoneByLW(sx, bottom, w, 12));
            bottom += 12;
        }

        // BAR "wasting" warning: only TRUE waste - the server measures what overflowed with every ally full too.
        // Overflow that went to allies is not lost, so it gets a quiet "sharing" note instead
        boolean metalWaste = updateWaste(true, eco.metalWasted);
        boolean energyWaste = updateWaste(false, eco.energyWasted);
        boolean metalShare = !metalWaste && eco.metalShared > 0.05f;
        boolean energyShare = !energyWaste && eco.energyShared > 0.05f;
        if (metalWaste || energyWaste || metalShare || energyShare) {
            if (metalWaste)
                renderWasteLabel(gg, font, zones, left + 4, bottom, true, metalWasteRate);
            else if (metalShare)
                renderShareLabel(gg, font, zones, left + 4, bottom, true, eco.metalShared);
            if (energyWaste)
                renderWasteLabel(gg, font, zones, left + totalW - 4, bottom, false, energyWasteRate);
            else if (energyShare)
                renderShareLabel(gg, font, zones, left + totalW - 4, bottom, false, eco.energyShared);
            bottom += 12;
        }

        // BAR shouts when you overflow; a small WASTING chip did not register in a 17 minute game (lovish sat at
        // 3499/3500 metal and 4499/4500 energy with five idle labs). After OVERFLOW_AFTER_MS at >95% of storage with
        // a positive net, an amber banner stays up until the pool drops. Own bar only - not an observer's view
        boolean ownBar = mc.player != null && owner.equals(mc.player.getName().getString());
        boolean metalOver = updateOverflow(true, ownBar && resources.ore > eco.metalStorage * OVERFLOW_FILL
            && eco.metalIncome + eco.metalConverted > eco.metalExpense);
        boolean energyOver = updateOverflow(false, ownBar && resources.wood > eco.energyStorage * OVERFLOW_FILL
            && eco.energyIncome > eco.energyExpense);
        if (metalOver || energyOver) {
            String s = metalOver && energyOver ? "YOU ARE OVERFLOWING METAL AND ENERGY"
                : metalOver ? "YOU ARE OVERFLOWING METAL" : "YOU ARE OVERFLOWING ENERGY";
            int w = font.width(s) + 12;
            int bx = left + totalW / 2 - w / 2;
            double phase = (System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2;
            int pulse = 0xA0 + (int) (0x5F * (0.5 + 0.5 * Math.sin(phase)));
            gg.fill(bx, bottom, bx + w, bottom + 13, 0xD0302008);
            gg.fill(bx, bottom + 12, bx + w, bottom + 13, (pulse << 24) | (OVERFLOW_AMBER & 0xFFFFFF));
            gg.drawString(font, s, bx + 6, bottom + 3, (pulse << 24) | (OVERFLOW_AMBER & 0xFFFFFF));
            zones.add(RectZone.getZoneByLW(bx, bottom, w, 13));
            bottom += 13;
            NotificationClientEvents.onOverflowBanner(metalOver, energyOver);
        }

        // Overcharge toggle chip: only on your own bar, only for a Mana faction (the server re-checks both)
        boolean manaOwner = FactionTraits.of(PlayerClientEvents.getPlayerFaction(owner)).mana;
        ManaClientEvents.overchargeChip = null;
        if (manaOwner && mc.player != null && owner.equals(mc.player.getName().getString())
                && ManaClientEvents.localIsMana()) {
            String key = Keybindings.overcharge.getCurrentLabel();
            String s = (eco.overcharge ? "OVERCHARGE ON" : "Overcharge") + (key.isEmpty() ? "" : "  [" + key + "]");
            int w = font.width(s) + 10;
            int cx = left + totalW - 4 - w;
            boolean hover = mouseX >= cx && mouseX < cx + w && mouseY >= bottom && mouseY < bottom + 12;
            gg.fill(cx, bottom, cx + w, bottom + 12, eco.overcharge ? 0xD0503A10 : (hover ? 0xD0303640 : 0xC0101216));
            gg.fill(cx, bottom + 11, cx + w, bottom + 12, eco.overcharge ? OVERCHARGE_GOLD : 0xFF6A6450);
            gg.drawString(font, s, cx + 5, bottom + 2, eco.overcharge ? OVERCHARGE_GOLD : 0xD8D0B8);
            RectZone chip = RectZone.getZoneByLW(cx, bottom, w, 12);
            zones.add(chip);
            ManaClientEvents.overchargeChip = chip;
            if (hover)
                HudClientEvents.deferTooltip(List.of(
                    line(eco.overcharge ? "Overcharge is ON - click to switch off" : "Overcharge - click to switch on"),
                    line("Spells cost x" + fmtMult(ManaServerEvents.OVERCHARGE_COST) + " for x"
                        + fmtMult(ManaServerEvents.OVERCHARGE_POWER) + " power."),
                    line("Build speed is capped at " + Math.round(ManaServerEvents.OVERCHARGE_BUILD_CAP * 100)
                        + "% and converters pause."),
                    line("Switches itself off when energy runs out.")), mouseX + 6, mouseY + 12);
            bottom += 12;
        }

        // tooltips
        if (mouseY >= top && mouseY < top + PANEL_H) {
            List<FormattedCharSequence> tip = null;
            if (mouseX >= left && mouseX < left + panelW)
                tip = List.of(line("Metal  " + resources.ore + " / " + Math.round(eco.metalStorage)),
                    line("+" + fmt(eco.metalIncome) + "/s from extractors, -" + fmt(eco.metalExpense) + "/s on builds"),
                    line("Build more extractors on metal patches to grow income."));
            else if (mouseX >= left + panelW + POP_W && mouseX < left + totalW) {
                // energyExpense includes the converters' and the spells' drain; split them out so a bar pinned at the
                // conversion threshold, or falling while nothing is being built, makes sense
                float onBuilds = Math.max(0, eco.energyExpense - eco.energyConverted - eco.manaDrain);
                float threshold = FactionTraits.of(PlayerClientEvents.getPlayerFaction(owner)).converterThreshold;
                List<FormattedCharSequence> lines = new ArrayList<>(8);
                lines.add(line("Energy  " + resources.wood + " / " + Math.round(eco.energyStorage)
                    + (eco.overcharge ? "   OVERCHARGED" : "")));
                lines.add(line("+" + fmt(eco.energyIncome) + "/s from generators, -" + fmt(onBuilds) + "/s on builds"));
                if (manaOwner || eco.manaDrain > 0.05f)
                    lines.add(line("-" + fmt(eco.manaDrain) + "/s mana drain (spells)"
                        + (eco.overcharge ? " - x" + fmtMult(ManaServerEvents.OVERCHARGE_COST) + " cost, x"
                        + fmtMult(ManaServerEvents.OVERCHARGE_POWER) + " power" : "")));
                if (eco.overcharge)
                    lines.add(line("Overcharge: build speed max "
                        + Math.round(ManaServerEvents.OVERCHARGE_BUILD_CAP * 100) + "%, converters paused."));
                if (eco.conversionCapacity > 0) {
                    lines.add(line("-" + fmt(eco.energyConverted) + "/s into converters (max " + fmt(eco.conversionCapacity)
                        + "/s) -> +" + fmt(eco.metalConverted) + " metal/s"));
                    lines.add(line("Converters only use energy above " + Math.round(threshold * 100) + "% storage ("
                        + Math.round(eco.energyStorage * threshold) + "),"));
                    lines.add(line("so energy sitting there means they're running, not that you're short."));
                } else if (manaOwner) {
                    lines.add(line("Energy is mana: every spell is paid from this bar."));
                } else {
                    lines.add(line("Wind generators are cheap; keep them behind the base."));
                }
                tip = lines;
            }
            else if (mouseX >= px && mouseX < px + POP_W)
                tip = List.of(line("Population " + popStr + "  (" + workers + " workers)"),
                    line("Build houses or more capitols to raise supply."));
            // deferred: HudClientEvents draws exactly one HUD tooltip per frame, and a hovered button wins
            if (tip != null)
                HudClientEvents.deferTooltip(tip, mouseX + 6, mouseY + 12);
        }
        return new Layout(left, left + totalW, bottom);
    }

    private static void renderResource(GuiGraphics gg, Font font, int x, int y, int w, boolean metal,
                                       int amount, float storage, float income, float expense) {
        int fillCol = metal ? METAL_FILL : ENERGY_FILL;
        int dimCol = metal ? METAL_DIM : ENERGY_DIM;
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("minecraft",
            metal ? "textures/item/iron_ingot.png" : "textures/item/redstone.png");
        MyRenderer.renderIcon(gg, icon, x + 5, y + 4, 12);

        int textX = x + 21;
        int rightX = x + w - 6;
        // amount (bright) / storage (dim)
        String amt = String.valueOf(amount);
        String cap = " / " + Math.round(storage);
        gg.drawString(font, amt, textX, y + 4, 0xFFFFFF);
        gg.drawString(font, cap, textX + font.width(amt), y + 4, 0x9AA0A6);

        // net at the far right of the top line, income/expense pair under it at the bar's end
        float net = income - expense;
        String netStr = (net >= 0 ? "+" : "-") + fmt(Math.abs(net));
        gg.drawString(font, netStr, rightX - font.width(netStr), y + 4, net >= 0 ? 0x55FF55 : 0xFF5555);

        // the fill bar
        int barY = y + 16, barH = 7;
        float fill = storage > 0 ? Math.max(0, Math.min(1, amount / storage)) : 0;
        gg.fill(textX, barY, rightX, barY + barH, dimCol);
        gg.fill(textX, barY, textX + Math.round((rightX - textX) * fill), barY + barH, fillCol);
        // storage-full tick: BAR wastes income at full storage, so make the cap visible
        if (fill >= 0.999f)
            gg.fill(rightX - 1, barY - 1, rightX + 1, barY + barH + 1, 0xFFFFFFFF);

        String inc = "+" + fmt(income), exp = "-" + fmt(expense);
        int ex = rightX - font.width(exp);
        int ix = ex - 5 - font.width(inc);
        gg.drawString(font, inc, ix, y + 24, 0x55FF55, false);
        gg.drawString(font, exp, ex, y + 24, 0xFF5555, false);
    }

    static final float OVERFLOW_FILL = 0.95f;
    static final long OVERFLOW_AFTER_MS = 10_000;
    static final int OVERFLOW_AMBER = 0xFFFFB428;
    private static long metalOverSince = -1, energyOverSince = -1;

    /** True once the pool has been over the line for OVERFLOW_AFTER_MS straight; resets as soon as it is not. */
    private static boolean updateOverflow(boolean metal, boolean over) {
        long now = System.currentTimeMillis();
        long since = metal ? metalOverSince : energyOverSince;
        since = over ? (since < 0 ? now : since) : -1;
        if (metal) metalOverSince = since; else energyOverSince = since;
        return since >= 0 && now - since >= OVERFLOW_AFTER_MS;
    }

    // Hysteresis so the label doesn't flicker as the server's 1 s waste average bounces around zero: show after
    // WASTE_SHOW_MS of continuous waste, keep for WASTE_HOLD_MS after it stops. The shown rate is smoothed.
    private static final long WASTE_SHOW_MS = 1000, WASTE_HOLD_MS = 1500;
    private static long metalWasteSince = -1, energyWasteSince = -1;
    private static long metalWasteLast = -1, energyWasteLast = -1;
    private static float metalWasteRate = 0, energyWasteRate = 0;

    /** @param net per second truly wasted (overflow nobody on the team had room for), from the server */
    private static boolean updateWaste(boolean metal, float net) {
        long now = System.currentTimeMillis();
        boolean atCap = net > 0.05f;
        long since = metal ? metalWasteSince : energyWasteSince;
        long last = metal ? metalWasteLast : energyWasteLast;
        float rate = metal ? metalWasteRate : energyWasteRate;
        if (atCap) {
            if (since < 0)
                since = now;
            last = now;
            rate = rate <= 0 ? net : rate + (net - rate) * 0.1f;
        } else if (last < 0 || now - last > WASTE_HOLD_MS) {
            since = -1;
            rate = 0;
        }
        if (metal) { metalWasteSince = since; metalWasteLast = last; metalWasteRate = rate; }
        else { energyWasteSince = since; energyWasteLast = last; energyWasteRate = rate; }
        return since >= 0 && now - since >= WASTE_SHOW_MS;
    }

    // "WASTING +X/s" in the resource's bar colour, pulsing slowly (~1.2 s) on a dark chip so it stays readable;
    // anchored at the bar's left edge for metal and right edge for energy, under the bar/stall warning
    private static void renderWasteLabel(GuiGraphics gg, Font font, List<RectZone> zones, int anchorX, int y,
                                         boolean metal, float rate) {
        String s = "WASTING +" + fmt(rate) + "/s";
        int w = font.width(s) + 8;
        int x = metal ? anchorX : anchorX - w;
        double phase = (System.currentTimeMillis() % 1200L) / 1200.0 * Math.PI * 2;
        int alpha = 0x90 + (int) (0x6F * (0.5 + 0.5 * Math.sin(phase)));
        int col = ((metal ? METAL_FILL : ENERGY_FILL) & 0x00FFFFFF) | (alpha << 24);
        gg.fill(x, y, x + w, y + 12, 0xC0101216);
        gg.fill(x, y + 11, x + w, y + 12, col);
        gg.drawString(font, s, x + 4, y + 2, col);
        zones.add(RectZone.getZoneByLW(x, y, w, 12));
    }

    // "sharing +X/s": full storage, but the overflow is going to allies with room (BAR), so nothing is lost.
    // Small and steady (no pulse) - it's information, not a warning
    private static void renderShareLabel(GuiGraphics gg, Font font, List<RectZone> zones, int anchorX, int y,
                                         boolean metal, float rate) {
        String s = "sharing +" + fmt(rate) + "/s";
        int w = font.width(s) + 8;
        int x = metal ? anchorX : anchorX - w;
        gg.fill(x, y, x + w, y + 12, 0xA0101216);
        gg.drawString(font, s, x + 4, y + 2, 0x8FD18F);
        zones.add(RectZone.getZoneByLW(x, y, w, 12));
    }

    /**
     * Overcharge on the energy bar: a pulsing gold frame around the fill bar with "OVERCHARGE" drawn small on it, so
     * the state reads at a glance from the RTS camera (it caps build speed, so it must never be forgotten on).
     * Same geometry as renderResource's fill bar.
     */
    private static void renderOverchargeFrame(GuiGraphics gg, Font font, int x, int y, int w) {
        int x1 = x + 21, x2 = x + w - 6, by = y + 16, bh = 7;
        double phase = (System.currentTimeMillis() % 900L) / 900.0 * Math.PI * 2;
        int alpha = 0x80 + (int) (0x7F * (0.5 + 0.5 * Math.sin(phase)));
        int col = (OVERCHARGE_GOLD & 0x00FFFFFF) | (alpha << 24);
        gg.fill(x1 - 1, by - 1, x2 + 1, by, col);
        gg.fill(x1 - 1, by + bh, x2 + 1, by + bh + 1, col);
        gg.fill(x1 - 1, by, x1, by + bh, col);
        gg.fill(x2, by, x2 + 1, by + bh, col);
        String s = "OVERCHARGE";
        float scale = 0.75f;
        // a solid gold plate under the word, so it reads on both the filled and the empty part of the bar
        int plateW = Math.round(font.width(s) * scale) + 4, plateX = (x1 + x2) / 2 - plateW / 2;
        gg.fill(plateX, by, plateX + plateW, by + bh, OVERCHARGE_GOLD);
        gg.pose().pushPose();
        gg.pose().translate((x1 + x2) / 2f - font.width(s) * scale / 2f, by + 0.5f, 0);
        gg.pose().scale(scale, scale, 1f);
        gg.drawString(font, s, 0, 0, 0xFF2A1A00, false);
        gg.pose().popPose();
    }

    private static String fmtMult(float m) {
        return m == Math.round(m) ? String.valueOf(Math.round(m)) : String.valueOf(m);
    }

    static String fmt(float perSecond) {
        if (perSecond < 10f)
            return String.valueOf(Math.round(perSecond * 10f) / 10f);
        return String.valueOf(Math.round(perSecond));
    }

    static FormattedCharSequence line(String s) {
        return FormattedCharSequence.forward(s, Style.EMPTY);
    }

    /**
     * Which pool is causing the stall. Comparing raw expenses was wrong (energy numbers are always bigger), so
     * this compares how full each pool is: the emptier one (relative to its storage) is the one you're short on.
     */
    static boolean shortOnMetal(Resources resources, ClientEconomy eco) {
        float metalFill = resources.ore / Math.max(1f, eco.metalStorage);
        float energyFill = resources.wood / Math.max(1f, eco.energyStorage);
        return metalFill <= energyFill;
    }
}
