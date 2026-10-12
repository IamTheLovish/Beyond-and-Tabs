package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.unit.Relationship;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Decommission button on an own building's command card (BuildingReclaim is the server side). Irreversible, so
 * it arms on the first click (a message says what it will return) and fires on a second click within
 * {@link #CONFIRM_MS}. Appended after the building's own abilities so no ability's default hotkey slot moves; it has
 * no hotkey of its own, deliberately - nobody should sell a lab with a stray key press.
 */
public final class BuildingReclaimClient {
    private BuildingReclaimClient() { }

    static final long CONFIRM_MS = 3000;
    private static BlockPos armedPos = null;
    private static long armedAt = 0;
    // buildings this client already ordered decommissioned (the server doesn't sync the state; the shrinking
    // structure shows it) - keeps the button from offering it twice
    private static final Set<BlockPos> ordered = new HashSet<>();

    public static AbilityButton button(BuildingPlacement bp) {
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(FormattedCharSequence.forward(I18n.get("hud.reignofnether.decommission"), Style.EMPTY.withBold(true)));
        for (String line : BuildingStats.reclaimLines(bp))
            tooltip.add(FormattedCharSequence.forward(line, Style.EMPTY));
        return new AbilityButton(
            "Decommission",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/pickaxe.png"),
            null,
            () -> bp.originPos.equals(armedPos) && System.currentTimeMillis() - armedAt < CONFIRM_MS,
            () -> !BuildingReclaim.canEverDecommission(bp) || ordered.contains(bp.originPos)
                || BuildingClientEvents.getPlayerToBuildingRelationship(bp) != Relationship.OWNED,
            () -> true,
            () -> click(bp),
            null,
            tooltip,
            null,
            bp
        );
    }

    static void click(BuildingPlacement bp) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;
        String why = BuildingReclaim.refusal(bp, true);
        if (why != null) {
            HudClientEvents.showTemporaryMessage(I18n.get(why));
            return;
        }
        long now = System.currentTimeMillis();
        if (!bp.originPos.equals(armedPos) || now - armedAt >= CONFIRM_MS) {
            armedPos = bp.originPos;
            armedAt = now;
            HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.decommission.confirm", BuildingStats.reclaimValue(bp)));
            return;
        }
        armedPos = null;
        ordered.add(bp.originPos);
        BuildingServerboundPacket.decommission(bp.originPos, mc.player.getName().getString());
    }

    public static void forget(BlockPos pos) {
        ordered.remove(pos);
    }
}
