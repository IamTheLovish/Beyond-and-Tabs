package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Box-selection rules, after Beyond All Reason's Smart Select widget (luaui/Widgets/unit_smart_select.lua by aegis,
 * public domain; NOTICE.md). A drag box over a mixed crowd gives what a BAR player expects, in this order:
 * <ol>
 *   <li>Default: if any mobile unit is in the box, immobile ones (totems; buildings are not entities here) are dropped;
 *       among the mobiles, workers are kept only when no other mobile unit (a combat unit) is in the box.</li>
 *   <li>Alt held: only mobile combat units (no workers, nothing immobile).</li>
 *   <li>Z held: only unit types already in the current selection (a no-op with nothing selected).</li>
 *   <li>Space held: only idle units.</li>
 *   <li>Shift adds the result to the current selection, Ctrl subtracts it, neither replaces it.</li>
 * </ol>
 * A single click never comes through here (UnitClientEvents). {@link #filterBox} is the live preselection while the
 * box is dragged (what the box will give, before Shift / Ctrl combine it with the selection); {@link #filter} is the
 * whole rule on release. Pure: it reads the lists and the {@link Rules} predicates, nothing else, so a GameTest can
 * feed it plain unit lists. The predicates reuse the codebase's own checks (AreaCommandClientEvents' worker / fighter
 * sorting, Unit.isIdle) and are overridable: the client swaps in its synced idle-worker set and the HUD's unit-type
 * names (CursorClientEvents).
 */
public final class SmartSelect {

    private SmartSelect() {}

    /** The modifier keys held when the box is evaluated: Shift add, Ctrl subtract, Alt combat, Z same type, Space idle. */
    public record Mods(boolean shift, boolean ctrl, boolean alt, boolean sameType, boolean idleOnly) {
        public static final Mods NONE = new Mods(false, false, false, false, false);
    }

    /** What the rules ask of a unit. {@link #DEFAULT} is server-safe; the client overrides idle and type. */
    public interface Rules {
        /** Can it move at all? Totems and other zero-speed units count as buildings for rule 1. */
        boolean isMobile(LivingEntity le);
        /** A mobile unit that fights and is not a worker (a villager is both; worker wins, as in AreaCommands). */
        boolean isCombat(LivingEntity le);
        /** A builder / gatherer. */
        boolean isWorker(LivingEntity le);
        /** Nothing to do right now. */
        boolean isIdle(LivingEntity le);
        /** The "same type" key for Z: two units with equal keys are the same kind. */
        Object typeKey(LivingEntity le);
    }

    public static final Rules DEFAULT = new Rules() {
        @Override
        public boolean isMobile(LivingEntity le) {
            return SmartSelect.isMobile(le);
        }

        @Override
        public boolean isCombat(LivingEntity le) {
            return SmartSelect.isMobile(le) && SmartSelect.isFighter(le);
        }

        @Override
        public boolean isWorker(LivingEntity le) {
            return SmartSelect.isWorker(le);
        }

        @Override
        public boolean isIdle(LivingEntity le) {
            return le instanceof Unit u && u.isIdle();
        }

        @Override
        public Object typeKey(LivingEntity le) {
            return le.getType();
        }
    };

    /**
     * The worker / fighter split AreaCommandClientEvents uses for its area commands, kept HERE so the rules stay
     * server-safe: CI run 38478383984 failed all eight SmartSelect GameTests with "Attempted to load class
     * net/minecraft/client/Minecraft for invalid dist DEDICATED_SERVER" because that client class was touched.
     */
    public static boolean isWorker(LivingEntity le) {
        return le instanceof WorkerUnit && le instanceof Unit;
    }

    public static boolean isFighter(LivingEntity le) {
        return !(le instanceof WorkerUnit) && le instanceof AttackerUnit && le instanceof Unit;
    }

    /** A unit whose base movement speed is above zero (AbstractTotem sets it to 0); non-unit mobs count as mobile. */
    static boolean isMobile(LivingEntity le) {
        if (!(le instanceof Unit))
            return true;
        if (le.getAttribute(Attributes.MOVEMENT_SPEED) == null)
            return true;
        return le.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) > 0;
    }

    /** The whole rule: the filtered box combined with the current selection as Shift / Ctrl ask. */
    public static List<LivingEntity> filter(List<LivingEntity> inBox, List<LivingEntity> currentSelection, Mods mods) {
        return filter(inBox, currentSelection, mods, DEFAULT);
    }

    public static List<LivingEntity> filter(List<LivingEntity> inBox, List<LivingEntity> currentSelection, Mods mods,
                                            Rules rules) {
        return combine(filterBox(inBox, currentSelection, mods, rules), currentSelection, mods);
    }

    /**
     * Rules 1-4 on the units under the box: what the box itself yields, in box order. This is what the drag highlights
     * live - with Shift the units about to be added, with Ctrl the units about to be removed.
     */
    public static List<LivingEntity> filterBox(List<LivingEntity> inBox, List<LivingEntity> currentSelection, Mods mods,
                                               Rules rules) {
        List<LivingEntity> out = new ArrayList<>(inBox.size());
        if (mods.alt()) {
            for (LivingEntity le : inBox)
                if (rules.isCombat(le))
                    out.add(le);
        } else {
            boolean anyMobile = false, anyNonWorkerMobile = false;
            for (LivingEntity le : inBox) {
                if (!rules.isMobile(le))
                    continue;
                anyMobile = true;
                if (!rules.isWorker(le))
                    anyNonWorkerMobile = true;
            }
            for (LivingEntity le : inBox) {
                boolean mobile = rules.isMobile(le);
                if (anyMobile && !mobile)
                    continue;   // a mobile unit in the box: the immobile ones are not what was meant
                if (anyNonWorkerMobile && mobile && rules.isWorker(le))
                    continue;   // a combat unit in the box: the workers beside it are not what was meant
                out.add(le);
            }
        }
        if (mods.sameType() && !currentSelection.isEmpty()) {
            Set<Object> keys = new HashSet<>();
            for (LivingEntity le : currentSelection)
                keys.add(rules.typeKey(le));
            out.removeIf(le -> !keys.contains(rules.typeKey(le)));
        }
        if (mods.idleOnly())
            out.removeIf(le -> !rules.isIdle(le));
        return out;
    }

    /** Rule 5: Shift appends the box to the selection (no duplicates), Ctrl removes it, neither replaces it. */
    public static List<LivingEntity> combine(List<LivingEntity> box, List<LivingEntity> currentSelection, Mods mods) {
        if (mods.ctrl()) {
            Set<Integer> drop = new HashSet<>();
            for (LivingEntity le : box)
                drop.add(le.getId());
            List<LivingEntity> out = new ArrayList<>(currentSelection.size());
            for (LivingEntity le : currentSelection)
                if (!drop.contains(le.getId()))
                    out.add(le);
            return out;
        }
        if (mods.shift()) {
            List<LivingEntity> out = new ArrayList<>(currentSelection);
            Set<Integer> have = new HashSet<>();
            for (LivingEntity le : currentSelection)
                have.add(le.getId());
            for (LivingEntity le : box)
                if (have.add(le.getId()))
                    out.add(le);
            return out;
        }
        return new ArrayList<>(box);
    }
}
