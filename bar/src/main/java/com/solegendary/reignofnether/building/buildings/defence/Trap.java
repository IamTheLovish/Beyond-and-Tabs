package com.solegendary.reignofnether.building.buildings.defence;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A trap (design/defences.md): a 3x3 patch of faction ground cover (carpet, moss, petals, ...) with a trigger plate in the
 * middle, laid flat on the ground so units walk over it. Three shared mechanics, two per faction:
 * <ul>
 *   <li>{@link Kind#SPIKES} - spikes / caltrops: hurts and slows every enemy on it; <b>rearmable</b>;</li>
 *   <li>{@link Kind#SNARE} - a root snare / net / bear trap: roots every enemy on it for a few seconds; <b>rearmable</b>;</li>
 *   <li>{@link Kind#MINE} - a mine / powder keg / glyph: a blast over a wider radius; <b>one shot</b>, gone after.</li>
 * </ul>
 * <b>Hidden until triggered</b> (Concealment.isBuildingHiddenFrom): the enemy's clients are never sent the placement
 * (FogOfWarServerEvents.canPlayerSeeBuilding refuses it), so no outline, health bar, minimap mark or hover card, and
 * enemy units can neither auto-target nor be ordered at it (TrapPlacement.isAttackable). When it fires it is revealed
 * for good. A rearmable trap fires by knocking out its trigger plate: the trap shows as damaged, and a worker repairing
 * it puts the plate back - repairing <i>is</i> rearming. Ground units only: flyers never spring a trap.
 * <p>
 * <b>Enhanced traps</b> (lovish Oct 21 02:51: "tier 1 constructors can make enhanced version of tier 1 defenses - kinda
 * like in bar"): the same kind, once a T1 lab stands, at about twice the price - {@link #ENHANCED_RADIUS} the radius,
 * {@link #ENHANCED_POWER} the damage and hold, and one <b>spare charge</b>: the first spring keeps the plate (and a mine
 * keeps its blocks) and the trap rearms itself after {@link #SPARE_REARM_TICKS}; only the second spring spends it.
 */
public class Trap extends DefenceBuilding {

    public enum Kind {
        //      radius dmg  rootTicks slowTicks rearm
        SPIKES(1.8, 30f, 0, 60, true),
        SNARE(2.2, 4f, 80, 0, true),
        MINE(3.0, 70f, 0, 0, false);

        public final double radius;
        public final float damage;
        public final int rootTicks, slowTicks;
        public final boolean rearmable;

        Kind(double radius, float damage, int rootTicks, int slowTicks, boolean rearmable) {
            this.radius = radius;
            this.damage = damage;
            this.rootTicks = rootTicks;
            this.slowTicks = slowTicks;
            this.rearmable = rearmable;
        }
    }

    /** An enhanced trap's radius, and its damage / root / slow, against the base kind's. */
    public static final double ENHANCED_RADIUS = 1.4;
    public static final float ENHANCED_POWER = 1.5f;
    /** How long an enhanced trap's spare charge takes to rearm after its first spring. */
    public static final int SPARE_REARM_TICKS = 100;

    public final Kind kind;
    /** The enhanced twin (a T1 lab's): wider, harder, one spare charge. */
    public final boolean enhanced;
    /** The trigger plate in the middle of the patch: the trap's starting (instant) block, and what firing knocks out. */
    public final Block trigger;

    public Trap(String id, String displayName, DefenceStyle style, Kind kind, Block trigger) {
        this(id, displayName, style, kind, trigger, null);
    }

    /** @param t1Labs non-null: the enhanced twin, waiting for any of these T1 labs */
    public Trap(String id, String displayName, DefenceStyle style, Kind kind, Block trigger,
                @Nullable java.util.function.Supplier<List<com.solegendary.reignofnether.building.Building>> t1Labs) {
        super(id, displayName, style, DefenceCosts.of(kind, t1Labs != null));
        this.kind = kind;
        this.enhanced = t1Labs != null;
        this.anyOfLabs = t1Labs;
        this.trigger = trigger;
        this.portraitBlock = style.trapPortrait();
        this.maxHealth = enhanced ? 120d : 60d;
        this.buildTimeModifier = 1.5f;   // nine flat blocks would otherwise go down in a couple of seconds
        this.explodeChance = 0f;
        this.drawAggro = false;
        // only the plate is laid at once: with no starting type the whole (single) layer would be the foundation and
        // the trap would stand finished, unpaid, the moment it was placed
        this.startingBlockTypes.add(trigger);
    }

    public double radius() {
        return enhanced ? kind.radius * ENHANCED_RADIUS : kind.radius;
    }

    public float damage() {
        return enhanced ? kind.damage * ENHANCED_POWER : kind.damage;
    }

    public int rootTicks() {
        return enhanced ? Math.round(kind.rootTicks * ENHANCED_POWER) : kind.rootTicks;
    }

    public int slowTicks() {
        return enhanced ? Math.round(kind.slowTicks * ENHANCED_POWER) : kind.slowTicks;
    }

    @Override
    protected Object[] tooltipArgs() {
        return new Object[] { (int) damage(), rootTicks() / 20 };
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new TrapPlacement(this, level, pos, rotation, ownerName,
            getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), false);
    }

    public static class TrapPlacement extends BuildingPlacement {
        public static final int SCAN_TICKS = 4;
        /** How close to the centre a unit must stand to spring the trap: someone has to step onto the patch. */
        public static final double PLATE_RADIUS = 1.5;

        public final Trap trap;
        /** Fired at least once: the enemy has seen it. Not saved (a reloaded trap hides again, like a fresh one). */
        public boolean revealed = false;
        /** The enemy owner a game test limits triggers to (null in play: game tests share one world). */
        @Nullable public String onlyFoe = null;
        /**
         * Springs left before the trap is spent (enhanced: a spare one). Not saved, like {@link #revealed}: a reloaded
         * enhanced trap has its spare again - a rare reload is not worth a save field.
         */
        public int spareCharges;
        /** Ticks until a spent spare charge has rearmed (0: armed, if built and whole). */
        public int rearmTicks = 0;

        private final List<LivingEntity> scratch = new ArrayList<>();
        private final List<LivingEntity> victims = new ArrayList<>();

        public TrapPlacement(Trap building, Level level, BlockPos originPos, Rotation rotation, String ownerName,
                             ArrayList<BuildingBlock> blocks, boolean isCapitol) {
            super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
            this.trap = building;
            this.spareCharges = building.enhanced ? 1 : 0;
        }

        /** A hidden trap cannot be hit at all (nobody on the other side knows it is there). */
        @Override
        public boolean isAttackable() {
            return revealed && super.isAttackable();
        }

        /**
         * Hidden from {@code viewer}'s side: not yet revealed and the viewer is neither the owner nor an ally (an
         * ownerless viewer - a neutral - doesn't see it either). Spectators are let through by the caller
         * (FogOfWarServerEvents.canPlayerSeeBuilding only hides it from RTS players).
         */
        public boolean isHiddenFrom(@Nullable String viewer) {
            if (revealed || ownerName == null || ownerName.isBlank())
                return false;
            if (viewer == null || viewer.isBlank())
                return true;
            return !(viewer.equals(ownerName) || AlliancesServerEvents.isAllied(ownerName, viewer));
        }

        /** Built, the plate in place (a rearmable trap that fired waits for a repair) and no spare charge rearming. */
        public boolean isArmed() {
            return isBuilt && !isDestroyedServerside && rearmTicks <= 0 && getBlocksPlaced() >= getBlocksTotal();
        }

        @Override
        public void tick(Level tickLevel) {
            super.tick(tickLevel);
            if (rearmTicks > 0)
                rearmTicks--;
            if (tickLevel instanceof ServerLevel sl && (tickAge + originPos.hashCode()) % SCAN_TICKS == 0)
                checkTrigger(sl);
        }

        /** Springs the trap if armed and an enemy ground unit is on it. Returns how many were caught. Public for the test. */
        public int checkTrigger(ServerLevel sl) {
            if (!isArmed() || ownerName == null || ownerName.isBlank())
                return 0;
            if (!foeOnPlate(sl)) {
                scratch.clear();
                return 0;
            }
            double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5;
            double radius = trap.radius(), r2 = radius * radius;
            victims.clear();
            for (LivingEntity le : UnitGrid.near(sl, cx, cz, radius, scratch)) {
                if (!isFoe(le))
                    continue;
                double dx = le.getX() - cx, dz = le.getZ() - cz;
                if (dx * dx + dz * dz <= r2 && Math.abs(le.getY() - centrePos.getY()) <= 3)
                    victims.add(le);
            }
            scratch.clear();
            for (LivingEntity le : victims) {
                le.hurt(sl.damageSources().generic(), trap.damage());
                if (trap.rootTicks() > 0)   // rooted: slowness this strong stops a unit where it stands
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, trap.rootTicks(), 9, false, true));
                if (trap.slowTicks() > 0)
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, trap.slowTicks(), 1, false, true));
            }
            int caught = victims.size();
            victims.clear();
            spring(sl);
            return caught;
        }

        /**
         * The armed trap's every-{@link #SCAN_TICKS} check, the part every trap on the map pays: is an enemy ground unit
         * on the plate (within {@link #PLATE_RADIUS} of the centre)? Asks the grid for the plate only, not the blast
         * radius - {@link #checkTrigger} asks again for the victims on the rare tick it fires. Public for the 8v8
         * benchmark.
         */
        public boolean foeOnPlate(ServerLevel sl) {
            double cx = centrePos.getX() + 0.5, cz = centrePos.getZ() + 0.5;
            boolean stepped = false;
            for (LivingEntity le : UnitGrid.near(sl, cx, cz, PLATE_RADIUS, scratch)) {
                double dx = le.getX() - cx, dz = le.getZ() - cz;
                // the cheap distance test before isFoe: most units in the radius are not on the plate
                if (dx * dx + dz * dz <= PLATE_RADIUS * PLATE_RADIUS && Math.abs(le.getY() - centrePos.getY()) <= 2 && isFoe(le)) {
                    stepped = true;
                    break;
                }
            }
            return stepped;
        }

        private boolean isFoe(LivingEntity le) {
            if (!le.isAlive() || le.level() != level || !(le instanceof Unit u) || u.isFlyingUnit())
                return false;
            String o = u.getOwnerName();
            return o != null && !o.isBlank() && !ownerName.equals(o) && !AlliancesServerEvents.isAllied(ownerName, o)
                && (onlyFoe == null || onlyFoe.equals(o));
        }

        /** The trap goes off: effects, then reveal, then the plate goes (rearmable) or the whole trap does (a mine). */
        void spring(ServerLevel sl) {
            double x = centrePos.getX() + 0.5, y = centrePos.getY() + 0.3, z = centrePos.getZ() + 0.5;
            switch (trap.kind) {
                case SPIKES -> {
                    sl.sendParticles(ParticleTypes.CRIT, x, y, z, 20, 0.8, 0.2, 0.8, 0.2);
                    com.solegendary.reignofnether.sounds.Sfx.TRAP_SPIKES.play(sl, centrePos);
                }
                case SNARE -> {
                    sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 30, 1.0, 0.3, 1.0, 0.0);
                    com.solegendary.reignofnether.sounds.Sfx.TRAP_SNARE.play(sl, centrePos);
                }
                case MINE -> {
                    sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
                    sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 20, 1.2, 0.4, 1.2, 0.02);
                    com.solegendary.reignofnether.sounds.Sfx.TRAP_MINE.play(sl, centrePos);
                }
            }
            reveal();
            if (spareCharges > 0) {
                // an enhanced trap's spare charge: nothing breaks, it rearms itself after SPARE_REARM_TICKS
                spareCharges--;
                rearmTicks = SPARE_REARM_TICKS;
                return;
            }
            // rearmable: only the plate goes (the trap reads as damaged until a worker repairs it back). A mine is
            // spent: every block goes, and the building's own death check removes it on the next world tick.
            // onBlockBreak keeps the placed-block count honest (explodeChance is 0: no blast from it)
            for (BuildingBlock b : blocks) {
                if (b.getBlockState().isAir() || (trap.kind.rearmable && b.getBlockState().getBlock() != trap.trigger))
                    continue;
                sl.setBlock(b.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
                onBlockBreak(sl, b.getBlockPos(), false);
            }
        }

        /** Shows the trap to everyone from now on: the placement goes to the clients that were refused it. */
        public void reveal() {
            if (revealed)
                return;
            revealed = true;
            if (!level.isClientSide())
                BuildingServerEvents.replaceClientBuilding(originPos);
        }
    }
}
