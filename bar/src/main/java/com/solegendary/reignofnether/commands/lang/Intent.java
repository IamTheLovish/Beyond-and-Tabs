package com.solegendary.reignofnether.commands.lang;

/**
 * One order the command language understood: who, what, to what, where, and whether it queues behind the clause
 * before it ("stop and build anti-air here" = STOP, then BUILD queued). The executor (client) turns these into the
 * same calls the hotkeys make; nothing here knows Minecraft.
 */
public record Intent(Group group, Verb verb, String object, Place place, boolean queued) {

    public enum Verb {
        SELECT, STOP, HOLD, MOVE, ATTACK_MOVE, PATROL, RETREAT, GUARD, BUILD, REPAIR, RECLAIM, GATHER,
        USE_ABILITY, AUTOCAST_ON, AUTOCAST_OFF, SAVE_GROUP, FOCUS;

        /** Verbs that act on something the player may not want to lose: never guessed from a near-miss. */
        public boolean destructive() {
            return this == RECLAIM;
        }
    }

    /** Who the order is for. {@code name} is the unit type for UNIT_TYPE, {@code number} the control group. */
    public record Group(Kind kind, String name, int number) {
        public enum Kind { ALL_WORKERS, IDLE_WORKERS, WORKERS, SOLDIERS, EVERYONE, SELECTION, UNITS_HERE, CONTROL_GROUP, UNIT_TYPE, HERO }

        public static Group of(Kind kind) { return new Group(kind, null, -1); }
        public static Group type(String name) { return new Group(Kind.UNIT_TYPE, name, -1); }
        public static Group control(int n) { return new Group(Kind.CONTROL_GROUP, null, n); }
        public static final Group SELECTION = of(Kind.SELECTION);
    }

    /** Where. {@code direction} and {@code reference} are set for DIRECTION_OF ("north of the capitol"). */
    public record Place(Kind kind, String direction, String reference) {
        public enum Kind { NONE, HERE, THERE, NEAR_YOU, AT_CAPITOL, AT_FRONT, HOME, DIRECTION_OF }

        public static Place of(Kind kind) { return new Place(kind, null, null); }
        public static final Place NONE = of(Kind.NONE);
    }

    public Intent withQueued(boolean q) { return new Intent(group, verb, object, place, q); }
    public Intent withGroup(Group g) { return new Intent(g, verb, object, place, queued); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(group.kind());
        if (group.name() != null) sb.append('(').append(group.name()).append(')');
        if (group.number() >= 0) sb.append('(').append(group.number()).append(')');
        sb.append(' ').append(verb);
        if (object != null) sb.append(' ').append(object);
        if (place.kind() != Place.Kind.NONE) {
            sb.append(" @").append(place.kind());
            if (place.direction() != null) sb.append('(').append(place.direction()).append(" of ").append(place.reference()).append(')');
        }
        if (queued) sb.append(" [queued]");
        return sb.toString();
    }
}
