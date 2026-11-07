package com.solegendary.reignofnether.unit.units.storm;

/**
 * Where the Rocket Battery's rack puts its bolts, as plain numbers (no Minecraft): the rack is two compact pods of copper
 * tubes over the crew's shoulders, two tubes in each (one behind the other), each tube loaded with one charged bolt seated
 * in its muzzle (lovish, Nov 7: "B: copper tubes"). The model (RocketRackModel, client) is built from these numbers and
 * the battery fires each bolt from its own tube's muzzle (RocketBatteryUnit.fireRocket), so what flies off the rack is
 * what was drawn on it. The pods stand outside the head's width so the rack can lift forward to fire without passing
 * through it.
 * <p>
 * Torso space as the model's: pixels (1/16 block), y down from the shoulder line, the back at +z, +x the crew's left.
 * The rack hinges at ({@link #HINGE_Y}, {@link #HINGE_Z}) and tilts about x (positive = the tops lean forward).
 */
public final class RocketRackGeometry {
    private RocketRackGeometry() { }

    public static final int TUBES = 4;
    /** Where the rack hinges on the torso (px). */
    public static final float HINGE_Y = 9.5F, HINGE_Z = 3.5F;
    /**
     * Each tube's centre across the back (px along x) and how far behind the hinge it stands (px along z), in firing
     * order: right pod front, left pod front, right pod back, left pod back - the volley alternates shoulders.
     */
    public static final float[] TUBE_X = { -5.5F, 5.5F, -5.5F, 5.5F };
    public static final float[] TUBE_Z = { 1.0F, 1.0F, 4.0F, 4.0F };
    /** The tubes' cross-section (px), and where they run from the hinge (px, y up is negative): bottom to mouth. */
    public static final float TUBE_WIDTH = 3.0F, TUBE_BOTTOM = -7.0F, TUBE_MOUTH = -19.0F;
    /** Where a bolt leaves: the middle of the charge seated in the tube's mouth (px above the hinge). */
    public static final float MUZZLE_Y = TUBE_MOUTH - 1.0F;
    /** The rack's tilt at rest, and the extra tilt forward when it is lifted into firing position (radians). */
    public static final float REST_TILT = -0.12F, LIFT_TILT = 0.45F;
    /** The tilt a bolt leaves at: the rack lifted into firing position. */
    public static final float FIRE_TILT = REST_TILT + LIFT_TILT;
    /** The crew's model origin above its feet (the shoulder line), in blocks. */
    public static final double SHOULDER_HEIGHT = 1.5;

    /** The muzzle of tube {@code tube} in torso space (px) with the rack tilted by {@code tilt}: {x, y, z}. */
    public static float[] muzzle(int tube, float tilt) {
        float y = MUZZLE_Y, z = TUBE_Z[tube];
        // the whole rack tips about x (a positive tilt moves the tops towards -z, forward)
        float cos = (float) Math.cos(tilt), sin = (float) Math.sin(tilt);
        return new float[] { TUBE_X[tube], HINGE_Y + y * cos - z * sin, HINGE_Z + y * sin + z * cos };
    }

    /**
     * The world position of tube {@code tube}'s muzzle for a crew standing at ({@code fx}, {@code fy}, {@code fz}) whose
     * body faces {@code bodyYawDeg} (Minecraft yaw: 0 = +z), with the rack at {@code tilt}: {x, y, z}.
     */
    public static double[] muzzleWorld(double fx, double fy, double fz, float bodyYawDeg, int tube, float tilt) {
        float[] t = muzzle(tube, tilt);
        double yaw = Math.toRadians(bodyYawDeg);
        double leftX = Math.cos(yaw), leftZ = Math.sin(yaw), fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
        double side = t[0] / 16.0, back = t[2] / 16.0;   // +x is the crew's left, +z its back
        return new double[] { fx + leftX * side - fwdX * back, fy + SHOULDER_HEIGHT - t[1] / 16.0, fz + leftZ * side - fwdZ * back };
    }
}
