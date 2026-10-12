package com.solegendary.reignofnether.onboarding;

/**
 * The "First Battle" tutorial's steps, in play order (design/onboarding.md). Each finishes on the real action:
 * <ul>
 *   <li>{@link Kind#INFO} - text only; the player presses Continue;</li>
 *   <li>{@link Kind#CLIENT} - something only the client can see (camera, selection): the client watches for it and
 *       reports it, and the server accepts the report only for the step that is current;</li>
 *   <li>{@link Kind#SERVER} - an order, a building, a unit, a reclaim, a D-gun shot or a kill: the server sees it
 *       happen in the world ({@link OnboardingServer}).</li>
 * </ul>
 * Order matters to the player, not to the code: the economy comes before the army steps so attack-move is learnt
 * with real fighters, not workers. Saved by name (OnboardingSaveData), so steps can be inserted later without breaking
 * a half-finished tutorial save; a name that no longer exists resumes at the start.
 */
public enum OnboardingStep {
    WELCOME(Kind.INFO, Highlight.NONE),
    ZOOM(Kind.CLIENT, Highlight.NONE),
    PAN(Kind.CLIENT, Highlight.NONE),
    ROTATE(Kind.CLIENT, Highlight.NONE),
    OVERVIEW(Kind.CLIENT, Highlight.NONE),
    SELECT(Kind.CLIENT, Highlight.OWN_COMMANDER),
    BOX_SELECT(Kind.CLIENT, Highlight.NONE),
    MOVE(Kind.SERVER, Highlight.NONE),
    FORMATION(Kind.SERVER, Highlight.NONE),
    EXTRACTOR(Kind.SERVER, Highlight.METAL_PATCH),
    ENERGY(Kind.SERVER, Highlight.NONE),
    LAB(Kind.SERVER, Highlight.NONE),
    TRAIN(Kind.SERVER, Highlight.NONE),
    ATTACK_MOVE(Kind.SERVER, Highlight.NONE),
    RECLAIM(Kind.SERVER, Highlight.WRECKS),
    DGUN(Kind.SERVER, Highlight.OWN_COMMANDER),
    KILL_COMMANDER(Kind.SERVER, Highlight.ENEMY_COMMANDER),
    DONE(Kind.INFO, Highlight.NONE);

    public enum Kind { INFO, CLIENT, SERVER }

    /** What the world highlight marks during the step (the server sends its position). */
    public enum Highlight { NONE, OWN_COMMANDER, METAL_PATCH, WRECKS, ENEMY_COMMANDER }

    public final Kind kind;
    public final Highlight highlight;

    OnboardingStep(Kind kind, Highlight highlight) {
        this.kind = kind;
        this.highlight = highlight;
    }

    public OnboardingStep next() {
        return this == DONE ? DONE : values()[ordinal() + 1];
    }

    /** 1-based number among the steps the player does (WELCOME and DONE are not counted). */
    public int number() {
        return Math.max(1, Math.min(ordinal(), total()));
    }

    public static int total() {
        return values().length - 2;
    }

    public String titleKey() {
        return "onboarding.reignofnether.step." + name().toLowerCase() + ".title";
    }

    public String bodyKey() {
        return "onboarding.reignofnether.step." + name().toLowerCase() + ".body";
    }

    /** By name, or WELCOME for an unknown one (a step renamed since the save). */
    public static OnboardingStep byName(String name) {
        if (name != null)
            for (OnboardingStep s : values())
                if (s.name().equals(name))
                    return s;
        return WELCOME;
    }

    public static OnboardingStep byOrdinal(int i) {
        return i >= 0 && i < values().length ? values()[i] : WELCOME;
    }
}
