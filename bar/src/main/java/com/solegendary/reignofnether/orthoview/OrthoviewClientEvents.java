package com.solegendary.reignofnether.orthoview;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingClientEvents;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.cursor.CursorModePolicy;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.guiscreen.TopdownGuiServerboundPacket;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.hud.HintBar;
import com.solegendary.reignofnether.onboarding.OnboardingTips;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.keybinds.KeyTaps;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.startpos.StartPosClientEvents;
import com.solegendary.reignofnether.startpos.StartPosServerboundPacket;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialStage;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyMath;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

/**
 * The RTS camera: Beyond All Reason's Spring camera, as far as an orthographic Minecraft view allows
 * (lovish: "feels weird and isn't as smooth as BAR"). Bindings and config keys: design/camera.md.
 *
 * <ul>
 *   <li><b>Wheel</b> zooms toward the ground under the cursor (zooming out is from the screen centre, as Spring does,
 *       unless camera_zoom_out_from_cursor). The camera always owns the wheel over the map - a selection, a build
 *       ghost or Shift never re-route it. <b>Ctrl+wheel</b> tilts.</li>
 *   <li><b>Tilt follows zoom</b>: angled close up, near top-down zoomed out (camera_pitch_close / _far); Ctrl+wheel,
 *       Alt+middle-drag and Alt+pan keys add a tilt offset on top.</li>
 *   <li><b>Middle-drag</b> grabs the map; <b>Alt+middle-drag</b> rotates (yaw) and tilts around the screen centre.</li>
 *   <li>Keyboard pan (WASD / arrows), screen-edge pan with a short acceleration; pan speed scales with zoom.</li>
 *   <li><b>Tab</b> overview, camera anchors, and every "look at" (Space alert, portraits, control groups) glide.</li>
 *   <li><b>Shift never moves the camera</b> - in BAR it only queues orders. It used to tilt / swing the view, and a
 *       Shift+drag building line that reached a screen edge swung the camera side to side.</li>
 * </ul>
 *
 * All motion runs in one per-frame integrator ({@link #updateCamera}) on real frame time: zoom, yaw and tilt are
 * critically damped springs (exact solution, so 30 and 300 fps settle identically), panning is velocity-smoothed,
 * and glides are a 2D spring on the camera position. No allocations per frame. Everything is client-side camera
 * movement, so it behaves the same for a remote multiplayer client as for the host (the server accepts orthoview
 * movement from any client - ServerGamePacketListenerImplMixin).
 *
 * @author SoLegendary, adapted from Mineshot by Nico Bergemann <barracuda415 at yahoo.de>
 */
public class OrthoviewClientEvents {

    public static final int CHAT_Y_OFFSET = -55; // shift chat up by this much when in RTS cam to make room for the

    public enum LeafHideMethod {
        NONE, AROUND_UNITS_AND_CURSOR, // requires threaded video option
        ALL
    }

    public static boolean shouldHideLeaves() {
        return hideLeavesMethod != LeafHideMethod.NONE;
    }

    // Default NONE (lovish Oct 22 17:52): the old default swapped every leaf near the cursor/units for green stained
    // glass, so trees turned see-through as the cursor moved, and each swap re-meshed the chunk. Trees stay solid;
    // units under canopies get silhouettes instead (planned). The HUD leaves button still cycles the other modes.
    public static LeafHideMethod hideLeavesMethod = LeafHideMethod.NONE;
    public static int enabledCount = 0;
    public static boolean enabled = false;
    private static boolean cameraMovingByMouse = false; // excludes edgepanning

    private static final Minecraft MC = Minecraft.getInstance();
    private static final float ZOOM_MIN = 10;
    // the old RoN maximum; the dynamic maximum never drops below this
    private static final float ZOOM_MAX = 90;
    // absolute maximum for BAR-sized battles; the real limit is whatever the render distance can fill
    public static final float ZOOM_MAX_HARD = 200;
    // zoom at which tilt-follows-zoom reaches camera_pitch_far
    private static final float ZOOM_TILT_FAR = ZOOM_MAX * 1.5f;
    private static final float CAMROTY_MAX = -12; // shallowest tilt (towards a side-on view)
    private static final float CAMROTY_MIN = -90; // straight down

    private static final float ZOOM_DEFAULT = 30;
    // the diagonal the (diamond, or 45-degree-rotated square) minimap is drawn for: "map north" in this mod
    private static final float CAMROTX_DEFAULT = 135;
    private static final float CAMROTY_DEFAULT = -45;

    // BAR-feel tuning (all per second of real time)
    private static final float PAN_SPEED_PER_ZOOM = 1.25f;   // pan speed = this * zoom * sensitivity (blocks/s)
    // pan velocity response (1/s) when camera_smoothing is at its default 10. It was a fixed 11/s: the pan reached 90%
    // of its speed only ~0.21 s after the key went down, which read as a delay (lovish, Oct 19: "feels like a delay in
    // the camera moving"). 30/s gets there in ~0.08 s; it now scales with camera_smoothing like the glide (0 = instant).
    private static final float PAN_SMOOTHING_AT_DEFAULT = 30f;
    private static final float ZOOM_KEY_RATE = 2.2f;         // e-folds per second while PageUp/PageDown held
    private static final float YAW_KEY_SPEED = 110f;         // degrees per second (Alt + pan keys)
    private static final float PITCH_KEY_SPEED = 70f;        // degrees per second (Alt + pan keys)
    private static final float ROT_MOUSE_DEG_PER_PX = 0.25f; // Alt + middle-drag, per window pixel
    private static final float TILT_STEP_SCROLL = 4f;        // degrees per notch (Ctrl+wheel)
    private static final int EDGE_PAN_MARGIN = 2;            // window pixels (the cursor is clamped to the window)
    private static final double EDGE_ZONE_FRACTION = 0.015;  // of the window's smaller side: ~16 px at 1080p
    private static final float EDGE_ACCEL_TIME = 0.35f;      // seconds at an edge to reach full speed
    private static final float EDGE_START_FRACTION = 0.35f;  // ...starting from this fraction of it
    private static final float GLIDE_OMEGA_SCALE = 0.6f;     // glides settle a little slower than zoom (~0.35 s)
    private static final long OVERVIEW_HOLD_MS = 250;        // a Tab held this long flies back in on release
    /** A spectator pressed the view key to leave the RTS view: don't pull them back automatically. */
    private static boolean spectatorLeftOnPurpose = false;

    private static int cameraLockTicksLeft = 0;
    private static boolean cameraLocked = false;

    // zoom = number of blocks in view height (higher == zoomed out); springs chase the targets every frame
    private static float zoom = ZOOM_DEFAULT;
    private static float targetZoom = ZOOM_DEFAULT;
    private static float zoomLogVel = 0;            // d(ln zoom)/dt
    private static boolean zoomAnchorAtCursor = false;
    private static float camRotX = CAMROTX_DEFAULT; // left/right - should start northeast (towards -Z,+X)
    private static float targetCamRotX = CAMROTX_DEFAULT;
    private static float yawVel = 0;
    private static float camRotY = CAMROTY_DEFAULT; // up/down; derived from zoom + pitchOffset every frame
    private static float pitchOffset = 0;           // user tilt on top of the zoom-driven pitch (degrees, + = steeper)
    private static float targetPitchOffset = 0;
    private static float pitchVel = 0;
    public static final float MAX_PAN_SENSITIVITY = 3.0f;

    private static float panVelRight = 0; // blocks/s, camera-relative ground plane
    private static float panVelFwd = 0;
    private static float edgeHoldTime = 0;
    private static long lastFrameNanos = 0;
    private static boolean mmbDown = false;
    private static double grabLastX = 0;
    private static double grabLastY = 0;
    private static float lastGroundY = 64;

    // glide: the camera position springs toward a target (a ground point to centre, or a raw camera x/z)
    private static boolean glideActive = false;
    private static boolean glideIsFocus = true;
    private static double glideX = 0, glideY = 64, glideZ = 0;
    private static double glideVelX = 0, glideVelZ = 0;

    // BAR Tab overview
    private static boolean overviewActive = false;
    private static float overviewPrevZoom = ZOOM_DEFAULT;
    private static long overviewPressedAt = 0;

