package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.unit.UnitAction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * One player's First Battle: which step they are on, who their sparring partner is and the little bits of state the
 * step checks need. Pure logic (no level access), so the step rules are testable on their own and each student is
 * independent - a shared LAN world, or parallel game tests, never cross wires.
 */
public class OnboardingSession {

    /** A formation drag sends one single-unit order per slot in the same tick or two (UnitClientEvents). */
    static final int FORMATION_WINDOW_TICKS = 2;

    public final String student;
    public String enemy = "";
    public OnboardingStep step = OnboardingStep.WELCOME;
    public BlockPos home = BlockPos.ZERO;
    public BlockPos enemyHome = BlockPos.ZERO;

    // per-step state, reset on every step change
    /** Fighters owned when TRAIN began: two more than this finishes it. */
    int baseline = -1;
    /** The step's world setup (top-up, wreck field) has run. */
    boolean stepPrepared = false;
    /** A matching building is placed but not finished yet: the panel says "building...". */
    boolean inProgress = false;
    /** The enemy commander has been seen alive in this step (so "none left" means it died, not "not loaded yet"). */
    boolean enemyCommanderSeen = false;
    BlockPos highlight = null;

    // formation detection: single-unit move orders to distinct spots within FORMATION_WINDOW_TICKS
    private long formationTick = Long.MIN_VALUE;
    private int formationOrders = 0;
    private BlockPos formationFirst = null;

    public OnboardingSession(String student) {
        this.student = student;
    }

    /** A matching building is placed but not finished (the panel says "building..."). */
    public boolean isInProgress() {
        return inProgress;
    }

    /** Moves to the next step (DONE stays DONE) and clears the per-step state. Returns the new step. */
    public OnboardingStep advance() {
        setStep(step.next());
        return step;
    }

    public void setStep(OnboardingStep s) {
        step = s;
        baseline = -1;
        stepPrepared = false;
        inProgress = false;
        enemyCommanderSeen = false;
        highlight = null;
        formationTick = Long.MIN_VALUE;
        formationOrders = 0;
        formationFirst = null;
    }

    /**
     * The client says it saw {@code reported} happen. Taken only for the current step, and only when that step is one
     * the client is allowed to finish (camera / selection) or a Continue on an info step. Anything else - a stale
     * report after a skip, or a modified client claiming the extractor step - is ignored.
     */
    public boolean clientReport(OnboardingStep reported) {
        if (reported != step || step == OnboardingStep.DONE)
            return false;
        if (step.kind == OnboardingStep.Kind.SERVER)
            return false;
        advance();
        return true;
    }

    /** Skip the current step (any kind). False once the tutorial is over. */
    public boolean skip() {
        if (step == OnboardingStep.DONE)
            return false;
        advance();
        return true;
    }

    /**
     * An order the student's units were given. Finishes MOVE (a move), ATTACK_MOVE (an attack-move) and FORMATION (a
     * line: two or more single-unit orders to different spots in one burst, which is exactly what a right- or
     * left-drag formation sends, while a plain right click sends one order for the whole selection).
     *
     * @param unitCount units the order carried after the server's ownership filter
     * @return true when this order finished the current step
     */
    public boolean onOrder(UnitAction action, int unitCount, BlockPos target, long gameTick) {
        if (unitCount <= 0 || action == null)
            return false;
        boolean move = action == UnitAction.MOVE;
        boolean attackMove = action == UnitAction.ATTACK_MOVE;
        if (!move && !attackMove)
            return false;
        switch (step) {
            case MOVE -> {
                advance();
                return true;
            }
            case ATTACK_MOVE -> {
                if (attackMove) {
                    advance();
                    return true;
                }
                return false;
            }
            case FORMATION -> {
                if (unitCount != 1 || target == null)
                    return false;
                if (gameTick - formationTick > FORMATION_WINDOW_TICKS) {
                    formationTick = gameTick;
                    formationOrders = 1;
                    formationFirst = target;
                    return false;
                }
                if (!target.equals(formationFirst))
                    formationOrders++;
                if (formationOrders >= 2) {
                    advance();
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** TRAIN: fighters owned now. The first call fixes the baseline; two more than it finishes the step. */
    public boolean onFighterCount(int fighters) {
        if (step != OnboardingStep.TRAIN)
            return false;
        if (baseline < 0) {
            baseline = fighters;
            return false;
        }
        if (fighters >= baseline + 2) {
            advance();
            return true;
        }
        return false;
    }

    /** KILL_COMMANDER: whether the enemy still has a living commander (and whether it is still in the match). */
    public boolean onEnemyCommander(boolean alive, boolean enemyStillInMatch) {
        if (step != OnboardingStep.KILL_COMMANDER)
            return false;
        if (alive && enemyStillInMatch) {
            enemyCommanderSeen = true;
            return false;
        }
        if (enemyCommanderSeen || !enemyStillInMatch) {
            advance();
            return true;
        }
        return false;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("student", student);
        t.putString("enemy", enemy);
        t.putString("step", step.name());
        t.putLong("home", home.asLong());
        t.putLong("enemyHome", enemyHome.asLong());
        return t;
    }

    public static OnboardingSession load(CompoundTag t) {
        OnboardingSession s = new OnboardingSession(t.getString("student"));
        s.enemy = t.getString("enemy");
        s.step = OnboardingStep.byName(t.getString("step"));
        s.home = BlockPos.of(t.getLong("home"));
        s.enemyHome = BlockPos.of(t.getLong("enemyHome"));
        return s;
    }
}
