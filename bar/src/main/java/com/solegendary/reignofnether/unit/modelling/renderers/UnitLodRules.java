package com.solegendary.reignofnether.unit.modelling.renderers;

/**
 * The unit render level-of-detail rules as pure arithmetic (no Minecraft classes), so a JUnit test pins them
 * (test/.../UnitLodRulesTest). {@link UnitLod} feeds them the live camera; every threshold lives here.
 *
 * What a unit's draw costs and where it goes (design/animation-foundation-audit.md section 4, PR #84's bench): posing
 * is ~1 us a unit (1-2% of its frame cost); the draw - the part-tree walk and the CPU vertex transform, once per pass -
 * is the rest. With a shader pack every unit is drawn twice, once more into the pack's shadow map. So the levers are
 * passes and parts, measured in on-screen size, not animation rate:
 * <ul>
 *   <li><b>Shadow pass, overlay layers</b> ({@link #skipOverlayLayer}): the team-colour cloth, the generated bodies'
 *       _glow pass, the eyes/glow layers and the upgrade glow re-draw parts of the body on the body's own surface.
 *       The body pass already writes exactly that silhouette into the shadow map, so these passes add no shadow
 *       texel - they are skipped there at every distance (and add a faint additive tint to a pack's coloured
 *       shadows at most). Up close nothing changes on screen.</li>
 *   <li><b>Detail parts</b> ({@link #firstPersonDetail}): the small "lod" parts of the generated bodies. The RTS
 *       camera keeps its zoom switch (GeneratedBodies.DETAIL_ZOOM: orthographic, so every unit is the same size on
 *       screen and one switch is already a projected-size rule). Outside the RTS view the old rule was a raw 40
 *       blocks; it is now the same on-screen size - pixels per block at the unit - pinned so a 1080p screen at FOV 70
 *       switches at exactly 40 blocks as before. A higher-resolution screen or a narrower FOV (spyglass, possession
 *       zoom) keeps detail further out, where it is still big enough to see.</li>
 * </ul>
 * Animation pose decimation was looked at and left out on purpose: a renderer's model is shared by every unit of
 * its type, so reusing a pose means saving and restoring every part's transform, which costs what posing does
 * (~1 us); and at the frame rates of a big fight (25-35 fps) any skipped pose drops a unit under 20 Hz, which shows
 * as stutter. The audit reached the same verdict ("no animation LOD").
 */
public final class UnitLodRules {
    private UnitLodRules() { }

    /** The screen the old fixed switches were tuned on: 1080 px tall, Minecraft's default FOV 70. */
    public static final double REF_SCREEN_HEIGHT_PX = 1080.0;
    public static final double REF_FOV_DEG = 70.0;
    /** The old first-person detail switch (GeneratedBodies.DETAIL_DISTANCE), the reference the size rule is pinned to. */
    public static final double LEGACY_DETAIL_DISTANCE = 40.0;

    /**
     * Detail parts outside the RTS view need at least this many screen pixels per block at the unit: what 40 blocks
     * gives on the reference screen (~19.3 px per block - a 0.2-block buckle is ~4 px there).
     */
    public static final double DETAIL_MIN_PX_PER_BLOCK = pxPerBlockPerspective(REF_SCREEN_HEIGHT_PX, REF_FOV_DEG,
            LEGACY_DETAIL_DISTANCE);

    /** Screen pixels per block for an orthographic view {@code zoomBlocks} tall (the RTS camera's zoom). */
    public static double pxPerBlockOrtho(double screenHeightPx, double zoomBlocks) {
        return screenHeightPx / Math.max(1e-3, zoomBlocks);
    }

    /** Screen pixels per block at {@code distance} blocks from a perspective camera with vertical FOV {@code fovDeg}. */
    public static double pxPerBlockPerspective(double screenHeightPx, double fovDeg, double distance) {
        double halfFov = Math.toRadians(Math.max(1.0, Math.min(179.0, fovDeg))) / 2.0;
        return screenHeightPx / (2.0 * Math.max(1e-3, distance) * Math.tan(halfFov));
    }

    /** First person: the unit's small detail parts are drawn when they are big enough on screen to see. */
    public static boolean firstPersonDetail(double pxPerBlock) {
        return pxPerBlock >= DETAIL_MIN_PX_PER_BLOCK;
    }

    /** Old rule, kept for the LOD-off A/B: a raw distance. */
    public static boolean legacyFirstPersonDetail(double distance) {
        return distance < LEGACY_DETAIL_DISTANCE;
    }

    // ---- strategic icons instead of models, by crowd (Oct 23: 120 fps at <60 units, 55-75 at 450-575) ----

    /** Up to this many known units the icon switch stays where it always was (StrategicViewClientEvents.STRATEGIC_ZOOM_ENTER). */
    public static final int CROWD_START_UNITS = 200;
    /** From here on the switch has moved as far as it goes. */
    public static final int CROWD_FULL_UNITS = 600;
    /** The switch never moves in further than this share of the player's threshold: 70 -> 49, safely above close zoom. */
    public static final float CROWD_MIN_FACTOR = 0.7f;
    /** The factor moves in steps of this much (a step is 3.5 zoom at 70, inside the 8 zoom hysteresis band). */
    private static final float CROWD_STEP = 0.05f;
    /** Never scale a threshold the player set closer than this: it is already near the detail zoom. */
    private static final float CROWD_MIN_ENTER = 48f;

    /** 1 for a small army, falling in steps to {@link #CROWD_MIN_FACTOR} as the known units grow to {@link #CROWD_FULL_UNITS}. */
    public static float crowdFactor(int units) {
        if (units <= CROWD_START_UNITS)
            return 1f;
        float t = Math.min(1f, (units - CROWD_START_UNITS) / (float) (CROWD_FULL_UNITS - CROWD_START_UNITS));
        float f = 1f - t * (1f - CROWD_MIN_FACTOR);
        return Math.max(CROWD_MIN_FACTOR, Math.round(f / CROWD_STEP) * CROWD_STEP);
    }

    /**
     * The zoom (view height in blocks) past which units draw as icons: the player's threshold, brought in as the
     * crowd grows because a crowd's models are what the frame pays for. Quantised so a unit count that jitters does not
     * flip the view; never below {@code min(threshold, CROWD_MIN_ENTER)}.
     */
    public static float iconEnterZoom(float threshold, int units) {
        return Math.max(threshold * crowdFactor(units), Math.min(threshold, CROWD_MIN_ENTER));
    }

    /**
     * A unit's model is not drawn this frame: strategic view, the unit is not the HUD portrait's (that render feeds
     * the portrait) and not a big unit still before its later switch. One rule for the render event
     * (StrategicViewClientEvents.onRenderLiving) and for the entity dispatcher's cull in front of it.
     */
    public static boolean skipUnitModel(boolean strategic, boolean strategicBig, boolean isUnit, boolean isBig,
                                        boolean isHudSelected) {
        return strategic && isUnit && !isHudSelected && !(isBig && !strategicBig);
    }

    /**
     * An overlay layer (team cloth, glow, eyes, upgrade glow) of a unit is skipped: only while a shader pack draws
     * its shadow map, and only with the LOD on.
     */
    public static boolean skipOverlayLayer(boolean lodOn, boolean isUnit, boolean shadowPass) {
        return lodOn && isUnit && shadowPass;
    }
}
