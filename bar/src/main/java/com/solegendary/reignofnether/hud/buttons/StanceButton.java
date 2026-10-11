package com.solegendary.reignofnether.hud.buttons;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.LanguageUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * BAR's fire-state toggle on the command card: one button that cycles Hold fire -> Return fire -> Fire at will
 * (left click forward, right click back), its icon following the hud-selected unit's state and three pips under the
 * icon showing which of the three is lit. Applies to every selected fighter at once, as BAR's state buttons do.
 * Tooltips are cached per state so hovering allocates nothing.
 */
public class StanceButton extends Button {

    private static final ResourceLocation[] ICONS = {
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/stance_hold_fire.png"),
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/stance_return_fire.png"),
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/stance_fire_at_will.png"),
    };
    private static final int[] PIP_COLOURS = { 0xFFFF6A5A, 0xFFFFC040, 0xFF7CE07A };   // red, amber, green
    private static final int PIP_OFF = 0xFF3A3F46;
    @SuppressWarnings("unchecked")
    private static final List<FormattedCharSequence>[] TOOLTIPS = new List[3];

    public StanceButton() {
        super("Fire state", Button.itemIconSize, ICONS[EngagementStance.DEFAULT.ordinal()], null,
            () -> false, () -> false, () -> true,
            () -> cycle(true), () -> cycle(false), null);
        this.isActive = () -> current() != EngagementStance.DEFAULT;
    }

    /** The hud-selected unit's stance (Fire at will when nothing fitting is selected). */
    public static EngagementStance current() {
        LivingEntity le = HudClientEvents.hudSelectedEntity;
        return le == null ? EngagementStance.DEFAULT : EngagementStance.of(le);
    }

    static boolean takesStance(LivingEntity le) {
        return le instanceof AttackerUnit && le instanceof Unit && !(le instanceof WorkerUnit);
    }

    static void cycle(boolean forward) {
        EngagementStance next = forward ? current().next() : current().previous();
        UnitClientEvents.sendUnitCommand(next.action(), StanceButton::takesStance);
    }

    static List<FormattedCharSequence> tooltipFor(EngagementStance s) {
        int i = s.ordinal();
        if (TOOLTIPS[i] == null) {
            TOOLTIPS[i] = List.of(
                FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.stance"), Style.EMPTY),
                FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.stance." + s.key), Style.EMPTY),
                FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.stance.tooltip"), Style.EMPTY)
            );
        }
        return TOOLTIPS[i];
    }

    @Override
    public void render(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        EngagementStance s = current();
        this.iconResource = ICONS[s.ordinal()];
        this.tooltipLines = tooltipFor(s);
        super.render(guiGraphics, x, y, mouseX, mouseY);
        // three pips along the bottom edge of the frame, the active state's lit in its colour
        int px = x + 4, py = y + iconFrameSize - 4;
        for (int i = 0; i < 3; i++) {
            int c = i == s.ordinal() ? PIP_COLOURS[i] : PIP_OFF;
            guiGraphics.fill(px + i * 5, py, px + i * 5 + 3, py + 2, c);
        }
    }
}
