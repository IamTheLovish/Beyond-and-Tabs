package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitAnimationAction;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitAnimationClientboundPacket;
import com.solegendary.reignofnether.unit.units.monsters.WardenUnit;
import com.solegendary.reignofnether.unit.units.monsters.ZoglinUnit;
import com.solegendary.reignofnether.unit.units.neutral.PandaUnit;
import com.solegendary.reignofnether.unit.units.neutral.PolarBearUnit;
import com.solegendary.reignofnether.unit.units.piglins.HoglinUnit;
import com.solegendary.reignofnether.unit.units.piglins.MarauderUnit;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;
import com.solegendary.reignofnether.unit.units.villagers.RavagerUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.monster.Slime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

// Move towards a building to attack it
// will continually try to move towards the building if too far away as long as this goal is being enacted

// similar to BuildRepairGoal but to damage instead of repair
// unlike BuildRepairGoal the ticks and destroy logic is on the goal side since units have different damage and
// attack speed amounts stats the building is not damaged in unison

public class MeleeAttackBuildingGoal extends MoveToTargetBlockGoal {

    protected int ticksToNextBlockBreak = (int) ((AttackerUnit) mob).getAttackCooldown();
    protected BuildingPlacement buildingTarget;

    // The repath loop of this goal is tick()'s, on its own faster cooldown. These used to be named RECALC_COOLDOWN_MAX
    // and recalcCooldown, shadowing MoveToTargetBlockGoal's, and the base canContinueToUse ran its own repath loop on
    // the base fields alongside this one - two repath loops per attacker (see canContinueToUse below).
    protected static final int MELEE_RECALC_COOLDOWN_MAX = 10;
    protected static final int MELEE_RECALC_COOLDOWN_CAP = 100; // 5s — attacks should respond faster than generic move
    protected int currentMeleeRecalcCooldown = MELEE_RECALC_COOLDOWN_MAX;
    protected int meleeRecalcCooldown = 0; // limit start() used by tick()
    public boolean forced = false;

    // calcMoveTarget's last answer: getClosestGroundPos reads two block states per perimeter cell, and tick() asked
    // every tick although the answer only changes when the unit changes cell (or the ground round the site changes,
    // hence the refresh every MOVE_TARGET_REFRESH ticks)
    static final int MOVE_TARGET_REFRESH = 20;
    private BuildingPlacement moveTargetFor = null;
    private BlockPos moveTargetFrom = null;
    private BlockPos cachedMoveTarget = null;
    private long moveTargetAt = Long.MIN_VALUE;

    // The ground cells round each building its melee attackers head for (server side). A party sent at a building from
    // one side all took the cell nearest themselves - the same few cells on the near face - and big units queued behind
    // the front ones: six Marauders (iron-golem wide) on a converter came into reach at ticks 50-63 and the back pair at
    // 72-112, and the raid timing test failed in CI (flake-stress 41708076632). Each attacker now takes the nearest cell
    // at least the two units' width from every other attacker's cell, so a party spreads round the building; with no
    // such cell left it takes the nearest, as before. Keyed by entity id, so a dead attacker or a destroyed building is
    // not held here. A unit keeps its cell until it gives it up (below): re-picking it from where the unit stood every
    // move-target refresh swapped cells mid-attack, and a Marauder sent round the corner left reach for 20 ticks (its
    // blow timer stops out of reach) - the converter still read 4.2 s (flake-stress 41738191824; 41739474107). The
    // spacing binds the newcomer only, so one unit's new claim never moves another's (41803713392: it did, in a chain).
    private static final Map<BuildingPlacement, Map<Integer, BlockPos>> ATTACK_CELLS = new WeakHashMap<>();
    // Cells of this target this unit could not reach. A cell is picked by distance only, so one behind bodies (other
    // units round the building, which paths do not see) or a wall was kept while the unit stood: one Marauder of six
    // never came in reach of a converter in 194 ticks (flake-stress 41742015436). A unit out of reach that gets no
    // closer to its cell for NO_PROGRESS_TICKS gives it up, with the ring cells within 2 of it (what blocked it blocks
    // those as well), and claims the next nearest. Its navigation is no sign: a unit pressing on bodies never finishes
    // its path. Cleared with a new order; bounded by the ring round the building.
    private final Set<BlockPos> unreachableCells = new HashSet<>();
    private int cellsGivenUp = 0;
    private static final int NO_PROGRESS_TICKS = 60;
    private double closestToCell = Double.MAX_VALUE;
    private int ticksWithoutProgress = 0;

