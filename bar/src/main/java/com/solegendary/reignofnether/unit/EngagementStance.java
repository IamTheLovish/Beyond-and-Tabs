package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * BAR's fire state, one of the command-card cycling toggles: Hold fire (never pick a target on its own - only an
 * explicit attack order makes it shoot), Return fire (only engage whatever is shooting at it) and Fire at will (RoN's
 * usual behaviour: idle aggro, attack-move acquisition, retargeting). Read by the auto-acquisition paths in
 * {@link AttackerUnit#tick} and {@link Unit#aggroToEnemyIfIdle}; set through the SET_STANCE_* unit actions, which
 * run on both sides, so the client knows the stance without a sync packet (same as hold position).
 *
 * Stored in the entity's persistent data rather than a field, so no unit class has to change; written into the
 * unit's own save data too ({@link Unit#addUnitSaveData}) so a reload keeps it. Default is Fire at will.
 */
public enum EngagementStance {
    HOLD_FIRE("hold_fire"),
    RETURN_FIRE("return_fire"),
    FIRE_AT_WILL("fire_at_will");

    public static final String KEY = "bt_stance";
    public static final EngagementStance DEFAULT = FIRE_AT_WILL;
    private static final EngagementStance[] VALUES = values();

    public final String key;

    EngagementStance(String key) {
        this.key = key;
    }

    public static EngagementStance byOrdinal(int i) {
        return i < 0 || i >= VALUES.length ? DEFAULT : VALUES[i];
    }

    public static EngagementStance of(LivingEntity le) {
        CompoundTag data = le.getPersistentData();
        return data.contains(KEY) ? byOrdinal(data.getInt(KEY)) : DEFAULT;
    }

    public static void set(LivingEntity le, EngagementStance stance) {
        le.getPersistentData().putInt(KEY, stance.ordinal());
    }

    /** The next state in BAR's click order: Hold fire -> Return fire -> Fire at will -> Hold fire. */
    public EngagementStance next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public EngagementStance previous() {
        return VALUES[(ordinal() + VALUES.length - 1) % VALUES.length];
    }

    public UnitAction action() {
        return switch (this) {
            case HOLD_FIRE -> UnitAction.SET_STANCE_HOLD_FIRE;
            case RETURN_FIRE -> UnitAction.SET_STANCE_RETURN_FIRE;
            case FIRE_AT_WILL -> UnitAction.SET_STANCE_FIRE_AT_WILL;
        };
    }

    public static EngagementStance fromAction(UnitAction action) {
        return switch (action) {
            case SET_STANCE_HOLD_FIRE -> HOLD_FIRE;
            case SET_STANCE_RETURN_FIRE -> RETURN_FIRE;
            default -> FIRE_AT_WILL;
        };
    }

    public static boolean isStanceAction(UnitAction action) {
        return action == UnitAction.SET_STANCE_HOLD_FIRE || action == UnitAction.SET_STANCE_RETURN_FIRE
            || action == UnitAction.SET_STANCE_FIRE_AT_WILL;
    }

    /** True when the unit may pick targets on its own (idle aggro, attack-move, retargeting, guard threats). */
    public static boolean mayAutoAcquire(LivingEntity le) {
        return of(le) == FIRE_AT_WILL;
    }

    /** True when the unit may shoot back at whatever just hit it. */
    public static boolean mayRetaliate(LivingEntity le) {
        return of(le) != HOLD_FIRE;
    }

    public static void save(LivingEntity le, CompoundTag tag) {
        EngagementStance s = of(le);
        if (s != DEFAULT)
            tag.putInt(KEY, s.ordinal());
    }

    public static void load(LivingEntity le, CompoundTag tag) {
        if (tag.contains(KEY))
            set(le, byOrdinal(tag.getInt(KEY)));
    }
}
