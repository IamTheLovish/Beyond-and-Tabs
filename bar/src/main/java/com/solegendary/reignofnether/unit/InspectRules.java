package com.solegendary.reignofnether.unit;

/**
 * Inspecting units and buildings you don't control (tester request Oct 26: "select enemy units to pull up their
 * information just like your own, you just can't command them ... it shouldn't show the abilities for heroes").
 * Pure rules, no Minecraft client classes: the client selection / HUD code asks these, and the game tests and JUnit
 * tests check the same answers.
 * <ul>
 *   <li>Only what the player can see right now can be inspected: lit by fog of war, not cloaked / in a thicket from
 *       this side, not riding something. The same test drops an inspected unit that walks into the dark.</li>
 *   <li>Only controllable things take orders and get a command card (own, an ally who shared control, sandbox
 *       all-mobs). An inspected enemy never rides along in a group: a selection of 2+ keeps controllable ones only.</li>
 *   <li>Public info (portrait, name, health, stats, building type) shows for everyone; plan info (abilities and
 *       their cooldowns, items, rally) only for own/controlled and allied things. Spectators and sandbox players see
 *       every card in full.</li>
 * </ul>
 */
public final class InspectRules {
    private InspectRules() { }

    /** What a unit or building card can show. */
    public enum Info {
        PORTRAIT, NAME, HEALTH, STATS, BUILDING_TYPE,   // public: anyone who can see it
        ABILITIES, ITEMS, RALLY                         // plans: own, controlled or allied only
        // production queue, autocast and the command card keep their own HUD gates (HudClientEvents)
    }

    /** The small label on the card of something that isn't yours. */
    public enum Tag {
        NONE(null, 0), ALLY("Ally", 0xFF55C8FF), ENEMY("Enemy", 0xFFFF5555), NEUTRAL("Neutral", 0xFFC8C8C8);

        public final String label;
        public final int colour;

        Tag(String label, int colour) {
            this.label = label;
            this.colour = colour;
        }
    }

    /**
     * The viewer's relation to a unit owned by {@code ownerName}, the same answer as the client's
     * UnitClientEvents.getPlayerToEntityRelationship for a unit: own first (OWNED, so own cards keep their command
     * card), then ally, else enemy; no owner is neutral. Not the server's unit-to-unit relation
     * (UnitServerEvents.getUnitToEntityRelationship), which calls a same-owner pair FRIENDLY.
     */
    public static Relationship relation(String viewerName, String ownerName, boolean allied) {
        if (ownerName == null || ownerName.isBlank())
            return Relationship.NEUTRAL;
        if (ownerName.equals(viewerName))
            return Relationship.OWNED;
        if (allied)
            return Relationship.FRIENDLY;
        return Relationship.HOSTILE;
    }

    /** Can the local player see this well enough to select it for inspection (or keep it inspected)? */
    public static boolean mayInspect(boolean inBrightChunk, boolean hiddenFromViewer, boolean passenger) {
        return inBrightChunk && !hiddenFromViewer && !passenger;
    }

    /** May the local player order it (own, an ally who shared control, or sandbox control of all mobs)? */
    public static boolean canCommand(Relationship rel, boolean alliedControl, boolean controlAllMobs) {
        return rel == Relationship.OWNED || alliedControl || controlAllMobs;
    }

    /** Does the card show this piece of info for a thing in relation {@code rel} to the viewer? */
    public static boolean shows(Info info, Relationship rel, boolean alliedControl, boolean controlAllMobs) {
        return switch (info) {
            case PORTRAIT, NAME, HEALTH, STATS, BUILDING_TYPE -> true;
            case ABILITIES, ITEMS, RALLY -> canCommand(rel, alliedControl, controlAllMobs) || rel == Relationship.FRIENDLY;
        };
    }

    /** Does this viewer see every card in full (sandbox players, and spectators / non-RTS players)? */
    public static boolean seesEverything(boolean rtsPlayer, boolean sandboxPlayer) {
        return !rtsPlayer || sandboxPlayer;
    }

    /** May this stay in a selection of {@code selectionSize} things? A lone inspected thing may; a group is orders. */
    public static boolean keepInSelection(int selectionSize, boolean canCommand) {
        return selectionSize <= 1 || canCommand;
    }

    /** The card's tag: none for own things, else Ally / Enemy / Neutral. */
    public static Tag tag(Relationship rel) {
        return switch (rel) {
            case OWNED -> Tag.NONE;
            case FRIENDLY -> Tag.ALLY;
            case HOSTILE -> Tag.ENEMY;
            case NEUTRAL -> Tag.NEUTRAL;
        };
    }
}