    // BAR camera anchors (store with Ctrl+key, recall with key)
    private static final int ANCHORS = 4;
    private static final boolean[] anchorSet = new boolean[ANCHORS];
    private static final double[] anchorX = new double[ANCHORS], anchorY = new double[ANCHORS], anchorZ = new double[ANCHORS];
    private static final float[] anchorZoom = new float[ANCHORS], anchorYaw = new float[ANCHORS], anchorPitch = new float[ANCHORS];

    // scratch outputs (no allocation in the per-frame maths)
    private static float springVel;
    private static double springVelD;
    private static double offX, offZ;

    // by default orthoview players stay at BASE_Y, but can be raised to as high as MAX_Y if they are clipping terrain
    public static double orthoviewPlayerBaseY = 100;
    public static double orthoviewPlayerMaxY = 160;
    private static double minOrthoviewY = 0;

    public static void setMinOrthoviewY(double value) {
        minOrthoviewY = value;
        if (MC.level != null && MC.player != null && MC.player.getY() < value + 15 && MC.gameMode != null &&
            (MC.gameMode.getPlayerMode() == GameType.CREATIVE ||
            MC.gameMode.getPlayerMode() == GameType.SPECTATOR) && isEnabled()) {
            MC.player.move(MoverType.SELF, new Vec3(0, minOrthoviewY - MC.player.getY() + 15, 0));
        }
    }

    public static float getPanSensitivityMult() {
        return (float) ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.get() / 10f;
    }
    public static void adjustPanSensitivityMult(boolean increase) {
        if (increase && Math.round(getPanSensitivityMult() * 10) < (MAX_PAN_SENSITIVITY * 10))
            ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.set(ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.get() + 1);
        else if (!increase && Math.round(getPanSensitivityMult() * 10) > 1)
            ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.set(ReignOfNetherClientConfigs.CAMERA_SENSITIVITY.get() - 1);
    }

