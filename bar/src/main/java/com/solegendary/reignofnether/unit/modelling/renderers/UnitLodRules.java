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

    /**
     * An overlay layer (team cloth, glow, eyes, upgrade glow) of a unit is skipped: only while a shader pack draws
     * its shadow map, and only with the LOD on.
     */
    public static boolean skipOverlayLayer(boolean lodOn, boolean isUnit, boolean shadowPass) {
        return lodOn && isUnit && shadowPass;
    }
}
