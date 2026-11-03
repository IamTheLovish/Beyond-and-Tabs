package com.solegendary.reignofnether.orthoview;

/**
 * Embeddium's block-face culling, made right for the orthographic RTS view (lovish, Nov 3: buildings at the bottom of
 * the screen "sliced" and see-through at some angles - the Sunforged capitol's walls and roofs gone, its inner stairs
 * showing).
 * <p>
 * Embeddium 0.3.31 (DefaultChunkRenderer.getVisibleFaces) draws a section's faces of one direction only when the camera
 * POINT is on that side of the section (+-3 blocks): +Y faces only when the camera is above the section, -Z faces only
 * when it is on the -Z side, and so on. That is right for a perspective camera, where every ray starts at that point.
 * In the RTS view the rays are parallel: a face is seen whenever it faces against the view direction, wherever the
 * camera point is. The camera point (the player's eye, 24 back along the view - CameraMixin) sits only 30-100 above
 * the ground, so a tall building near the bottom edge of the screen reaches above it and behind it; Embeddium then
 * dropped the walls facing the viewer and the roofs, and the far walls and floors inside showed through
 * (OrthoFaceCullTest: 22-74% of the visible faces of a 24x24 hall at the bottom edge, zoom 60-90; none at zoom 30).
 * <p>
 * The orthographic view is the limit of a perspective camera pulled infinitely far back along the view direction, so
 * Embeddium is handed a camera point {@link #BACK} blocks back for that test only (EmbeddiumOrthoFaceCullMixin): every
 * section in the render distance is then in front of it, and its rule keeps exactly the faces that face the viewer and
 * still skips the back faces. Only the face test moves - the real camera keeps placing the geometry. Pure maths, no
 * client classes, so the unit test runs it as-is.
 */
public final class OrthoFaceCull {

    /**
     * How far back the face test's camera point goes: far enough that a face only edge-on to the view by
     * (render distance) / BACK - 512 / 2^20, under 0.03 degrees - could still be judged by the point's position, and
     * small enough that a world-border coordinate (30M) minus it stays far inside an int.
     */
    public static final int BACK = 1 << 20;

    // the shift for the current terrain pass (0 outside the RTS view and in a shader pack's shadow pass)
    private static int dx, dy, dz;

    private OrthoFaceCull() {
    }

    /** Set once per terrain pass: whether the RTS view is drawing it, and the camera's unit view direction. */
    public static void beginPass(boolean ortho, float fx, float fy, float fz) {
        dx = ortho ? shift(fx) : 0;
        dy = ortho ? shift(fy) : 0;
        dz = ortho ? shift(fz) : 0;
    }

    static int shift(float forward) {
        return -Math.round(BACK * forward);
    }

    public static int camX(int x) {
        return x + dx;
    }

    public static int camY(int y) {
        return y + dy;
    }

    public static int camZ(int z) {
        return z + dz;
    }
}
