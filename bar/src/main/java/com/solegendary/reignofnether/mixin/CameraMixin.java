package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class CameraMixin {

    @Shadow private Vec3 position;
    @Shadow protected void setPosition(Vec3 pPos) { }
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private boolean detached;

    // when orthoview is enabled, set the camera to 3rd person and move it further back than normal
    // so that we still render chunks if the orthoview player is inside blocks

    @Inject(
            method = "move",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void move(double pDistanceOffset, double pVerticalOffset, double pHorizontalOffset, CallbackInfo ci) {
        if (!OrthoviewClientEvents.isEnabled())
            return;
        ci.cancel();
        pDistanceOffset -= 20;
        double d0 = (double)this.forwards.x() * pDistanceOffset + (double)this.up.x() * pVerticalOffset + (double)this.left.x() * pHorizontalOffset;
        double d1 = (double)this.forwards.y() * pDistanceOffset + (double)this.up.y() * pVerticalOffset + (double)this.left.y() * pHorizontalOffset;
        double d2 = (double)this.forwards.z() * pDistanceOffset + (double)this.up.z() * pVerticalOffset + (double)this.left.z() * pHorizontalOffset;
        this.setPosition(new Vec3(this.position.x + d0, this.position.y + d1, this.position.z + d2));
    }

    // The RTS camera counts as detached (third person) whatever the camera type says. This used to be a copy of
    // vanilla's whole setup body run again after it (rotation, position, and the third-person pull-back with its
    // eight getMaxZoom ray casts - all done twice per frame whenever the view was detached, and any other mod's
    // setup hook undone). Only the flag was ever different, so only the flag is set.
    @Inject(
            method = "setup",
            at = @At("TAIL")
    )
    public void setup(BlockGetter pLevel, Entity pEntity, boolean pDetached, boolean pThirdPersonReverse,
                      float pPartialTick, CallbackInfo ci) {
        if (OrthoviewClientEvents.isEnabled())
            this.detached = true;
    }
}
