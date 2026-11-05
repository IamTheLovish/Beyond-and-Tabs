package com.solegendary.reignofnether.unit.units.storm;

/**
 * Where the Rocket Battery's rack puts its bolts, as plain numbers (no Minecraft): the rack is a frame of four copper
 * lightning rods fanned out behind the crew's shoulders, each carrying one charged bolt at its tip. The model
 * (RocketRackModel, client) is built from these numbers and the battery fires each bolt from the tip of its own rod
 * (RocketBatteryUnit.fireRocket), so what flies off the rack is what was drawn on it - not from above the hat.
 * <p>
 * Torso space as the model's: pixels (1/16 block), y down from the shoulder line, the back at +z, +x the crew's left.
 * The rack hinges at ({@link #HINGE_Y}, {@link #HINGE_Z}) and tilts about x (positive = the tops lean forward).
 */
public final class RocketRackGeometry {
    private RocketRackGeometry() { }

    public static final int RODS = 4;
    /** Where the rack hinges on the torso (px). */
    public static final float HINGE_Y = 9.5F, HINGE_Z = 3.5F;
    /** Each rod's foot on the back plate (px along x), its fan angle about z (radians; the top leans outwards) and its length (px). */
    public static final float[] ROD_X = { -4.5F, -1.5F, 1.5F, 4.5F };
    public static final float[] ROD_FAN = { -0.60F, -0.28F, 0.28F, 0.60F };
    public static final float[] ROD_LENGTH = { 16.0F, 19.0F, 19.0F, 16.0F };
    /** The rack's tilt at rest, and the extra tilt forward when it is lifted into firing position (radians). */
    public static final float REST_TILT = -0.12F, LIFT_TILT = 0.30F;
    /** The tilt a bolt leaves at: the rack lifted into firing position. */
    public static final float FIRE_TILT = REST_TILT + LIFT_TILT;
    /** The crew's model origin above its feet (the shoulder line), in blocks. */
    public static final double SHOULDER_HEIGHT = 1.5;

    /** The tip of rod {@code rod} in torso space (px) with the rack tilted by {@code tilt}: {x, y, z}. */
    public static float[] tip(int rod, float tilt) {
        float len = ROD_LENGTH[rod], fan = ROD_FAN[rod];
        // the rod leans out by its fan angle about z (a positive angle moves the top towards +x) ...
        float x = ROD_X[rod] + len * (float) Math.sin(fan), y = -len * (float) Math.cos(fan);
        // ... and the whole rack tips about x (a positive tilt moves the tops towards -z, forward)
        float cos = (float) Math.cos(tilt), sin = (float) Math.sin(tilt);
        return new float[] { x, HINGE_Y + y * cos, HINGE_Z + y * sin };
    }

    /**
     * The world position of rod {@code rod}'s tip for a crew standing at ({@code fx}, {@code fy}, {@code fz}) whose body
     * faces {@code bodyYawDeg} (Minecraft yaw: 0 = +z), with the rack at {@code tilt}: {x, y, z}.
     */
    public static double[] tipWorld(double fx, double fy, double fz, float bodyYawDeg, int rod, float tilt) {
        float[] t = tip(rod, tilt);
        double yaw = Math.toRadians(bodyYawDeg);
        double leftX = Math.cos(yaw), leftZ = Math.sin(yaw), fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
        double side = t[0] / 16.0, back = t[2] / 16.0;   // +x is the crew's left, +z its back
        return new double[] { fx + leftX * side - fwdX * back, fy + SHOULDER_HEIGHT - t[1] / 16.0, fz + leftZ * side - fwdZ * back };
    }
}
