package com.solegendary.reignofnether.unit.modelling.animations;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Function;

/**
 * Client only. {@link TitheBinderRig}'s parts over ModelPart: the unit model builds its rig once with
 * {@code TitheBinderParts.bones(this::bone)} (like {@code new Quadruped.Rig(this::bone)}) and calls
 * {@code TitheBinderRig.apply(pose, bones)} after restPose. The sign mapping lives in TitheBinderRig.apply (pure,
 * unit-tested); this class only forwards the already-converted numbers onto the part's fields.
 */
@OnlyIn(Dist.CLIENT)
public final class TitheBinderParts {

    private TitheBinderParts() {}

    /** Resolves every bone the rig writes (null where the body has none: that part is skipped). */
    public static TitheBinderRig.Bones bones(Function<String, ModelPart> bone) {
        return new TitheBinderRig.Bones(name -> of(bone.apply(name)));
    }

    /** One ModelPart as a rig part (null stays null). */
    public static TitheBinderRig.Part of(ModelPart p) {
        return p == null ? null : new On(p);
    }

    private static final class On implements TitheBinderRig.Part {
        private final ModelPart p;

        On(ModelPart p) {
            this.p = p;
        }

        @Override
        public void addRotation(float xRad, float yRad, float zRad) {
            p.xRot += xRad;
            p.yRot += yRad;
            p.zRot += zRad;
        }

        @Override
        public void addOffset(float x, float y, float z) {
            p.x += x;
            p.y += y;
            p.z += z;
        }

        @Override
        public void setOffset(float x, float y, float z) {
            p.x = x;
            p.y = y;
            p.z = z;
        }

        @Override
        public void setRotation(float xRad, float yRad, float zRad) {
            p.xRot = xRad;
            p.yRot = yRad;
            p.zRot = zRad;
        }

        @Override
        public void setScale(float s) {
            p.xScale = s;
            p.yScale = s;
            p.zScale = s;
        }

        @Override
        public void setVisible(boolean visible) {
            p.visible = visible;
        }
    }
}
