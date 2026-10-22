package com.solegendary.reignofnether.maps.gen;

/**
 * A seat's character on a generated map (balanced asymmetry, lovish Oct 21 21:47: "different split sides that even it
 * out"; 22:28: "give one side more neutral objectives with the other side giving more mexes"). Contrasts pair them:
 * PROSPECTOR / WARDEN, OPEN / BASTION, MOATED / BANKS (a river splits the map), PLAIN for the symmetric control.
 *
 * @param letter     one letter for render sheets and logs
 * @param holeTarget the open-ground fill places patches until no reachable ground in the seat's territory is farther
 *                   than this from a patch (smaller = denser metal)
 * @param ownSites   capture sites placed on the seat's own forward ground (safe for it, reachable by everyone)
 * @param baseTier   the tier the base pad stands on (0 = lowland, 1 = a plateau +4 above it)
 * @param plateau    the pad is ringed by a cliff, reached only by its ramps (defensible, few approaches)
 * @param smooth     the ground round the base has no cliffs (open, many approaches)
 */
public enum Archetype {
    PLAIN('P', 37, 0, 0, false, false),
    PROSPECTOR('$', 29, 0, 0, false, false),
    WARDEN('W', 46, 2, 0, false, false),
    OPEN('O', 31, 0, 0, false, true),
    BASTION('B', 43, 0, 1, true, false),
    MOATED('M', 40, 0, 0, false, false),
    BANKS('K', 34, 0, 0, false, false);

    public final char letter;
    public final double holeTarget;
    public final int ownSites;
    public final int baseTier;
    public final boolean plateau;
    public final boolean smooth;

    Archetype(char letter, double holeTarget, int ownSites, int baseTier, boolean plateau, boolean smooth) {
        this.letter = letter;
        this.holeTarget = holeTarget;
        this.ownSites = ownSites;
        this.baseTier = baseTier;
        this.plateau = plateau;
        this.smooth = smooth;
    }
}
