package com.solegendary.reignofnether.building;

/**
 * Placing a building over fogged / never-seen ground (tester, Oct 26: "when you are placing a building the blueprint
 * should follow your cursor, it shouldn't be invisible"). Pure, so JUnit and GameTests can check it.
 *
 * <p>The cause: a chunk the client was never sent (beyond view distance, or a dark player's chunk with no pre-game
 * snapshot: ChunkMapInitialSendMixin cancels the live packet) reads as air, so the cursor ray
 * (CursorClientEvents.clip) misses. A miss came back as the ray's far end, 200 blocks past the cursor and deep
 * underground, and the ghost was drawn there. Now a miss lands the cursor on the horizontal plane of the last real
 * hit: the ghost follows the cursor at the height the player last saw. It uses no block of the hidden chunk, so it
 * reveals nothing (the client has none to reveal); the server judges the site with real terrain when the worker
 * gets there (BuildingServerEvents.placeBuilding: fog queue, then the normal validity check).
 */
public final class FogPlacement {

    private FogPlacement() {}

    /** The client's "footprint not loaded" error key (BuildingValidators): ground the client has no blocks for. */
    public static final String NOT_LOADED = "building.reignofnether.not_loaded";

    /**
     * Where the ray {@code o + t*d} crosses the plane {@code y = planeY}, or null when it never does going forward
     * (parallel to it, or the plane is behind the ray's start).
     */
    public static double[] rayToPlaneY(double ox, double oy, double oz, double dx, double dy, double dz, double planeY) {
        if (Math.abs(dy) < 1e-9)
            return null;
        double t = (planeY - oy) / dy;
        if (t < 0)
            return null;
        return new double[] { ox + t * dx, planeY, oz + t * dz };
    }

    /**
     * Does the client leave this placement to the server? Only when the site is in fog and the client's sole
     * objection is that it has no blocks there: it cannot judge ground it was never sent, so the order goes out and
     * the server checks the real terrain. Every other error (or any error on visible ground) still refuses at once.
     */
    public static boolean clientDefers(String errorKey, boolean inFog) {
        return inFog && NOT_LOADED.equals(errorKey);
    }
}