    public MeleeAttackBuildingGoal(Mob mob) {
        super(mob, true, 0);
    }

    // The base repath loop (nav done and more than 1.5 blocks off the move target) is tick()'s job here, which skips
    // it while the unit is already attacking: isAttacking accepts 2 blocks off the footprint, so a unit hitting the
    // building from 1.5-2 blocks off its ground cell used to repath on the spot over and over (the BuildRepairGoal
    // band, fixed there the same way). Otherwise as the base: kept on while a path is pending or one is walked, and
    // on (for tick() to repath) while it is not yet there. Also kept on while attacking: once the walk was done the
    // base answered false, and the goal selector stopped it and (canUse: it still has a move target) started it again
    // in the same pass - a fresh createPath every other tick for every unit hitting a building, the swing itself
    // unchanged (the restarted goal ticked straight on).
    @Override
    public boolean canContinueToUse() {
        if (pathPending)
            return moveTarget != null;
        if (moveTarget == null)
            return false;
        if (this.mob.getNavigation().isDone())
            return isAttacking() || this.mob.getOnPos().distSqr(moveTarget) > getMinDistToRecalculateSqr();
        return true;
    }

    public void tick() {
        if (buildingTarget != null) {

            // for some reason, isDone() can sometimes be true even when moveTarget is nonnull and
            // we haven't reached the target, esp. for Brutes.
            // A1+A5: if the repathed final node matches the previous one, the target is unreachable —
            // stop instead of looping. Otherwise back off exponentially.
            // (not while an async path is in flight: a start() then dropped that request and asked again)
            if (this.mob.getNavigation().isDone() && moveTarget != null && !pathPending &&
                    this.mob.getOnPos().distSqr(moveTarget) > 1 && !isAttacking()) {
                if (meleeRecalcCooldown > 0) {
                    meleeRecalcCooldown -= 1;
                } else {
                    BlockPos oldFinalNode = getFinalNodePos();
                    this.start();
                    BlockPos newFinalNode = getFinalNodePos();
                    if (oldFinalNode != null && oldFinalNode.equals(newFinalNode)) {
                        currentMeleeRecalcCooldown = Math.min(currentMeleeRecalcCooldown * 2, MELEE_RECALC_COOLDOWN_CAP);
                    } else {
                        currentMeleeRecalcCooldown = MELEE_RECALC_COOLDOWN_MAX;
                    }
                    meleeRecalcCooldown = currentMeleeRecalcCooldown;
                }
            }
            if (buildingTarget == null) {
                return;
            }
            calcMoveTarget();
            watchProgress();
            if (buildingTarget.getBlocksPlaced() <= 0) {
                stopAttacking();
            }
            if (buildingTarget != null && isAttacking()) {
                BlockPos bp = buildingTarget.centrePos;
                this.mob.getLookControl().setLookAt(bp.getX(), bp.getY(), bp.getZ());
                mob.getLookControl().lookAtCooldown = 20;

                // a unit with 1 attack damage @ 20 attack cd and building damage multiplier 1.0 will destroy a block once per second
                // if the damage multiplier leaves a fraction remainder, treat that as a chance to destroy an additional block
                // eg. if a unit with 3 damage attacks a building with 0.5 multiplier, always destroy 1 block + 50% chance to destroy 2 blocks
                ticksToNextBlockBreak -= 1;
                if (ticksToNextBlockBreak <= 0)
                    doBuildingAttack();
            }
        } else {
            this.moveTarget = null;
        }
    }

