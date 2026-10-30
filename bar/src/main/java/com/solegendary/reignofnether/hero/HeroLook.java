package com.solegendary.reignofnether.hero;

/**
 * How a hero is told apart in a fight (design-draft-heroes-better-2026-10-30 #4; lovish, Oct 30: "Ring + crest,
 * 1.25x"): the rules the client draws by, in common code so the server's GameTests hold them (HeroMarkerGameTests).
 * The drawing is HeroMarkerClientEvents (a player-colour ring round the feet, a crest over the head) and the hero
 * renderers' scale (HeroRenderers.HERO_SCALE).
 */
public final class HeroLook {
    private HeroLook() { }

    /** Every faction hero is drawn this much bigger than the unit its body is built on (was 1.1). */
    public static final float SCALE = 1.25f;

    /**
     * The ring and the crest: on heroes only, only in the RTS view (never in first person), and only on a hero the
     * viewer can see - in sight (not under the fog of war) and not cloaked from them (an own or allied cloaked hero is
     * seen by its side and keeps its marker). A marker on a hero in the fog would give away its exact spot (review r1 on
     * PR 424).
     */
    public static boolean showMarker(boolean isHero, boolean rtsView, boolean inSight, boolean hiddenFromViewer) {
        return isHero && rtsView && inSight && !hiddenFromViewer;
    }

    /**
     * The "+" badge beside the crest: the viewer's own hero holding unspent ability points (design draft #1's badge,
     * so a point waiting for the player - or for auto-learn's 20 s, PR 415 - shows without selecting the hero).
     */
    public static boolean showPointBadge(boolean ownHero, int skillPoints) {
        return ownHero && skillPoints > 0;
    }

    /** The ring's radius round a hero whose hitbox is {@code width} wide: just outside its drawn (scaled) body. */
    public static float ringRadius(float width) {
        return width * SCALE / 2 + 0.3f;
    }

    /** Blocks above the hero's feet the crest floats, for a hitbox {@code height} tall: clear of the drawn head. */
    public static float crestHeight(float height) {
        return height * SCALE + 0.45f;
    }
}
