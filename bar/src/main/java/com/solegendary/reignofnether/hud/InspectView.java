package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.unit.InspectRules;
import com.solegendary.reignofnether.unit.NonUnitClientEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client side of {@link InspectRules}: the local player's relation to a unit or building, which card info shows for
 * it, and the small Enemy / Ally / Neutral tag drawn on an inspected card.
 */
public final class InspectView {
    private InspectView() { }

    /** Spectators (non-RTS players) and sandbox players see every card in full, like the queue / command-card gates. */
    private static boolean seesEverything() {
        return InspectRules.seesEverything(PlayerClientEvents.isRTSPlayer(), SandboxClientEvents.isSandboxPlayer());
    }

    public static boolean shows(InspectRules.Info info, LivingEntity le) {
        if (seesEverything())
            return true;
        return InspectRules.shows(info, UnitClientEvents.getPlayerToEntityRelationship(le),
            AlliancesClient.canControlAlly(le), NonUnitClientEvents.canControlAllMobs());
    }

    public static boolean shows(InspectRules.Info info, BuildingPlacement b) {
        if (seesEverything())
            return true;
        return InspectRules.shows(info, BuildingClientEvents.getPlayerToBuildingRelationship(b),
            AlliancesClient.canControlAlly(b.ownerName), NonUnitClientEvents.canControlAllMobs());
    }

    /** May the local player keep this unit in a selection of {@code selectionSize}? */
    public static boolean keepInSelection(int selectionSize, LivingEntity le) {
        return InspectRules.keepInSelection(selectionSize, InspectRules.canCommand(
            UnitClientEvents.getPlayerToEntityRelationship(le), AlliancesClient.canControlAlly(le),
            NonUnitClientEvents.canControlAllMobs()));
    }

    /** The tag, right-aligned so it ends at {@code right}, its top at {@code y}; nothing for own things. */
    public static void renderTag(GuiGraphics gg, InspectRules.Tag tag, int right, int y) {
        if (tag == InspectRules.Tag.NONE || tag.label == null)
            return;
        // 0.7x text so "Neutral" fits inside the 32 px unit portrait
        var font = Minecraft.getInstance().font;
        final float s = 0.7f;
        int w = (int) Math.ceil(font.width(tag.label) * s) + 3;
        gg.pose().pushPose();
        gg.pose().translate(0, 0, 300);   // over the portrait's 3D head
        gg.fill(right - w, y, right, y + 8, 0xC0000000);
        gg.pose().translate(right - w + 2, y + 1, 0);
        gg.pose().scale(s, s, 1f);
        gg.drawString(font, tag.label, 0, 0, tag.colour, false);
        gg.pose().popPose();
    }

    public static void renderTag(GuiGraphics gg, LivingEntity le, int right, int y) {
        renderTag(gg, InspectRules.tag(UnitClientEvents.getPlayerToEntityRelationship(le)), right, y);
    }

    public static void renderTag(GuiGraphics gg, BuildingPlacement b, int right, int y) {
        renderTag(gg, InspectRules.tag(BuildingClientEvents.getPlayerToBuildingRelationship(b)), right, y);
    }
}