    protected void doBuildingAttack() {
        if (mob instanceof IronGolemUnit ||
                mob instanceof HoglinUnit ||
                mob instanceof ZoglinUnit ||
                mob instanceof RavagerUnit ||
                mob instanceof WardenUnit ||
                mob instanceof PolarBearUnit
        ) {
            mob.handleEntityEvent((byte) 4);
            UnitAnimationClientboundPacket.sendBasicPacket(UnitAnimationAction.NON_KEYFRAME_ATTACK, mob);
        }
        else
            this.mob.swing(InteractionHand.MAIN_HAND);

        if (mob instanceof PandaUnit pandaUnit)
            pandaUnit.roll(true);

        AttackerUnit unit = (AttackerUnit) mob;
        ticksToNextBlockBreak = (int) unit.getAttackCooldown();
        double dmg = unit.getUnitAttackDamage() * buildingTarget.getMeleeDamageMult() * unit.getBuildingDamageMultiplier();
        try {
            net.minecraft.world.phys.Vec3 from = this.mob.getBoundingBox().getCenter();
            net.minecraft.world.phys.Vec3 toCentre = net.minecraft.world.phys.Vec3.atCenterOf(buildingTarget.centrePos).subtract(from);
            net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(toCentre.x, 0, toCentre.z);
            if (flat.lengthSqr() > 1e-4) {
                net.minecraft.world.phys.Vec3 hitPos = from.add(flat.normalize().scale(this.mob.getBbWidth() * .5 + .7));
                boolean heavy = this.mob.getBbWidth() * this.mob.getBbHeight() > 2.5f;
                com.solegendary.reignofnether.barfx.BarFx.impact(this.mob.level(), hitPos, flat,
                        heavy ? com.solegendary.reignofnether.barfx.BarFx.K_HEAVY_MELEE : com.solegendary.reignofnether.barfx.BarFx.K_MELEE,
                        com.solegendary.reignofnether.barfx.BarFx.AT_BUILDING, 1, this.mob);
            }
        } catch (Exception ignored) { }
        buildingTarget.destroyRandomBlocks(dmg, this.mob);

        if (unit instanceof MarauderUnit marauderUnit) {
            marauderUnit.decrementAttacks();
        }

        if (mob instanceof Slime slime && slime.onGround())
            slime.jumpFromGround();
    }

    private void calcMoveTarget() {
        if (this.buildingTarget == null)
            return;
        BlockPos from = mob.getOnPos();
        long now = mob.level().getGameTime();
        if (cachedMoveTarget == null || moveTargetFor != buildingTarget || !from.equals(moveTargetFrom)
                || now - moveTargetAt >= MOVE_TARGET_REFRESH || now < moveTargetAt) {
            cachedMoveTarget = mob.level().isClientSide() ? this.buildingTarget.getClosestGroundPos(from, 1) : claimCell(from);
            moveTargetFor = buildingTarget;
            moveTargetFrom = from;
            moveTargetAt = now;
        }
        this.moveTarget = cachedMoveTarget;
    }

    private BlockPos claimCell(BlockPos from) {
        BuildingPlacement target = buildingTarget;
        Map<Integer, BlockPos> cells = ATTACK_CELLS.computeIfAbsent(target, b -> new HashMap<>());
        BlockPos mine = cells.remove(mob.getId());
        Map<Mob, BlockPos> others = new HashMap<>();
        cells.entrySet().removeIf(e -> {
            if (mob.level().getEntity(e.getKey()) instanceof Mob m && m.isAlive() && m instanceof AttackerUnit au
                    && au.getAttackBuildingGoal() instanceof MeleeAttackBuildingGoal g && g.buildingTarget == target) {
                others.put(m, e.getValue());
                return false;
            }
            return true;
        });
        Predicate<BlockPos> clear = p -> {
            for (Map.Entry<Mob, BlockPos> e : others.entrySet()) {
                int need = Math.max(1, (int) Math.ceil((mob.getBbWidth() + e.getKey().getBbWidth()) / 2));
                if (Math.max(Math.abs(e.getValue().getX() - p.getX()), Math.abs(e.getValue().getZ() - p.getZ())) < need)
                    return false;
            }
            return !unreachableCells.contains(p);
        };
        BlockPos cell = mine != null && !unreachableCells.contains(mine) ? mine
            : others.isEmpty() && unreachableCells.isEmpty() ? null : target.getClosestGroundPos(from, 1, false, clear);
        if (cell == null && !unreachableCells.isEmpty())
            cell = target.getClosestGroundPos(from, 1, false, p -> !unreachableCells.contains(p));
        if (cell == null)
            cell = target.getClosestGroundPos(from, 1);
        cells.put(mob.getId(), cell);
        return cell;
    }

