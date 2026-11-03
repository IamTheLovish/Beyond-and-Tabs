package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.neutral.EndPortal;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.extensions.IForgeBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public class FrustumMixin {

    // I have no idea why this is needed but without it the game freezes and gets stuck inside
    // this function forever a few seconds after activating orthoView
    @Inject(
            method = "offsetToFullyIncludeCameraCube",
            at = @At("HEAD"),
            cancellable = true
    )
    private void offsetToFullyIncludeCameraCube(int p_194442_, CallbackInfoReturnable<Frustum> cir) {
        if (OrthoviewClientEvents.isEnabled()) {
            cir.setReturnValue((Frustum) (Object) this);
        }
    }

    // Block entities with an infinite render box (beacon, end portal, structure block - see
    // IForgeBlockEntity.getRenderBoundingBox) are never frustum-visible from the RTS camera's far-off position, so a
    // beacon's beam or a portal's pane vanished while its building stood in plain view. Count one visible when its
    // building is within two view-heights of the camera's ground point.
    //
    // isVisible(AABB) is the culling test every entity, block entity and (without Embeddium) chunk section pays per
    // frame, so this stays on the cheap side of it: the RTS-view test first, then an identity check against Forge's
    // one shared infinite box, and the building walk only for that box - with no BlockPos built per building.
    @Inject(
            method = "isVisible(Lnet/minecraft/world/phys/AABB;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    public void isVisible(AABB aabb, CallbackInfoReturnable<Boolean> cir) {
        if (!OrthoviewClientEvents.isEnabled())
            return;
        // the constant is what every vanilla getRenderBoundingBox returns; equals() covers a mod that built its own copy
        if (aabb != IForgeBlockEntity.INFINITE_EXTENT_AABB && !aabb.equals(IForgeBlockEntity.INFINITE_EXTENT_AABB))
            return;
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;
        float reach = Math.max(30, OrthoviewClientEvents.getZoom()) * 2;
        double reachSqr = (double) reach * reach;
        BlockPos onPos = player.getOnPos();
        for (BuildingPlacement building : BuildingClientEvents.getBuildings()) {
            if (!(building instanceof BeaconPlacement) && !(building.getBuilding() instanceof EndPortal))
                continue;
            // horizontal distance only (the old code compared against a point at the building's own height)
            double dx = building.centrePos.getX() - onPos.getX();
            double dz = building.centrePos.getZ() - onPos.getZ();
            if (dx * dx + dz * dz < reachSqr) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