    public static void updateOrthoviewY() {
        if (MC.player != null && MC.level != null) {
            BlockPos playerPos = MC.player.blockPosition();
            int radius = 10; // Defines the area around the player to sample heights
            int sumHeights = 0;
            int count = 0;

            // Iterate through a square area around the player
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int blockX = playerPos.getX() + x;
                    int blockZ = playerPos.getZ() + z;
                    int height = MC.level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ);
                    sumHeights += height;
                    count++;
                }
            }
            // Calculate the average height
            int avgHeight = count > 0 ? sumHeights / count : playerPos.getY();
            lastGroundY = avgHeight;

            // Update ORTHOVIEW values based on the average height
            orthoviewPlayerBaseY = Math.max(avgHeight + 30, minOrthoviewY);
            orthoviewPlayerMaxY = avgHeight + 100;
        }
    }

    // false while the player possesses a unit (hero mode, PossessionClientEvents): the RTS session stays on
    // underneath (enabled), but every RTS camera / HUD / fog hook that asks here steps aside until they return
    public static boolean isEnabled() {
        return enabled && !com.solegendary.reignofnether.hero.PossessionClientEvents.isPossessing();
    }

    public static boolean isCameraMovingByMouse() {
        return cameraMovingByMouse;
    }

    public static float getZoom() {
        return zoom;
    }

    /** The ground height the camera maths works from (whole-block average round the camera, refreshed each tick). */
    public static float getGroundY() {
        return lastGroundY;
    }

    public static float getCamRotX() {
        return -camRotX;
    }

    public static float getCamRotY() {
        return -camRotY;
    }

    public static boolean isOverviewActive() {
        return overviewActive;
    }

    public static boolean isCameraLocked() {
        return cameraLockTicksLeft > 0 || cameraLocked;
    }

    public static void lockCam() {
        cameraLocked = true;
    }

    public static void unlockCam() {
        cameraLocked = false;
    }

    private static void reset() {
        zoomFloor = ZOOM_MIN;
        targetZoom = ZOOM_DEFAULT;
        zoomAnchorAtCursor = false;
        overviewActive = false;
        // rotate back the short way round
        targetCamRotX = camRotX + Mth.wrapDegrees(CAMROTX_DEFAULT - camRotX);
        targetPitchOffset = 0;
    }

    // the camera controls hint while it may still be up; any player camera move (pan, zoom, rotate) closes it
    private static String cameraHint = null;

    private static void cameraMoved() {
        if (cameraHint == null)
            return;
        HintBar.clearIfShowing(cameraHint);
        cameraHint = null;
    }

    public static void rotateCam(float x, float y) {
        if (isCameraLocked()) {
            return;
        }
        targetCamRotX += x;
        targetPitchOffset -= y;
    }

    private static void normaliseYaw() {
        while (camRotX >= 360) {
            camRotX -= 360;
            targetCamRotX -= 360;
        }
        while (camRotX <= -360) {
            camRotX += 360;
            targetCamRotX += 360;
        }
    }

    public static void zoomCam(float zoomAdj) {
        if (isCameraLocked()) {
            return;
        }
        targetZoom = Mth.clamp(targetZoom + zoomAdj, zoomFloor, getZoomMax());
        zoomAnchorAtCursor = false;
    }

    // A scripted close-up (the eyes tour's 07b-s) may zoom past ZOOM_MIN: at 10 blocks of view height a unit is ~11% of
    // the screen, too small to judge hands and feet (Oct 15 stills). The player's wheel and keys keep ZOOM_MIN; any
    // scripted zoom at or above ZOOM_MIN restores it. Tilt stays at its close value (basePitchDeg clamps at ZOOM_MIN).
    private static float zoomFloor = ZOOM_MIN;

    /** Sets the zoom target directly (blocks of view height); below ZOOM_MIN only for scripted captures. */
    public static void setScriptedZoom(float zoomBlocks) {
        if (isCameraLocked()) {
            return;
        }
        zoomFloor = Mth.clamp(zoomBlocks, 2f, ZOOM_MIN);
        targetZoom = Mth.clamp(zoomBlocks, zoomFloor, getZoomMax());
        zoomAnchorAtCursor = false;
    }

    // the furthest zoom at which the whole view is still inside the loaded render distance
    public static float getZoomMax() {
        if (MC.player == null || MC.level == null)
            return ZOOM_MAX;
        double renderRadius = (MC.options.getEffectiveRenderDistance() - 1) * 16.0;
        // camRotY is negative (0 = horizon, -90 = straight down); the maths wants the downward tilt magnitude
        double pitch = Math.toRadians(Mth.clamp(Math.abs(getCamRotY()), 5f, 90f));
        double sinP = Math.sin(pitch);
        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        double height = Math.max(0, cam.y - lastGroundY);
        // horizontal distance from the camera to the ground point at the centre of the screen
        double d0 = pitch >= Math.toRadians(89.5) ? 0 : height / Math.tan(pitch);
        double aspect = (double) MC.getWindow().getScreenWidth() / Math.max(1, MC.getWindow().getScreenHeight());
        double u = 1 / (2 * sinP); // half ground depth per unit of zoom
        double v = aspect / 2;     // half ground width per unit of zoom
        // solve (d0 + u*H)^2 + (v*H)^2 = R^2 for the far corners of the view
        double a = u * u + v * v;
        double b = 2 * d0 * u;
        double c = d0 * d0 - renderRadius * renderRadius;
        double disc = b * b - 4 * a * c;
        double h = disc < 0 ? 0 : (-b + Math.sqrt(disc)) / (2 * a);
        // the view must stay inside the loaded radius (no 1.5x allowance: ground past it is not loaded, so a
        // building ghost placed there is red, see ZoomLimit and BuildingValidators "not_loaded")
        return ZoomLimit.max(h, ZOOM_MAX, ZOOM_MAX_HARD);
    }

    public static void panCam(float x, float y, float z) { // pan camera relative to rotation
        if (MC.player != null) {
            Vec2 XZRotated = MyMath.rotateCoords(x, z, -camRotX);
            moveCamera(XZRotated.x, y, XZRotated.y);
        }
    }

    // moves the camera entity and shifts its previous-position by the same amount so the move shows this frame
    // instead of being interpolated over the next tick (tick-based moves still interpolate normally)
    private static void moveCamera(double dx, double dy, double dz) {
        Player player = MC.player;
        if (player == null || (dx == 0 && dy == 0 && dz == 0))
            return;
        double x0 = player.getX();
        double y0 = player.getY();
        double z0 = player.getZ();
        if (player.noPhysics)
            player.setPos(x0 + dx, y0 + dy, z0 + dz); // spectator: nothing to collide with, and no Vec3 per frame
        else
            player.move(MoverType.SELF, new Vec3(dx, dy, dz));
        clampPlayerToWorldBorder();
        player.xo += player.getX() - x0;
        player.yo += player.getY() - y0;
        player.zo += player.getZ() - z0;
    }

    // ---- view maths (mirrored in tools/camera_math_check.py) ----
    // MC yaw/pitch (degrees, pitch + = looking down) -> camera basis; same convention as vanilla Camera, right = -left.
    // Sets offX/offZ: the horizontal displacement from the camera to the point on the plane y = h that is drawn at
    // window position (ndcX, ndcY) in [-1, 1] (y up), for an orthographic view `z` blocks tall.
    private static void viewOffset(double ndcX, double ndcY, float yawMc, float pitchMc, float z, double aspect,
                                   double camY, double h) {
        double yR = Math.toRadians(yawMc);
        double xR = Math.toRadians(pitchMc);
        double fx = -Math.sin(yR) * Math.cos(xR), fy = -Math.sin(xR), fz = Math.cos(yR) * Math.cos(xR);
        double rx = -Math.cos(yR), rz = -Math.sin(yR);
        // up = right x forward
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double a = ndcX * z * aspect / 2, b = ndcY * z / 2;
        double ox = rx * a + ux * b, oy = uy * b, oz = rz * a + uz * b;
        double t = Math.abs(fy) < 1e-4 ? 0 : (h - camY - oy) / fy;
        offX = ox + fx * t;
        offZ = oz + fz * t;
    }

    // moves the camera by an offset in the view plane, converted into an equivalent horizontal move
    // (sliding along the view direction doesn't change an orthographic image) so the camera height is kept
    private static void moveInViewPlane(double right, double up) {
        double yR = Math.toRadians(getCamRotX());
        double xR = Math.toRadians(getCamRotY());
        double fx = -Math.sin(yR) * Math.cos(xR), fy = -Math.sin(xR), fz = Math.cos(yR) * Math.cos(xR);
        double rx = -Math.cos(yR), rz = -Math.sin(yR);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double ox = rx * right + ux * up, oy = uy * up, oz = rz * right + uz * up;
        if (Math.abs(fy) > 1e-3) {
            ox -= fx * oy / fy;
            oz -= fz * oy / fy;
        }
        moveCamera(ox, 0, oz);
    }

    // changes camera height without shifting the image on screen
    private static void moveVerticalKeepingView(double dy) {
        double yR = Math.toRadians(getCamRotX());
        double xR = Math.toRadians(getCamRotY());
        double fx = -Math.sin(yR) * Math.cos(xR), fy = -Math.sin(xR), fz = Math.cos(yR) * Math.cos(xR);
        if (Math.abs(fy) > 1e-3) {
            double t = dy / fy;
            moveCamera(fx * t, dy, fz * t);
        } else {
            moveCamera(0, dy, 0);
        }
    }

    // critically damped spring, exact for a constant target over dt (frame-rate independent); velocity -> springVel
    private static float springStep(float x, float v, float target, float omega, float dt) {
        if (omega <= 0 || dt <= 0) {
            springVel = omega <= 0 ? 0 : v;
            return omega <= 0 ? target : x;
        }
        float e = x - target;
        float k = v + omega * e;
        float ex = (float) Math.exp(-omega * dt);
        springVel = (v - omega * k * dt) * ex;
        return target + (e + k * dt) * ex;
    }

    private static double springStepD(double x, double v, double target, double omega, double dt) {
        if (omega <= 0 || dt <= 0) {
            springVelD = omega <= 0 ? 0 : v;
            return omega <= 0 ? target : x;
        }
        double e = x - target;
        double k = v + omega * e;
        double ex = Math.exp(-omega * dt);
        springVelD = (v - omega * k * dt) * ex;
        return target + (e + k * dt) * ex;
    }

    // camera_smoothing 10 -> omega 25/s: ~0.15 s to within 10%, ~0.23 s to within 2% (BAR's glide); 0 = instant
    private static float springOmega() {
        int s = ReignOfNetherClientConfigs.CAMERA_SMOOTHING.get();
        return s <= 0 ? 0 : 250f / s;
    }

    // zoom-driven pitch (degrees below the horizon) before the user's tilt offset
    private static float basePitchDeg(float z) {
        float close = ReignOfNetherClientConfigs.CAMERA_PITCH_CLOSE.get();
        if (!ReignOfNetherClientConfigs.CAMERA_TILT_FOLLOWS_ZOOM.get())
            return close;
        float far = ReignOfNetherClientConfigs.CAMERA_PITCH_FAR.get();
        float t = (float) ((Math.log(Math.max(z, ZOOM_MIN)) - Math.log(ZOOM_MIN)) / (Math.log(ZOOM_TILT_FAR) - Math.log(ZOOM_MIN)));
        t = Mth.clamp(t, 0f, 1f);
        // linear in log-zoom: every wheel notch tilts the same small amount. The old smoothstep packed most of a 40
        // degree swing into the middle zooms, so the view visibly bent while zooming (lovish, Oct 12 16:16: "messes
        // up the angling ... a weird bend when zooming in and out"); the default range is also narrower now (55-75)
        return close + (far - close) * t;
    }

    private static float clampPitchOffset(float off, float z) {
        float base = basePitchDeg(z);
        return Mth.clamp(off, -CAMROTY_MAX - base, -CAMROTY_MIN - base);
    }

    // spectator skips vanilla border collision, so snap back inside on every move
    private static void clampPlayerToWorldBorder() {
        if (MC.player == null || MC.level == null) return;
        WorldBorder border = MC.level.getWorldBorder();
        double margin = 1.0D;
        double minX = border.getMinX() + margin;
        double maxX = border.getMaxX() - margin;
        double minZ = border.getMinZ() + margin;
        double maxZ = border.getMaxZ() - margin;
        if (maxX <= minX || maxZ <= minZ) return;
        double x = MC.player.getX();
        double z = MC.player.getZ();
        double cx = Mth.clamp(x, minX, maxX);
        double cz = Mth.clamp(z, minZ, maxZ);
        if (cx != x || cz != z)
            MC.player.setPos(cx, MC.player.getY(), cz);
    }

    public static void forceMoveCam(int x, int z, int cameraLockTicks) {
        forceMoveCam(x, z, cameraLockTicks, 20, (int) ZOOM_DEFAULT);
    }

    // lock the camera and glide it to a location (camera position, as RoN's tutorial/scenario positions are tuned),
    // remain locked for cameraLockTicks after the forcePanTicks the move is given
    public static void forceMoveCam(int x, int z, int cameraLockTicks, int forcePanTicks, int zoomLevel) {
        if (MC.player != null && OrthoviewClientEvents.isEnabled()) {
            cameraLockTicksLeft = forcePanTicks + cameraLockTicks;
            startGlide(false, x, 0, z);
            if (zoomLevel != 0) {
                targetZoom = zoomLevel;
                zoomAnchorAtCursor = false;
            }
        }
    }

    public static void forceMoveCam(String playerName, Vec3i pos, int cameraLockTicks, int forcePanTicks, int zoomLevel) {
        if (MC.player != null && MC.player.getName().getString().equals(playerName)) {
            forceMoveCam(pos.getX(), pos.getZ(), cameraLockTicks, forcePanTicks, zoomLevel);
        }
    }

    public static void forceMoveCam(Vec3i pos, int cameraLockTicks) {
        forceMoveCam(pos.getX(), pos.getZ(), cameraLockTicks);
    }

    /** Stops a glide in progress (a minimap jump or any other cut takes over the camera). */
    public static void cancelGlide() {
        glideActive = false;
        glideVelX = 0;
        glideVelZ = 0;
    }

    private static void startGlide(boolean focus, double x, double y, double z) {
        if (!glideActive) {
            glideVelX = 0;
            glideVelZ = 0;
        }
        glideActive = true;
        glideIsFocus = focus;
        glideX = x;
        glideY = y;
        glideZ = z;
        panVelRight = 0;
        panVelFwd = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) {
            return;
        }

        long windowHandle = MC.getWindow().getWindow();
        int cursorMode;

        try {
            cursorMode = GLFW.glfwGetInputMode(windowHandle, GLFW.GLFW_CURSOR);
        } catch (NullPointerException | IllegalStateException e) {
            cursorMode = GLFW.GLFW_CURSOR_NORMAL;
        }

        boolean grabbed = cursorMode == GLFW.GLFW_CURSOR_DISABLED;
        // never while the RTS view runs on the OS cursor (rts_system_cursor): that mode wants the mouse free
        boolean systemCursorActive = CursorModePolicy.useSystemCursor(
                ReignOfNetherClientConfigs.RTS_SYSTEM_CURSOR.get(), enabled && MC.screen instanceof TopdownGui);
        if (CursorModePolicy.shouldRegrab(systemCursorActive, MC.screen == null, grabbed, MC.isWindowActive())) {
            com.solegendary.reignofnether.cursor.CursorDiagClient.grabCall("regrab");
            MC.mouseHandler.releaseMouse();
            MC.mouseHandler.grabMouse();
        }

        if (isEnabled() && MC.gameMode != null && (
            MC.gameMode.getPlayerMode() == GameType.ADVENTURE || MC.gameMode.getPlayerMode() == GameType.SURVIVAL
        )) {
            toggleEnable();
        }

        // a spectator of a live match always watches from the RTS view: entering a Watch Battle world, a defeat, or
        // any path that left the camera in first person puts it back (checked twice a second; RTS-view only modes)
        if (!enabled && MC.screen == null && MC.player != null && MC.player.tickCount % 10 == 0
                && MC.gameMode != null && (MC.gameMode.getPlayerMode() == GameType.CREATIVE
                    || MC.gameMode.getPlayerMode() == GameType.SPECTATOR)
                && !isCameraLocked() && !spectatorLeftOnPurpose
                && com.solegendary.reignofnether.player.PlayerClientEvents.isMatchSpectator()) {
            toggleEnable();
        }

        if (cameraLockTicksLeft > 0) {
            cameraLockTicksLeft -= 1;
        }

        if (!OrthoviewClientEvents.isEnabled() || MC.player == null || MC.level == null) {
            glideActive = false;
            return;
        }

        // keep the camera above the terrain; moves are made along the view direction so the image doesn't jump
        if (MiscUtil.isGroundBlock(MC.level, MC.player.blockPosition().offset(0, -5, 0))
            && MC.player.getOnPos().getY() <= orthoviewPlayerMaxY) {
            moveVerticalKeepingView(1f);
        }
        if (!MiscUtil.isGroundBlock(MC.level, MC.player.blockPosition().offset(0, -6, 0))
            && MC.player.getOnPos().getY() >= orthoviewPlayerBaseY) {
            moveVerticalKeepingView(-1f);
        }
        updateOrthoviewY();
    }

    private static boolean isRawKeyDown(int key) {
        return GLFW.glfwGetKey(MC.getWindow().getWindow(), key) == GLFW.GLFW_PRESS;
    }

    private static boolean isCtrlDown() {
        return Keybindings.ctrlMod.isDown() || isRawKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isAltDown() {
        return Keybindings.altMod.isDown() || isRawKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static float smoothingAlpha(float rate, float dt) {
        return 1f - (float) Math.exp(-rate * dt);
    }

    // all continuous camera motion happens here, once per rendered frame, before the world is drawn
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0 : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0f, 0.1f);

        if (!isEnabled() || MC.player == null || MC.level == null) {
            panVelRight = 0;
            panVelFwd = 0;
            mmbDown = false;
            return;
        }
        updateCamera(dt);
    }

    private static boolean panKeyDown(Keybinding pan) {
        if (!pan.isDown())
            return false;
        int key = pan.getKey();
        if (!HudClientEvents.isCardKey(key))
            return true;
        // a WASD key that is also an order on the selected card (S stop, or one the player moved onto W/A/S/D) waits
        // out a tap before it pans; once it pans, that press is a hold and fires no order (KeyTaps.isHold), however
        // short lag made it measure
        if (KeyTaps.inTapWindow(key))
            return false;
        KeyTaps.markPan(key);
        return true;
    }

    private static void updateCamera(float dt) {
        Player player = MC.player;
        if (player == null)
            return;

        long window = MC.getWindow().getWindow();
        boolean inRtsScreen = MC.screen instanceof TopdownGui;
        boolean locked = isCameraLocked();
        boolean windowActive = MC.isWindowActive();
        boolean keysActive = inRtsScreen && windowActive && !locked && !TextInputClientEvents.isAnyInputFocused();
        boolean ctrl = isCtrlDown();
        boolean alt = isAltDown();
        double cursorX = MC.mouseHandler.xpos();
        double cursorY = MC.mouseHandler.ypos();
        int winW = Math.max(1, MC.getWindow().getScreenWidth());
        int winH = Math.max(1, MC.getWindow().getScreenHeight());
        double aspect = (double) winW / winH;

        // ---- keyboard: pan, or with Alt held rotate / tilt (Spring's movetilt). Shift is never read. ----
        float desiredRight = 0;
        float desiredFwd = 0;
        if (keysActive && !ctrl) {
            boolean up = panKeyDown(Keybindings.panUp) || Keybindings.panPlusZ.isDown();
            boolean down = panKeyDown(Keybindings.panDown) || Keybindings.panMinusZ.isDown();
            boolean left = panKeyDown(Keybindings.panLeft) || Keybindings.panPlusX.isDown();
            boolean right = panKeyDown(Keybindings.panRight) || Keybindings.panMinusX.isDown();
            if (left || right || up || down)
                cameraMoved();   // pan, or with Alt rotate / tilt
            if (alt) {
                if (left) targetCamRotX += YAW_KEY_SPEED * dt;
                if (right) targetCamRotX -= YAW_KEY_SPEED * dt;
                if (up) targetPitchOffset -= PITCH_KEY_SPEED * dt;   // towards side-on
                if (down) targetPitchOffset += PITCH_KEY_SPEED * dt; // towards top-down
            } else {
                desiredRight = (right ? 1 : 0) - (left ? 1 : 0);
                desiredFwd = (up ? 1 : 0) - (down ? 1 : 0);
                if (desiredRight != 0 && desiredFwd != 0) {
                    desiredRight *= 0.7071f;
                    desiredFwd *= 0.7071f;
                }
            }
        }
        if (keysActive) {
            if (Keybindings.zoomIn.isDownOrAlias() || Keybindings.zoomOut.isDownOrAlias()) {
                zoomFloor = ZOOM_MIN;   // the player's own zoom: a scripted close-up's floor never carries over
                cameraMoved();
            }
            if (Keybindings.zoomIn.isDownOrAlias()) {
                targetZoom *= (float) Math.exp(-ZOOM_KEY_RATE * dt);
                zoomAnchorAtCursor = false;
            }
            if (Keybindings.zoomOut.isDownOrAlias()) {
                targetZoom *= (float) Math.exp(ZOOM_KEY_RATE * dt);
                zoomAnchorAtCursor = false;
            }
        }

        // ---- middle mouse: grab the map, or with Alt rotate / tilt around the screen centre ----
        boolean mmb = inRtsScreen && windowActive && !locked
                && GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        double mdx = 0, mdy = 0;
        if (mmb) {
            if (mmbDown) {
                mdx = cursorX - grabLastX;
                mdy = cursorY - grabLastY;
            }
            mmbDown = true;
            cameraMovingByMouse = true;
            grabLastX = cursorX;
            grabLastY = cursorY;
            panVelRight = 0;
            panVelFwd = 0;
            if (mdx != 0 || mdy != 0) {
                glideActive = false; // the player took the camera
                cameraMoved();
            }
        } else if (mmbDown) {
            mmbDown = false;
            cameraMovingByMouse = false;
        }
        if (mmb && !alt && (mdx != 0 || mdy != 0)) {
            // 1 window pixel = zoom / winH blocks in the view plane, so at speed 10 the ground stays under the cursor
            double bpp = zoom / winH * ReignOfNetherClientConfigs.CAMERA_MMB_SPEED.get() / 10.0;
            if (ReignOfNetherClientConfigs.CAMERA_MMB_INVERT.get())
                bpp = -bpp;
            moveInViewPlane(-mdx * bpp, mdy * bpp);
        }

        // ---- screen-edge pan, ramping up over EDGE_ACCEL_TIME ----
        if (inRtsScreen && windowActive && !locked && !mmb && ReignOfNetherClientConfigs.CAMERA_EDGE_SCROLL.get()) {
            // BAR-style zone: the outer EDGE_ZONE_FRACTION of the window pans, faster the deeper the cursor is in it
            // (a 2 px band was hard to hit in windowed mode, claude/camera-feel-bar.md). Inside the wider band the
            // HUD wins - over the minimap or a button only the outer EDGE_PAN_MARGIN pixels still pan - so a click on
            // edge UI never drags the camera away.
            double zone = Math.max(EDGE_PAN_MARGIN, Math.min(winW, winH) * EDGE_ZONE_FRACTION);
            double dl = cursorX, dr = winW - cursorX, dtp = cursorY, db = winH - cursorY;
            double nearest = Math.min(Math.min(dl, dr), Math.min(dtp, db));
            boolean outerBand = nearest <= EDGE_PAN_MARGIN;
            double band = outerBand || !HudClientEvents.isMouseOverAnyButtonOrHud() ? zone : EDGE_PAN_MARGIN;
            int edgeRight = 0;
            int edgeFwd = 0;
            if (dl <= band) edgeRight = -1;
            else if (dr <= band) edgeRight = 1;
            if (dtp <= band) edgeFwd = 1;
            else if (db <= band) edgeFwd = -1;
            if (edgeRight != 0 || edgeFwd != 0) {
                edgeHoldTime += dt;
                float ramp = EDGE_START_FRACTION + (1 - EDGE_START_FRACTION) * Math.min(1f, edgeHoldTime / EDGE_ACCEL_TIME);
                // depth in the zone: 50% speed at its inner edge, full speed against the window edge
                float depth = (float) (outerBand ? 1 : 0.5 + 0.5 * (1 - Math.min(1, nearest / zone)));
                float mult = ramp * depth * ReignOfNetherClientConfigs.CAMERA_EDGE_SPEED.get() / 10f;
                if (edgeRight != 0 && edgeFwd != 0)
                    mult *= 0.7071f;
                if (desiredRight == 0) desiredRight = edgeRight * mult;
                if (desiredFwd == 0) desiredFwd = edgeFwd * mult;
            } else {
                edgeHoldTime = 0;
            }
        } else {
            edgeHoldTime = 0;
        }
        if (desiredRight < 0) TutorialClientEvents.pannedLeft = true;
        if (desiredRight > 0) TutorialClientEvents.pannedRight = true;
        if (desiredFwd > 0) TutorialClientEvents.pannedUp = true;
        if (desiredFwd < 0) TutorialClientEvents.pannedDown = true;
        if (desiredRight != 0 || desiredFwd != 0) {
            glideActive = false;
            cameraMoved();   // covers screen-edge pan too
        }

        // ---- rotation and zoom: everything below pivots so a ground point stays put on screen ----
        float oldZoom = zoom;
        float oldYawMc = getCamRotX();
        float oldPitchMc = getCamRotY();

        if (mmb && alt && (mdx != 0 || mdy != 0)) {
            // Alt+middle-drag: applied directly (a drag must not lag behind the hand); drag up flattens the view
            float dyaw = (float) mdx * ROT_MOUSE_DEG_PER_PX;
            float dpitch = (float) mdy * ROT_MOUSE_DEG_PER_PX;
            camRotX += dyaw;
            targetCamRotX += dyaw;
            pitchOffset += dpitch;
            targetPitchOffset += dpitch;
        }

        float omega = springOmega();
        targetZoom = Mth.clamp(targetZoom, zoomFloor, getZoomMax());
        targetPitchOffset = clampPitchOffset(targetPitchOffset, targetZoom);

        if (zoom != targetZoom || zoomLogVel != 0) {
            float lt = (float) Math.log(targetZoom);
            float nl = springStep((float) Math.log(zoom), zoomLogVel, lt, omega, dt);
            zoomLogVel = springVel;
            if (Math.abs(nl - lt) < 1e-4f && Math.abs(zoomLogVel) < 1e-3f) {
                zoom = targetZoom;
                zoomLogVel = 0;
            } else {
                zoom = (float) Math.exp(nl);
            }
        }
        if (camRotX != targetCamRotX || yawVel != 0) {
            camRotX = springStep(camRotX, yawVel, targetCamRotX, omega, dt);
            yawVel = springVel;
            if (Math.abs(camRotX - targetCamRotX) < 0.01f && Math.abs(yawVel) < 0.05f) {
                camRotX = targetCamRotX;
                yawVel = 0;
            }
        }
        if (pitchOffset != targetPitchOffset || pitchVel != 0) {
            pitchOffset = springStep(pitchOffset, pitchVel, targetPitchOffset, omega, dt);
            pitchVel = springVel;
            if (Math.abs(pitchOffset - targetPitchOffset) < 0.01f && Math.abs(pitchVel) < 0.05f) {
                pitchOffset = targetPitchOffset;
                pitchVel = 0;
            }
        }
        pitchOffset = clampPitchOffset(pitchOffset, zoom);
        camRotY = -(basePitchDeg(zoom) + pitchOffset);
        normaliseYaw();

        float newYawMc = getCamRotX();
        float newPitchMc = getCamRotY();
        if (zoom != oldZoom || newYawMc != oldYawMc || newPitchMc != oldPitchMc) {
            // pivot: the ground under the cursor while wheel-zooming in (BAR's zoom-to-cursor; out too if configured),
            // otherwise the ground at the screen centre (rotation, keys, glides, overview)
            double ndcX = 0, ndcY = 0, h = lastGroundY;
            boolean zoomingIn = zoom < oldZoom;
            if (zoom != oldZoom && zoomAnchorAtCursor && inRtsScreen && !glideActive
                    && (zoomingIn || ReignOfNetherClientConfigs.CAMERA_ZOOM_OUT_FROM_CURSOR.get())) {
                ndcX = Mth.clamp(cursorX / winW * 2 - 1, -1, 1);
                ndcY = Mth.clamp(1 - cursorY / winH * 2, -1, 1);
                BlockPos under = CursorClientEvents.getPreselectedBlockPos();
                if (under != null)
                    h = under.getY() + 1; // the real terrain height under the cursor, so a hilltop stays put too
            }
            double cx = player.getX(), cy = player.getEyeY(), cz = player.getZ();
            viewOffset(ndcX, ndcY, oldYawMc, oldPitchMc, oldZoom, aspect, cy, h);
            double px = cx + offX, pz = cz + offZ;
            viewOffset(ndcX, ndcY, newYawMc, newPitchMc, zoom, aspect, cy, h);
            moveCamera(px - offX - cx, 0, pz - offZ - cz);
        }

        // ---- glide (Space alert, portraits, control groups, overview, anchors, scripted moves) ----
        if (glideActive) {
            double cx = player.getX(), cy = player.getEyeY(), cz = player.getZ();
            double tx = glideX, tz = glideZ;
            if (glideIsFocus) {
                // the camera position that puts the target ground point at the screen centre, for the current view
                viewOffset(0, 0, newYawMc, newPitchMc, zoom, aspect, cy, glideY);
                tx -= offX;
                tz -= offZ;
            }
            double gOmega = omega * GLIDE_OMEGA_SCALE;
            double nx = springStepD(cx, glideVelX, tx, gOmega, dt);
            glideVelX = springVelD;
            double nz = springStepD(cz, glideVelZ, tz, gOmega, dt);
            glideVelZ = springVelD;
            boolean settled = Math.abs(nx - tx) < 0.02 && Math.abs(nz - tz) < 0.02
                    && Math.abs(glideVelX) < 0.1 && Math.abs(glideVelZ) < 0.1;
            if (settled) {
                nx = tx;
                nz = tz;
                glideVelX = 0;
                glideVelZ = 0;
                // a focus glide keeps tracking while the view is still turning or zooming
                if (!glideIsFocus || (zoom == targetZoom && camRotX == targetCamRotX && pitchOffset == targetPitchOffset))
                    glideActive = false;
            }
            moveCamera(nx - cx, 0, nz - cz);
        }

        // ---- pan (velocity smoothed, speed scales with zoom) ----
        float speed = PAN_SPEED_PER_ZOOM * zoom * getPanSensitivityMult();
        int smoothingSetting = ReignOfNetherClientConfigs.CAMERA_SMOOTHING.get();
        float panA = smoothingSetting <= 0 ? 1f : smoothingAlpha(PAN_SMOOTHING_AT_DEFAULT * 10f / smoothingSetting, dt);
        panVelRight += (desiredRight * speed - panVelRight) * panA;
        panVelFwd += (desiredFwd * speed - panVelFwd) * panA;
        if (Math.abs(panVelRight) < 0.01f && desiredRight == 0) panVelRight = 0;
        if (Math.abs(panVelFwd) < 0.01f && desiredFwd == 0) panVelFwd = 0;
        if ((panVelRight != 0 || panVelFwd != 0) && !locked) {
            double yaw = Math.toRadians(newYawMc);
            double rx = -Math.cos(yaw), rz = -Math.sin(yaw);   // right
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);    // forward along the ground
            moveCamera((rx * panVelRight + fx * panVelFwd) * dt, 0, (rz * panVelRight + fz * panVelFwd) * dt);
        }

        // note that we treat x and y rot as horizontal and vertical, but MC treats it the other way around...
        player.setXRot(getCamRotY());
        player.setYRot(getCamRotX());
        player.xRotO = player.getXRot();
        player.yRotO = player.getYRot();
    }

    // ---- BAR Tab overview ----
    private static void enterOverview() {
        if (MC.level == null)
            return;
        overviewActive = true;
        overviewPrevZoom = targetZoom;
        overviewPressedAt = System.currentTimeMillis();
        zoomAnchorAtCursor = false;
        WorldBorder border = MC.level.getWorldBorder();
        double size = Math.max(border.getMaxX() - border.getMinX(), border.getMaxZ() - border.getMinZ());
        if (size < 10_000) {
            // a match arena (not the vanilla 60M border): frame it (the view is `zoom` blocks tall, wider than tall, and near top-down out here)
            startGlide(true, border.getCenterX(), lastGroundY, border.getCenterZ());
            targetZoom = (float) size * 1.05f;
        } else {
            targetZoom = ZOOM_MAX_HARD; // clamped to what the render distance can fill
        }
    }

    // back to the zoom the overview was opened from, flying to the ground under the cursor
    private static void exitOverview() {
        overviewActive = false;
        targetZoom = overviewPrevZoom;
        zoomAnchorAtCursor = false;
        BlockPos under = CursorClientEvents.getPreselectedBlockPos();
        if (under != null)
            startGlide(true, under.getX() + 0.5, under.getY() + 1, under.getZ() + 0.5);
    }

    // ---- BAR camera anchors ----
    private static void storeAnchor(int i) {
        Player player = MC.player;
        if (player == null)
            return;
        double aspect = (double) MC.getWindow().getScreenWidth() / Math.max(1, MC.getWindow().getScreenHeight());
        viewOffset(0, 0, getCamRotX(), getCamRotY(), zoom, aspect, player.getEyeY(), lastGroundY);
        anchorX[i] = player.getX() + offX;
        anchorY[i] = lastGroundY;
        anchorZ[i] = player.getZ() + offZ;
        anchorZoom[i] = targetZoom;
        anchorYaw[i] = targetCamRotX;
        anchorPitch[i] = targetPitchOffset;
        anchorSet[i] = true;
        HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.camera_anchor_stored", i + 1));
    }

    private static void recallAnchor(int i) {
        if (!anchorSet[i] || isCameraLocked())
            return;
        overviewActive = false;
        targetZoom = anchorZoom[i];
        zoomAnchorAtCursor = false;
        targetCamRotX = camRotX + Mth.wrapDegrees(anchorYaw[i] - camRotX);
        targetPitchOffset = anchorPitch[i];
        startGlide(true, anchorX[i], anchorY[i], anchorZ[i]);
    }

    public static void toggleEnable() {
        if (MC.level == null || MC.player == null) {
            return;
        }

        for (BuildingPlacement building : BuildingClientEvents.getBuildings()) {
            RangeIndicatorAddon ria;
            if ((ria = building.getBuilding().getActiveAddon(RangeIndicatorAddon.class)) != null)
                ria.updateHighlightBps(building);
        }

        enabled = !enabled;
        zoomFloor = ZOOM_MIN;
        glideActive = false;
        overviewActive = false;

        if (enabled) {
            MC.options.tutorialStep = TutorialSteps.NONE;
            MC.getTutorial().stop();
            enabledCount += 1;
            PlayerServerboundPacket.enableOrthoview();
            MinimapClientEvents.setMapCentre(MC.player.getX(), MC.player.getZ());
            PlayerServerboundPacket.teleportPlayer(MC.player.getX(), orthoviewPlayerBaseY, MC.player.getZ());
            TopdownGuiServerboundPacket.openTopdownGui(MC.player.getId());
            MC.options.cloudStatus().set(CloudStatus.OFF);
            MC.options.hideGui = false; // for some reason, when gui is hidden, shape rendering goes whack
            MC.options.setCameraType(CameraType.FIRST_PERSON);
            switchToEasyIfPeaceful();
            // the first RTS view's controls hint goes through the one hint bar (hud/HintBar), not the HUD's red
            // warning line: the two used to overlap the start banner (rover run 1, finding 8), and under the loading
            // veil the warning line would have expired unread - the hint bar's clock waits for the cut
            // shown once ever (remembered in the seen-tips config, "Reset tips" brings it back) and closed as soon
            // as the player moves the camera
            if (OnboardingTips.takeCameraHint()) {
                cameraHint = I18n.get("hud.reignofnether.camera_controls_hint", Keybindings.toggleCamera.getDisplayName());
                HintBar.queue(cameraHint, 400);
            }
        } else {
            PlayerServerboundPacket.disableOrthoview();
            TopdownGuiServerboundPacket.closeTopdownGui(MC.player.getId());
            if (StartPosClientEvents.hasReservedPos()) {
                StartPosClientEvents.selectedFaction = Factions.NONE;
                StartPosServerboundPacket.unreservePos(StartPosClientEvents.getPos().pos);
            }
        }
        TutorialClientEvents.updateStage();
    }

    public static void tryToSetCamera(String playerName, boolean value) {
        if (MC.player != null && MC.player.getName().getString().equals(playerName) && value != enabled) {
            tryToToggleEnable();
        }
    }

    // server-driven (match start, scripted teleports): a cut, not a glide across the map
    public static void centreCameraOnPosForPlayer(String playerName, BlockPos bp) {
        if (MC.player != null && MC.player.getName().getString().equals(playerName))
            centreCameraOnPosInstant(new Vec3(bp.getX(), bp.getY(), bp.getZ()));
    }

    public static void centreCameraOnPos(BlockPos bp) {
        centreCameraOnPos(new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5));
    }

    // glides the camera (BAR) until x,z is at the centre of the screen. Re-targeting every tick (a held portrait)
    // just follows smoothly.
    public static void centreCameraOnPos(Vec3 pos) {
        if (MC.player == null) {
            return;
        }
        if (!enabled) {
            centreCameraOnPosInstant(pos);
            return;
        }
        MinimapClientEvents.setMapCentre(pos.x, pos.z);
        startGlide(true, pos.x, pos.y, pos.z);
    }

    // moves the camera to the position such that x,z is at the centre of the screen, at once
    // (exact: the centre-of-screen ray runs along the view direction through the camera, so slide back along it
    // to the camera's current height)
    public static void centreCameraOnPosInstant(Vec3 pos) {
        if (MC.player == null) {
            return;
        }
        MinimapClientEvents.setMapCentre(pos.x, pos.z);
        double yR = Math.toRadians(getCamRotX());
        double xR = Math.toRadians(getCamRotY());
        double fx = -Math.sin(yR) * Math.cos(xR), fy = -Math.sin(xR), fz = Math.cos(yR) * Math.cos(xR);
        double eyeY = MC.player.getEyeY();
        double s = Math.abs(fy) < 1e-3 ? 0 : (eyeY - pos.y) / fy;
        glideActive = false;
        panVelRight = 0;
        panVelFwd = 0;
        PlayerServerboundPacket.teleportPlayer(pos.x + fx * s, MC.player.getY(), pos.z + fz * s);
    }

    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent evt) {
        if (isEnabled()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent evt) {
        if (isEnabled()) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    // can't use ScreenEvent.KeyboardKeyPressedEvent as that only happens when a screen is up
    public static void onInput(InputEvent.Key evt) {
        // no camera keys under the loading veil (matchstart.MatchLoadingScreen): the view key would drop the RTS
        // view to first person behind the art, and the veil's cut expects the RTS camera it put on the slot
        if (com.solegendary.reignofnether.matchstart.MatchLoadingScreen.isVeiling())
            return;
        int key = evt.getKey();
        boolean onRtsOrWorld = MC.screen == null || MC.screen instanceof TopdownGui;
        boolean typing = TextInputClientEvents.isAnyInputFocused();
        if (evt.getAction() == GLFW.GLFW_PRESS) {
            // the rebindable toggle (Backspace by default) is a printable key, so only honour it in the Minecraft
            // view or on the RTS screen - never while typing in chat, a lobby or a HUD text field. F12 is kept as
            // RoN's legacy alias.
            if ((key == Keybindings.toggleCamera.getKey() && onRtsOrWorld && !typing)
                    || key == Keybindings.getFnum(12).getKey()) {
                // a spectator may leave the RTS view on purpose to look closer from the free camera (lovish, Oct 12
                // 14:50: he couldn't switch views to gather data while watching). Only an ACCIDENTAL exit is undone by
                // the auto re-entry above; pressing the key again comes back. (The 12:xx clips' dark view + vanilla
                // menu are covered by the Match Menu on Esc.)
                if (com.solegendary.reignofnether.player.PlayerClientEvents.isMatchSpectator())
                    spectatorLeftOnPurpose = enabled;
                // hero mode: with one unit selected the toggle possesses it, and while possessing it returns to the
                // RTS view (PossessionClientEvents); otherwise it is the old RTS <-> Minecraft view switch
                if (com.solegendary.reignofnether.hero.PossessionClientEvents.onToggleKey())
                    return;
                tryToToggleEnable();
                return;
            }
            // Tab while possessing: hop to the next unit (PossessionClientEvents); the RTS view's Tab is untouched
            if (key == Keybindings.tab.getKey() && !typing
                    && com.solegendary.reignofnether.hero.PossessionClientEvents.onHopKey())
                return;
            if (!enabled || !(MC.screen instanceof TopdownGui) || typing || key == GLFW.GLFW_KEY_UNKNOWN)
                return;
            if (key == Keybindings.reset.getKey()) {
                reset();
            } else if (key == Keybindings.overview.getKey() && !isCtrlDown() && !isCameraLocked()) {
                // Ctrl+Tab is selection cycling (HudClientEvents)
                if (overviewActive)
                    exitOverview();
                else
                    enterOverview();
            } else {
                for (int i = 0; i < ANCHORS; i++) {
                    if (key == Keybindings.cameraAnchors[i].getKey()) {
                        if (isCtrlDown())
                            storeAnchor(i);
                        else
                            recallAnchor(i);
                        break;
                    }
                }
            }
        } else if (evt.getAction() == GLFW.GLFW_RELEASE) {
            // hold-to-peek: a Tab held past a tap flies back in where the cursor ended up
            if (enabled && overviewActive && key == Keybindings.overview.getKey()
                    && System.currentTimeMillis() - overviewPressedAt >= OVERVIEW_HOLD_MS)
                exitOverview();
        }
    }

    // in the overview a left click on the map is "fly there", not a selection (BAR)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePressPre(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!enabled || !overviewActive || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1
                || !(evt.getScreen() instanceof TopdownGui) || HudClientEvents.isMouseOverAnyButtonOrHud())
            return;
        exitOverview();
        evt.setCanceled(true);
    }

    // rotates the camera to fixed points at -135, -45, 45 and 135 degrees (animated by the frame smoothing)
    public static void fixedRotateCam(boolean clockwise) {
        if (isCameraLocked())
            return;
        float current = -targetCamRotX;
        int roundedRotX = (Math.round(current / 90.0f) * 90) + 45;
        float target;

        if (roundedRotX > current && clockwise)
            target = roundedRotX;
        else if (roundedRotX < current && !clockwise)
            target = roundedRotX - 90;
        else if (clockwise)
            target = roundedRotX;
        else
            target = roundedRotX - 90;

        if (target == current && clockwise)
            target += 90;
        else if (target == current)
            target -= 90;

        targetCamRotX = -target;
    }

    public static void tryToToggleEnable() {
        if (!OrthoviewClientEvents.isCameraLocked() && MC.gameMode != null) {
            if (MC.player != null && (
                    MC.gameMode.getPlayerMode() == GameType.ADVENTURE
                            || MC.gameMode.getPlayerMode() == GameType.SURVIVAL
            )) {
                MC.player.sendSystemMessage(Component.literal(I18n.get("hud.orthoview.reignofnether.ortho_error")));
            } else {
                toggleEnable();
            }
        }
    }

    public static Button getLeavesHidingButton() {
        return new Button("Hide Leaves Method",
                14,
                switch(hideLeavesMethod) {
                    case NONE -> ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/blocks/leaves.png");
                    case AROUND_UNITS_AND_CURSOR -> ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/blocks/lime_stained_glass.png");
                    case ALL -> ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/blocks/glass.png");
                },
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame.png"),
                null,
                () -> false,
                () -> !TutorialClientEvents.isAtOrPastStage(TutorialStage.MINIMAP_CLICK) || !MinimapClientEvents.isLargeMap(),
                () -> true,
                () -> {
                    FogOfWarClientEvents.resetFogChunks();
                    UnitClientEvents.windowUpdateTicks = 0;
                    if (hideLeavesMethod == LeafHideMethod.NONE) {
                        hideLeavesMethod = LeafHideMethod.AROUND_UNITS_AND_CURSOR;
                    } else if (hideLeavesMethod == LeafHideMethod.AROUND_UNITS_AND_CURSOR) {
                        hideLeavesMethod = LeafHideMethod.ALL;
                    } else if (hideLeavesMethod == LeafHideMethod.ALL) {
                        hideLeavesMethod = LeafHideMethod.NONE;
                    }
                },
                null,
                List.of(
                        fcs(I18n.get("hud.orthoview.reignofnether.hiding_leaves_around"), hideLeavesMethod == LeafHideMethod.AROUND_UNITS_AND_CURSOR),
                        fcs(I18n.get("hud.orthoview.reignofnether.hiding_leaves_all"), hideLeavesMethod == LeafHideMethod.ALL),
                        fcs(I18n.get("hud.orthoview.reignofnether.disabled_hiding_leaves"), hideLeavesMethod == LeafHideMethod.NONE)
                )
        );
    }

    // Method to switch difficulty to Easy if it is currently set to Peaceful
    private static void switchToEasyIfPeaceful() {
        Minecraft minecraft = Minecraft.getInstance();

        // Ensure this only runs in single-player mode
        if (minecraft.getSingleplayerServer() != null) {
            Difficulty currentDifficulty = minecraft.level.getDifficulty();

            // If the current difficulty is Peaceful, switch to Easy
            if (currentDifficulty == Difficulty.PEACEFUL) {
                minecraft.getSingleplayerServer().setDifficulty(Difficulty.EASY, true);
                HudClientEvents.showTemporaryMessage(
                    "RTS units cannot spawn in Peaceful. Your difficulty has been set to Easy.");
            }
        }
    }

    // The camera owns the wheel over the map, as in BAR: plain wheel zooms toward the cursor, Ctrl+wheel tilts.
    // Bug (lovish): "with a unit selected the wheel doesn't zoom" - Shift (held to queue orders) re-routed the wheel
    // to tilt, Ctrl to yaw, and any building ghost (a constructor's build hotkey) to ghost rotation. Now Shift is
    // ignored, ghosts rotate with [ and ], and the event is consumed so nothing downstream also acts on it. Only a
    // HUD scroll area keeps the wheel, and only while the cursor is over it.
    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre evt) {
        if (!enabled || isCameraLocked() || !(evt.getScreen() instanceof TopdownGui)) {
            return;
        }
        double delta = evt.getScrollDelta();
        if (delta == 0)
            return;
        if (ReignOfNetherClientConfigs.CAMERA_ZOOM_INVERT.get())   // BAR's "invert zoom" toggle
            delta = -delta;
        if (CustomBuildingClientEvents.isMouseOverScrollArea((int) evt.getMouseX(), (int) evt.getMouseY()))
            return;
        if (com.solegendary.reignofnether.building.AreaMexClientEvents.onScroll(evt.getScrollDelta())) {   // Shift+wheel sizes the area-mex circle
            evt.setCanceled(true);
            return;
        }
        if (com.solegendary.reignofnether.building.BuildingLineClientEvents.onScroll(evt.getScrollDelta())) {   // Shift+wheel: build spacing
            evt.setCanceled(true);
            return;
        }
        evt.setCanceled(true);

        cameraMoved();
        if (isCtrlDown()) {
            // wheel forward flattens the view, back steepens it (Spring: Ctrl+wheel tilts)
            targetPitchOffset -= (float) Math.signum(delta) * TILT_STEP_SCROLL;
            return;
        }
        float step = 1f + 0.015f * ReignOfNetherClientConfigs.CAMERA_ZOOM_SPEED.get(); // 10 -> 15% per notch
        targetZoom = Mth.clamp(targetZoom * (float) Math.pow(step, -delta), ZOOM_MIN, getZoomMax());
        zoomAnchorAtCursor = true;
        glideActive = false;
        if (delta > 0)
            overviewActive = false; // wheeling in from the overview is just zooming
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render evt) {
        if (!enabled || !(evt.getScreen() instanceof TopdownGui)) {
            return;
        }
        // GLFW coords seem to be 2x vanilla coords, but use only them for consistency
        // since we need to use glfwSetCursorPos
        long glfwWindow = MC.getWindow().getWindow();
        int glfwWinWidth = MC.getWindow().getScreenWidth();
        int glfwWinHeight = MC.getWindow().getScreenHeight();
        double cursorX = MC.mouseHandler.xpos();
        double cursorY = MC.mouseHandler.ypos();

        // lock mouse inside window (edge panning itself happens per frame in updateCamera)
        if (cursorX >= glfwWinWidth) {
            GLFW.glfwSetCursorPos(glfwWindow, glfwWinWidth, cursorY);
        }
        if (cursorY >= glfwWinHeight) {
            GLFW.glfwSetCursorPos(glfwWindow, cursorX, glfwWinHeight);
        }
        if (cursorX <= 0) {
            GLFW.glfwSetCursorPos(glfwWindow, 0, cursorY);
        }
        if (cursorY <= 0) {
            GLFW.glfwSetCursorPos(glfwWindow, cursorX, 0);
        }
    }

    // prevents stuff like fire and water effects being shown on your HUD
    @SubscribeEvent
    public static void onRenderBlockOverlay(RenderBlockScreenEffectEvent evt) {
        if (isEnabled()) {
            evt.setCanceled(true);
        }
    }

    // don't let orthoview players see other orthoview players or themselves
    @SubscribeEvent
    public static void onPlayerRender(RenderPlayerEvent.Pre evt) {
        if (enabled && (evt.getEntity().isSpectator() || evt.getEntity().isCreative())) {
            evt.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFovModifier(ViewportEvent.ComputeFov evt) {
        if (isEnabled()) {
            evt.setFOV(180);
        }
    }

    // distance fog is meaningless for a top-down orthographic camera and whites out the edges of the view
    // when zoomed out, so push it beyond anything visible (fluids keep their own fog)
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog evt) {
        if (!isEnabled() || evt.getType() != FogType.NONE) {
            return;
        }
        evt.setNearPlaneDistance(10000f);
        evt.setFarPlaneDistance(12000f);
        evt.setCanceled(true);
    }

    // Beyond the loaded terrain the RTS camera used to show raw sky: bright blue corners and a flat blue sheet past
    // the render distance when zoomed out (clip 15-56). The fog colour is also the frame's clear colour, so in the
    // RTS view it becomes a dark, desaturated ground tone (the sky itself is not drawn - see LevelRendererSkyMixin)
    // and the map edge reads as unexplored land. Dims a little with the (floored) night, like the terrain does.
    static final float EDGE_R = 0.17f, EDGE_G = 0.19f, EDGE_B = 0.14f;
    // the edge tone last set, so the world look's haze can tell it from real atmosphere (postfx/WorldLookPass)
    private static float edgeFogR = -1, edgeFogG = -1, edgeFogB = -1;

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor evt) {
        if (!isEnabled() || evt.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float light = MC.level != null ? Mth.clamp(MC.level.getSkyDarken((float) evt.getPartialTick()), 0.6f, 1f) : 1f;
        edgeFogR = EDGE_R * light;
        edgeFogG = EDGE_G * light;
        edgeFogB = EDGE_B * light;
        evt.setRed(edgeFogR);
        evt.setGreen(edgeFogG);
        evt.setBlue(edgeFogB);
    }

    /**
     * Whether a fog colour is exactly this frame's map-edge tone - i.e. nothing after onFogColor (a realm map's haze in
     * MapClient, another mod) replaced it. Exact float compare: the value reaches the shader fog colour unchanged.
     */
    public static boolean isEdgeFogColour(float r, float g, float b) {
        return r == edgeFogR && g == edgeFogG && b == edgeFogB;
    }

    /**
     * The RTS view's depth range, in blocks along the view direction from the camera (negative = behind it). Window
     * depth runs linearly from 0 at ORTHO_Z_NEAR to 1 at ORTHO_Z_FAR. Shader packs (Iris / Oculus: Complementary,
     * Photon, Bliss...) treat any depth below 0.56 as the first-person HAND - no ambient occlusion, no TAA, no
     * reflections; with the old symmetric -3000..3000 range every block within 360 of the camera was "hand", which is
     * the whole battlefield (Oct 15 shader research). So the range is lopsided: everything from SCENE_NEAREST (the
     * nearest scene point a wide, low view can show - the camera sits 30-100 above the ground) to the far plane lands
     * at depth >= 0.6, and the geometry nearer still renders (as before, just shallower).
     * SCENE_NEAREST covers the worst case: the ground at the bottom edge of a full zoom-out (ZOOM_MAX_HARD 200) at the
     * shallowest tilt (CAMROTY_MAX, 12 degrees) sits at H / sin(p) - (Z / 2) / tan(p) along the view - about -326 for a
     * camera 30 above the ground, bounded by (200 / 2) / tan(12) ~ 470; -150 left that strip below 0.56 ("hand"
     * again). Near plane -2750: the 24-bit depth step is still ~0.0002 blocks.
     */
    private static final float SCENE_NEAREST = -500f, ORTHO_Z_FAR = 1000f;
    private static final float SCENE_DEPTH = 0.6f;
    private static final float ORTHO_Z_NEAR = SCENE_NEAREST - SCENE_DEPTH / (1f - SCENE_DEPTH) * (ORTHO_Z_FAR - SCENE_NEAREST);

    // OrthoViewMixin returns this in place of the perspective projection while the RTS view is on
    public static Matrix4f getOrthographicProjection() {
        int width = MC.getWindow().getScreenWidth();
        int height = MC.getWindow().getScreenHeight();

        float zoomFinal = zoom;

        float wView = (zoomFinal / height) * width;
        float left = -wView / 2;
        float rgt = wView / 2;

        float top = zoomFinal / 2;
        float bot = -zoomFinal / 2;

        // BarFx camera shake: nudge the view a little on big nearby blasts
        // playtest (Oct 10): shaking grew with zoom (x3 zoomed out) and a big fight shook the whole screen.
        // Keep it a small fixed nudge, and none at all in strategic view.
        float shakeScale = com.solegendary.reignofnether.orthoview.StrategicViewClientEvents.isStrategicView() ? 0f : .4f;
        float shX = com.solegendary.reignofnether.barfx.BarFxClient.shakeX() * shakeScale;
        float shY = com.solegendary.reignofnether.barfx.BarFxClient.shakeY() * shakeScale;
        left += shX; rgt += shX; top += shY; bot += shY;

        // JOML builds it (the old code wrote a row-major matrix into JOML's column-major 16-float constructor, so the
        // x/y offsets landed in the w row: harmless while centred, but every camera shake turned the view into a
        // perspective warp). A new matrix each call, as before: the caller keeps the one it gets.
        return new Matrix4f().setOrtho(left, rgt, bot, top, ORTHO_Z_NEAR, ORTHO_Z_FAR);
    }
}
