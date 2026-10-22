package com.solegendary.reignofnether.maps.gen;

/**
 * Plane distances for the generator: sqrt(x^2 + z^2). Math.hypot guards against overflow the generator never meets,
 * costs several times as much in the per-column loops, and is only specified to within one ulp, so it is not
 * guaranteed to agree between a player's JVM and the server's; Math.sqrt is correctly rounded everywhere.
 */
final class Geo {
    private Geo() { }

    static double len(double x, double z) {
        return Math.sqrt(x * x + z * z);
    }
}
