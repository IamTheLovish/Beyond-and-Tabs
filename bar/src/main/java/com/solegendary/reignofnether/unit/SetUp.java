package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.FieldArtillery;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Set-up time (lovish, Oct 24: massed shell artillery formed an unapproachable death ball): the field artillery
 * ({@link FieldArtillery#getSetUpTicks()}) fires only once it has stood still that long since it last moved.
 * Every firing path asks {@link #isSetUp} before it spends the shot - the bow attack goal, Attack Ground and the
 * building attack hold the shot (and keep their cooldown) until the unit is set up, and the units' own fire methods
 * refuse too, for possession and anything that calls them directly.
 * <p>
 * "Moving": navigating with a real step this tick ({@link #STEP}, a walk is 0.1-0.25 a tick; a stalled path is not a
 * move), or ending up more than {@link #DRIFT} from where it last stood - a possessed unit walked by hand, a teleport.
 * A knockback or a crowd shove smaller than that never costs the set-up: being shot at is not moving.
 * <p>
 * Server side, from Unit.tick; one map entry per artillery unit, nothing for anyone else. A unit never ticked yet (just
 * spawned where it stands) counts as set up.
 */
public final class SetUp {
    private SetUp() { }

    /** lovish's 2 s for the field artillery. */
    public static final int ARTILLERY_TICKS = 40;
    /** A navigating unit that moves more than this in a tick (horizontal) is walking. */
    static final double STEP = 0.02;
    /** Displaced this far (horizontal) from where it last stood, it has moved whatever moved it. */
    static final double DRIFT = 1.5;

    private static final class State {
        double anchorX, anchorZ, lastX, lastZ;
        long movedAt = Long.MIN_VALUE / 2;
    }

    private static final Map<Mob, State> STATE = new WeakHashMap<>();

    static int setUpTicks(Entity e) {
        return e instanceof FieldArtillery f ? f.getSetUpTicks() : 0;
    }

    /** Records whether the unit moved this tick. Server side, once per tick (Unit.tick). */
    public static void tick(Mob mob) {
        if (mob.level().isClientSide() || setUpTicks(mob) <= 0)
            return;
        double x = mob.getX(), z = mob.getZ();
        State s = STATE.get(mob);
        if (s == null) {
            s = new State();
            s.anchorX = s.lastX = x;
            s.anchorZ = s.lastZ = z;
            STATE.put(mob, s);
            return;
        }
        double sx = x - s.lastX, sz = z - s.lastZ;
        double ax = x - s.anchorX, az = z - s.anchorZ;
        boolean walking = !mob.getNavigation().isDone() && sx * sx + sz * sz > STEP * STEP;
        if (walking || ax * ax + az * az > DRIFT * DRIFT) {
            s.movedAt = mob.level().getGameTime();
            s.anchorX = x;
            s.anchorZ = z;
        }
        s.lastX = x;
        s.lastZ = z;
    }

    /** Ticks left before this unit may fire; 0 when it is set up (or needs no set-up). */
    public static int ticksLeft(Entity e) {
        int need = setUpTicks(e);
        if (need <= 0 || !(e instanceof Mob mob))
            return 0;
        State s = STATE.get(mob);
        if (s == null)
            return 0;
        long since = mob.level().getGameTime() - s.movedAt;
        return since >= need ? 0 : (int) (need - since);
    }

    /** May this unit fire now (it has stood still for its set-up time, or needs none)? */
    public static boolean isSetUp(Entity e) {
        return ticksLeft(e) == 0;
    }
}