    /** Server side, each tick: a unit out of reach that gets no closer to its cell for NO_PROGRESS_TICKS gives it up. */
    private void watchProgress() {
        if (mob.level().isClientSide() || buildingTarget == null || cachedMoveTarget == null || pathPending || isAttacking()) {
            ticksWithoutProgress = 0;
            closestToCell = Double.MAX_VALUE;
            return;
        }
        double d = Math.sqrt(mob.position().distanceToSqr(cachedMoveTarget.getX() + .5, mob.getY(), cachedMoveTarget.getZ() + .5));
        if (d < closestToCell - .5) {
            closestToCell = d;
            ticksWithoutProgress = 0;
        } else if (++ticksWithoutProgress >= NO_PROGRESS_TICKS) {
            giveUpCell();
        }
    }

    /** Gives up the cell this unit could not reach, claims the next and heads for it. */
    private void giveUpCell() {
        ticksWithoutProgress = 0;
        closestToCell = Double.MAX_VALUE;
        if (mob.level().isClientSide() || buildingTarget == null || cachedMoveTarget == null)
            return;
        BlockPos lost = cachedMoveTarget;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                unreachableCells.add(lost.offset(dx, 0, dz));
        releaseCell();
        cachedMoveTarget = null;
        calcMoveTarget();
        if (moveTarget == null)
            return;
        cellsGivenUp++;
        this.start();
    }

    /** How many cells this unit has given up as unreachable since its last order (for the tests). */
    public int getCellsGivenUp() {
        return cellsGivenUp;
    }

    private void releaseCell() {
        if (buildingTarget != null && !mob.level().isClientSide()) {
            Map<Integer, BlockPos> cells = ATTACK_CELLS.get(buildingTarget);
            if (cells != null)
                cells.remove(mob.getId());
        }
    }

    // only count as building if in range of the target - building is actioned in Building.tick()
    public boolean isAttacking() {
        if (buildingTarget != null)
            return buildingTarget.isPosInsideBuilding(mob.getOnPos(), 2);
        return false;
    }

    public void setBuildingTarget(BlockPos blockPos) {
        if (blockPos != null) {
            currentMeleeRecalcCooldown = MELEE_RECALC_COOLDOWN_MAX;
            cachedMoveTarget = null;   // a new order always takes a fresh ground cell
            releaseCell();
            unreachableCells.clear();
            cellsGivenUp = 0;
            if (this.mob.level().isClientSide()) {
                BuildingPlacement b = BuildingUtils.findBuilding(this.mob.level().isClientSide(), blockPos);
                if (b != null && b.isAttackable()) {
                    this.buildingTarget = b;
                    MiscUtil.addUnitCheckpoint(((Unit) mob), new BlockPos(
                                    buildingTarget.centrePos.getX(),
                                    buildingTarget.originPos.getY() + 1,
                                    buildingTarget.centrePos.getZ()),
                            false
                    );
                }
            }
            else {
                BuildingPlacement b = BuildingUtils.findBuilding(this.mob.level().isClientSide(), blockPos);
                if (b != null && b.isAttackable()) {
                    this.buildingTarget = b;
                    if (this.mob.isVehicle() && this.mob.getFirstPassenger() instanceof AttackerUnit aUnit &&
                            aUnit.getAttackBuildingGoal() instanceof RangedAttackBuildingGoal<?> rabg)
                        rabg.setBuildingTarget(this.buildingTarget);
                }
            }
            calcMoveTarget();
            this.start();
        }
    }

    public BuildingPlacement getBuildingTarget() { return buildingTarget; }

    // if we override stop() it for some reason is called after start() and we can never begin this goal...
    public void stopAttacking() {
        releaseCell();
        buildingTarget = null;
        super.stopMoving();
        forced = false;
    }
}
