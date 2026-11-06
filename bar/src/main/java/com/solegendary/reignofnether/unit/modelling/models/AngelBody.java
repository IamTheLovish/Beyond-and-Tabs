package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.AngelRig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * An angel's unit model (MenderAngelUnitModel, SeraphArcherUnitModel, SwordSeraphUnitModel): AngelRenderer hands it
 * the frame's blended rig pose before drawing; its setupAnim lays the pose on the generated body after restPose.
 */
@OnlyIn(Dist.CLIENT)
public interface AngelBody {
    /** The pose for the coming draw (the drive's blend), or null for the rest pose. */
    void frame(AngelRig.Pose pose);

    /** The generated body's detail switch (GeneratedBodies.wantsDetail). */
    void setDetail(boolean on);
}
