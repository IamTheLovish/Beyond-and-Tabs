package com.solegendary.reignofnether.unit.modelling.animations;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Function;

/**
 * Client only. {@link AngelRig}'s parts over ModelPart: an angel's unit model builds its rig once with
 * {@code AngelParts.bones(this::bone)} and calls {@code AngelRig.apply(pose, bones)} after restPose. The sign mapping
 * lives in AngelRig.apply (pure, unit-tested); this class only forwards the converted numbers onto the part's fields.
 */
@OnlyIn(Dist.CLIENT)
public final class AngelParts {

    private AngelParts() {}

    /** Resolves every bone the rig writes (null where the body has none: that part is skipped). */
    public static AngelRig.Bones bones(Function<String, ModelPart> bone) {
        return new AngelRig.Bones(name -> {
            ModelPart p = bone.apply(name);
            return p == null ? null : new On(p);
        });
    }

    private static final class On implements AngelRig.Part {
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
        public void setScale(float x, float y, float z) {
            p.xScale = x;
            p.yScale = y;
            p.zScale = z;
        }

        @Override
        public void setVisible(boolean visible) {
            p.visible = visible;
        }
    }
}
