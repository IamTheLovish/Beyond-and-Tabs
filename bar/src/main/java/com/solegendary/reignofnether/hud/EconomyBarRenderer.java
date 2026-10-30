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
import net.minecraft.client.resources.language.I18n;
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
    // Oct 22 (lovish: "make the metal bar and energy bar not as thick"): the bar is 5 px thick (was 7) and the strip 28 px
    // tall (was 34): amount row, bar, income / expense row, each one pixel closer. Mirrored by ECO_PANEL_H in
    // tools/hud_layout.py.
    static final int BG = 0xC0101216, PANEL_H = 28, POP_W = 58, BAR_H = 5;
    // compact mode (HudPanels.economyCompact, the chevron right of the bar): one row - amount / storage and net, a
    // 2 px fill line under it - for players who read the economy from the net figure alone
    static final int COMPACT_H = 22;   // the stall row under it must still clear the control-group row (22 px)
    static final int PANEL_W_MAX = 210, PANEL_W_MIN = 130;
    static final int OVERCHARGE_GOLD = 0xFFFFD24A;

    public record Layout(int left, int right, int bottom) { }

    /**
     * Top of the "Build speed N%" strip this frame, or -1 when there is none: the status line's one row. A toast is
     * drawn in it while one is up (NotificationClientEvents.coversStallRow) instead of stacking under it.
     */
    static int stallRowY = -1;

    // where renderResource drew the metal [0] and energy [1] bars this frame (the economy-bar sliders hang on them)
    private static final com.solegendary.reignofnether.resources.EconomySliderClientEvents.BarGeom[] lastBar =
        new com.solegendary.reignofnether.resources.EconomySliderClientEvents.BarGeom[2];

    /** A slider marker: a 1 px line through the bar and a small cap above it, light with a dark edge. */
    private static void renderMarker(GuiGraphics gg, com.solegendary.reignofnether.resources.EconomySliderClientEvents.BarGeom g, float level, int colour) {
        int x = g.xOf(level);
        gg.fill(x - 1, g.y0() - 1, x + 2, g.y1() + 1, 0xFF101216);
        gg.fill(x, g.y0() - 1, x + 1, g.y1() + 1, colour);
        gg.fill(x - 3, g.y0() - 3, x + 4, g.y0() - 1, 0xFF101216);   // cap: 2 px, under the amount row's text
        gg.fill(x - 2, g.y0() - 2, x + 3, g.y0() - 1, colour);
    }

    // the worker count under the population: a stream over every client unit (hundreds at 8v8) plus an owner lookup
    // each, every frame, for a number that changes a few times a minute. Recounted 4x a second instead (CR-14).
    static final long WORKER_COUNT_MS = 250;
    private static int workerCount = 0;
    private static String workerCountOwner = null;
    private static long workerCountAt = 0;

    static int workerCount(String owner) {
        long now = System.currentTimeMillis();
        if (owner != null && owner.equals(workerCountOwner) && now - workerCountAt < WORKER_COUNT_MS && now >= workerCountAt)
            return workerCount;
        int n = 0;
        for (net.minecraft.world.entity.LivingEntity le : UnitClientEvents.getAllUnits())
            if (le instanceof WorkerUnit && le instanceof Unit un && owner != null && owner.equals(un.getOwnerName()))
                n++;
        workerCount = n;
        workerCountOwner = owner;
        workerCountAt = now;
        return n;
    }

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
        stallRowY = -1;
        stallRowY = -1;
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
        boolean compact = HudPanels.economyCompact;
        int panelH = compact ? COMPACT_H : PANEL_H;

        // one dark strip for the whole bar
        gg.fill(left, top, left + totalW, top + panelH, BG);
        gg.fill(left, top + panelH, left + totalW, top + panelH + 1, 0xFF3A3F46);
        zones.add(RectZone.getZoneByLW(left, top, totalW, panelH + 1));
        HudPanels.chevron(gg, HudPanels.ECONOMY, left + totalW + 1, top + 1, compact);

        renderResource(gg, font, left, top, panelW, true, resources.ore, eco.metalStorage, eco.metalIncome, eco.metalExpense, compact);
        renderResource(gg, font, left + panelW + POP_W, top, panelW, false, resources.wood, eco.energyStorage, eco.energyIncome, eco.energyExpense, compact);
        if (eco.overcharge && !compact)
            renderOverchargeFrame(gg, font, left + panelW + POP_W, top, panelW);

        // the economy-bar sliders (resources/EconomySliderClientEvents): markers on the local player's own bars only
        com.solegendary.reignofnether.resources.EconomySliderClientEvents.metalBar = null;
        com.solegendary.reignofnether.resources.EconomySliderClientEvents.energyBar = null;
        if (!compact && mc.player != null && owner.equals(mc.player.getName().getString())
                && lastBar[0] != null && lastBar[1] != null) {
            com.solegendary.reignofnether.resources.EconomySliderClientEvents.metalBar = lastBar[0];
            com.solegendary.reignofnether.resources.EconomySliderClientEvents.energyBar = lastBar[1];
            renderMarker(gg, lastBar[0], eco.shareLevel, 0xFFFFFFFF);
            renderMarker(gg, lastBar[1], eco.converterLevel, 0xFFFFFFFF);
        }

        // population block in the middle: bed icon in the player's colour, "used/supply", worker count under it
        int px = left + panelW;
        gg.fill(px, top + 3, px + 1, top + panelH - 3, 0xFF3A3F46);
        gg.fill(px + POP_W - 1, top + 3, px + POP_W, top + panelH - 3, 0xFF3A3F46);
        ResourceLocation bed = PlayerColors.getPlayerColorBedIcon(owner);
        MyRenderer.renderIcon(gg, bed, px + 4, top + 3, 12);
        int pop = UnitClientEvents.getCurrentPopulation(owner);
        int supply = BuildingClientEvents.getTotalPopulationSupply(owner);
        boolean unlimited = supply >= com.solegendary.reignofnether.resources.ResourceCosts.UNLIMITED_POPULATION;
        String popStr = unlimited ? String.valueOf(pop) : pop + "/" + supply;   // no cap = just the count, like BAR
        gg.drawString(font, popStr, px + 19, top + 3, !unlimited && pop >= supply ? 0xFF7A6A : 0xFFFFFF);
        int workers = workerCount(owner);
        String wStr = workers + " workers";
        if (!compact)
            gg.drawString(font, wStr, px + POP_W / 2 - font.width(wStr) / 2, top + 16, 0xA8B0B8);

        int bottom = top + panelH + 1;
        // stall warning under the bar
        // ONE economy line at a time (EconomyWarning): a stall hides the overflow / wasting lines below it, and either
        // hides the idle-factory toast. It names a pool only when that pool is really low.
        boolean stalled = eco.stall < 0.99f;
        boolean mine = mc.player != null && owner.equals(mc.player.getName().getString());
        if (stalled && mine)
            EconomyWarning.markBarLineUp();
        if (stalled) {
            EconomyWarning.Pool short_ = EconomyWarning.stallPool(
                resources.ore / Math.max(1f, eco.metalStorage), resources.wood / Math.max(1f, eco.energyStorage));
            String stallStr = "Build speed " + Math.round(eco.stall * 100) + "%"
                + (short_ == EconomyWarning.Pool.UNNAMED ? "" : "  -  short on " + (short_ == EconomyWarning.Pool.METAL ? "metal" : "energy"));
            int w = font.width(stallStr) + 10;
            int sx = screenWidth / 2 - w / 2;
            stallRowY = bottom;
            // one status line: while a toast is up it is drawn in this row instead (NotificationClientEvents), so the
            // strip steps aside for those few seconds - the row, and the zone, stay
            if (!NotificationClientEvents.coversStallRow()) {
                gg.fill(sx, bottom, sx + w, bottom + 12, 0xC0401010);
                gg.drawString(font, stallStr, sx + 5, bottom + 2, eco.stall < 0.5f ? 0xFF6A6A : 0xFFD24A);
            }
            zones.add(RectZone.getZoneByLW(sx, bottom, w, 12));
            bottom += 12;
        }

        // BAR "wasting" warning: only TRUE waste - the server measures what overflowed with every ally full too.
        // Overflow that went to allies is not lost, so it gets a quiet "sharing" note instead
        boolean metalWaste = updateWaste(true, eco.metalWasted) && !stalled;
        boolean energyWaste = updateWaste(false, eco.energyWasted) && !stalled;
        boolean metalShare = !metalWaste && !stalled && eco.metalShared > 0.05f;
        boolean energyShare = !energyWaste && !stalled && eco.energyShared > 0.05f;
        long nowMs = System.currentTimeMillis();
        // clip study Oct 13 (b10): "WASTING" sat on the bar for 25 straight minutes next to the overflow banner.
        // Now it shows once when the waste starts, again only when the rate changes a lot or after a minute of
        // silence, and never together with the overflow banner below (WarnGate; the labels' own look is unchanged)
        boolean showWaste = WASTE_GATE.update(metalWaste || energyWaste, metalWasteRate + energyWasteRate, nowMs);
        if (!showWaste) {
            metalWaste = false;
            energyWaste = false;
        }
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
        boolean metalOver = updateOverflow(true, !stalled && ownBar && resources.ore > eco.metalStorage * OVERFLOW_FILL
            && eco.metalIncome + eco.metalConverted > eco.metalExpense);
        boolean energyOver = updateOverflow(false, !stalled && ownBar && resources.wood > eco.energyStorage * OVERFLOW_FILL
            && eco.energyIncome > eco.energyExpense);
        // lovish's clip: "YOU ARE OVERFLOWING METAL AND ENERGY" sat there the whole game (1500 storage, +44 metal/s,
        // +550 energy/s) and told him nothing he could do. It says what to build and, like WASTING, shows once for
        // OVERFLOW_PULSE_MS when the overflow starts, again only when the surplus changes a lot or after a minute
        // of silence (clip study Oct 13 b10: the old quiet line still sat there for 25 minutes) - and never while
        // the WASTING label is up, since one warning line at a time is enough
        boolean anyOver = metalOver || energyOver;
        float surplus = (metalOver ? eco.metalIncome + eco.metalConverted - eco.metalExpense : 0)
            + (energyOver ? eco.energyIncome - eco.energyExpense : 0);
        boolean showOver = OVERFLOW_GATE.update(anyOver, surplus, nowMs) && !showWaste;
        if (showOver && ownBar)
            EconomyWarning.markBarLineUp();
        if (showOver) {
            String what = metalOver && energyOver ? I18n.get("hud.reignofnether.overflow.both")
                : metalOver ? I18n.get("hud.reignofnether.overflow.metal") : I18n.get("hud.reignofnether.overflow.energy");
            String todo = metalOver ? I18n.get("hud.reignofnether.overflow.todo_metal")
                : I18n.get("hud.reignofnether.overflow.todo_energy");
            String s = what + " - " + todo;
            int w = font.width(s) + 12;
            int bx = left + totalW / 2 - w / 2;
            double phase = (nowMs % 1000L) / 1000.0 * Math.PI * 2;
            int pulse = 0xA0 + (int) (0x5F * (0.5 + 0.5 * Math.sin(phase)));
            gg.fill(bx, bottom, bx + w, bottom + 13, 0xD0302008);
            gg.fill(bx, bottom + 12, bx + w, bottom + 13, (pulse << 24) | (OVERFLOW_AMBER & 0xFFFFFF));
            gg.drawString(font, s, bx + 6, bottom + 3, (pulse << 24) | (OVERFLOW_AMBER & 0xFFFFFF));
            zones.add(RectZone.getZoneByLW(bx, bottom, w, 13));
            bottom += 13;
            NotificationClientEvents.onOverflowBanner(metalOver, energyOver);   // spoken / toast line, once a minute
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
        if (mouseY >= top && mouseY < top + panelH) {
            List<FormattedCharSequence> tip = null;
            if (mouseX >= left && mouseX < left + panelW)
                tip = List.of(line("Metal  " + resources.ore + " / " + Math.round(eco.metalStorage)),
                    line("+" + fmt(eco.metalIncome) + "/s from extractors, -" + fmt(eco.metalExpense) + "/s on builds"),
                    line("Build more extractors on metal patches to grow income."),
                    line(eco.shareLevel >= 0.999f ? "Share: only the overflow at full storage goes to allies."
                        : "Share: metal above " + Math.round(eco.shareLevel * 100) + "% goes to allies."),
                    line("Drag the marker to change it, right-click to reset."));
            else if (mouseX >= left + panelW + POP_W && mouseX < left + totalW) {
                // energyExpense includes the converters' and the spells' drain; split them out so a bar pinned at the
                // conversion threshold, or falling while nothing is being built, makes sense
                float onBuilds = Math.max(0, eco.energyExpense - eco.energyConverted - eco.manaDrain);
                float threshold = eco.converterLevel;   // the player's slider (the faction's default until moved)
                List<FormattedCharSequence> lines = new ArrayList<>(8);
                lines.add(line("Energy  " + resources.wood + " / " + Math.round(eco.energyStorage)
                    + (eco.overcharge ? "   OVERCHARGED" : "")));
                lines.add(line("+" + fmt(eco.energyIncome) + "/s from generators, -" + fmt(onBuilds) + "/s on builds"));
                lines.add(line("Wind " + Math.round(com.solegendary.reignofnether.resources.Wind.strength()) + " ("
                    + Math.round(com.solegendary.reignofnether.resources.Wind.MIN) + "-" + Math.round(com.solegendary.reignofnether.resources.Wind.MAX)
                    + "): wind generators at " + Math.round(com.solegendary.reignofnether.resources.Wind.multiplier() * 100) + "%"));
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
                    lines.add(line("Drag the marker on the bar to change it, right-click to reset."));
                } else if (manaOwner) {
                    lines.add(line("Energy is mana: every spell is paid from this bar."));
                } else {
                    lines.add(line("Wind generators are cheap; keep them behind the base."));
                }
                tip = lines;
            }
            else if (mouseX >= px && mouseX < px + POP_W)
                tip = unlimited
                    ? List.of(line("Units: " + popStr + "  (" + workers + " workers)"), line("No population cap."))
                    : List.of(line("Population " + popStr + "  (" + workers + " workers)"),
                        line("The host set a population cap (gamerule maxPopulation)."));
            // deferred: HudClientEvents draws exactly one HUD tooltip per frame, and a hovered button wins
            if (tip != null)
                HudClientEvents.deferTooltip(tip, mouseX + 6, mouseY + 12);
        }
        return new Layout(left, left + totalW, bottom);
    }

    private static void renderResource(GuiGraphics gg, Font font, int x, int y, int w, boolean metal,
                                       int amount, float storage, float income, float expense, boolean compact) {
        int fillCol = metal ? METAL_FILL : ENERGY_FILL;
        int dimCol = metal ? METAL_DIM : ENERGY_DIM;
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath("minecraft",
            metal ? "textures/item/iron_ingot.png" : "textures/item/redstone.png");
        MyRenderer.renderIcon(gg, icon, x + 5, y + 3, 12);

        int textX = x + 21;
        int rightX = x + w - 6;
        // amount (bright) / storage (dim)
        String amt = String.valueOf(amount);
        String cap = " / " + Math.round(storage);
        gg.drawString(font, amt, textX, y + 2, 0xFFFFFF);
        gg.drawString(font, cap, textX + font.width(amt), y + 2, 0x9AA0A6);

        // net at the far right of the top line, income/expense pair under it at the bar's end
        float net = income - expense;
        String netStr = (net >= 0 ? "+" : "-") + fmt(Math.abs(net));
        // on a narrow panel (small windows / big GUI scale) the net ran over "amount / storage" (eyes, Oct 13: green
        // digits printed across "1500"); the income/expense pair below already tells the story, so drop the net there
        if (textX + font.width(amt) + font.width(cap) + 6 <= rightX - font.width(netStr))
            gg.drawString(font, netStr, rightX - font.width(netStr), y + 2, net >= 0 ? 0x55FF55 : 0xFF5555);

        // the fill bar (a 2 px line in the compact bar)
        int barY = compact ? y + 14 : y + 12, barH = compact ? 2 : BAR_H;
        float fill = storage > 0 ? Math.max(0, Math.min(1, amount / storage)) : 0;
        lastBar[metal ? 0 : 1] = new com.solegendary.reignofnether.resources.EconomySliderClientEvents.BarGeom(textX, rightX, barY, barY + barH);
        gg.fill(textX, barY, rightX, barY + barH, dimCol);
        gg.fill(textX, barY, textX + Math.round((rightX - textX) * fill), barY + barH, fillCol);
        // storage-full tick: BAR wastes income at full storage, so make the cap visible
        if (fill >= 0.999f)
            gg.fill(rightX - 1, barY - 1, rightX + 1, barY + barH + 1, 0xFFFFFFFF);

        if (compact)
            return;   // the income / expense pair is in the tooltip
        String inc = "+" + fmt(income), exp = "-" + fmt(expense);
        int ex = rightX - font.width(exp);
        int ix = ex - 5 - font.width(inc);
        gg.drawString(font, inc, ix, y + 18, 0x55FF55, false);
        gg.drawString(font, exp, ex, y + 18, 0xFF5555, false);
    }

    static final float OVERFLOW_FILL = 0.95f;
    static final long OVERFLOW_AFTER_MS = 10_000;
    static final int OVERFLOW_AMBER = 0xFFFFB428;
    static final long OVERFLOW_PULSE_MS = 10_000;
    private static long metalOverSince = -1, energyOverSince = -1;

    /**
     * A once-then-quiet gate for a standing economy warning: shown for showMs when its condition starts, then
     * hidden; shown again only when the value it reports has moved by more than half (or BIG_CHANGE_ABS/s) since it
     * was last shown, or after silentMs hidden while the condition still holds. Everything resets when it clears.
     */
    static final class WarnGate {
        static final float BIG_CHANGE_ABS = 5f;
        final long showMs, silentMs;
        long shownSince = -1, hiddenSince = -1;
        float shownValue;

        WarnGate(long showMs, long silentMs) {
            this.showMs = showMs;
            this.silentMs = silentMs;
        }

        boolean update(boolean active, float value, long now) {
            if (!active) {
                shownSince = -1;
                hiddenSince = -1;
                return false;
            }
            if (shownSince >= 0) {
                if (now - shownSince < showMs)
                    return true;
                shownSince = -1;
                hiddenSince = now;
                return false;
            }
            boolean bigChange = hiddenSince >= 0
                && Math.abs(value - shownValue) > Math.max(BIG_CHANGE_ABS, Math.abs(shownValue) * 0.5f);
            if (hiddenSince < 0 || bigChange || now - hiddenSince >= silentMs) {
                shownSince = now;
                hiddenSince = -1;
                shownValue = value;
                return true;
            }
            return false;
        }
    }

    static final long WARN_SILENCE_MS = 60_000;
    private static final WarnGate WASTE_GATE = new WarnGate(OVERFLOW_PULSE_MS, WARN_SILENCE_MS);
    private static final WarnGate OVERFLOW_GATE = new WarnGate(OVERFLOW_PULSE_MS, WARN_SILENCE_MS);

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
        int x1 = x + 21, x2 = x + w - 6, by = y + 12, bh = BAR_H;
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
        gg.fill(plateX, by - 1, plateX + plateW, by + bh + 1, OVERCHARGE_GOLD);   // 7 px: the word is 6 px tall, the bar only 5
        gg.pose().pushPose();
        gg.pose().translate((x1 + x2) / 2f - font.width(s) * scale / 2f, by - 0.5f, 0);
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
