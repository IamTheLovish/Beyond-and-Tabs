package com.solegendary.reignofnether.unit.support;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.blocks.BlockUtils;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.units.villagers.WitchUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Client only: while the player aims a Field Medic's harming potion (its button is armed), a red ring the size of the
 * cloud ({@link SupportMath#CLOUD_RADIUS}) follows the cursor, so a click that would cover
 * friends is seen before it is made (claude/healer-ai-oct21.md, C: a click still throws exactly where clicked).
 * Drawn from BlockClientEvents' range-indicator pass; the ring's blocks are recomputed only when the cursor moves to
 * another block.
 */
public final class HarmingAimPreview {
    private HarmingAimPreview() { }

    private static BlockPos lastCentre = null;
    private static Set<BlockPos> ring = Set.of();

    public static void render(PoseStack pose, VertexConsumer vc) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || CursorClientEvents.getLeftClickAction() != UnitAction.THROW_LINGERING_HARMING_POTION)
            return;
        boolean medic = false;
        for (LivingEntity le : UnitClientEvents.getSelectedUnits())
            if (le instanceof WitchUnit) {
                medic = true;
                break;
            }
        BlockPos at = CursorClientEvents.getPreselectedBlockPos();
        if (!medic || at == null || at.equals(BlockPos.ZERO))
            return;
        if (!at.equals(lastCentre)) {
            lastCentre = at;
            ring = MiscUtil.getRangeIndicatorCircleBlocks(at, (int) SupportMath.CLOUD_RADIUS, mc.level);
        }
        for (BlockPos bp : ring) {
            float yOffset = BlockUtils.getSnowLayers(mc.level.getBlockState(bp.above())) * 0.125f;
            MyRenderer.drawBlockFace(pose, vc, Direction.UP, yOffset, bp, 0.9f, 0.15f, 0.1f, 0.45f);
        }
    }
}
