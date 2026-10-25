package com.solegendary.reignofnether.unit;

import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.PhantomSummon;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public class NonUnitServerEvents {

    // list of units to cancel specific goals for so they don't interfere with player commands
    public static final List<PathfinderMob> attackSuppressedNonUnits = Collections.synchronizedList(new ArrayList<>());
    public static final List<PathfinderMob> moveSuppressedNonUnits = Collections.synchronizedList(new ArrayList<>());

    public static final List<Pair<PathfinderMob, BlockPos>> nonUnitMoveTargets = Collections.synchronizedList(new ArrayList<>());

    private static final TargetingConditions WILD_MOBS = TargetingConditions.forCombat().selector(e -> !(e instanceof Unit));

    public static boolean canControlAllMobs(Level level, String playerName) {
        return ResearchServerEvents.playerHasCheat(playerName, "wouldyoukindly");
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.level.isClientSide() || evt.level.dimension() != Level.OVERWORLD) {
            return;
        }

        synchronized (nonUnitMoveTargets) {
            nonUnitMoveTargets.removeIf(pair -> {
                PathfinderMob mob = pair.getFirst();
                BlockPos finalTargetBp = pair.getSecond();
                Path navPath = mob.getNavigation().getPath();
                if (mob.distanceToSqr(finalTargetBp.getCenter()) < 4) {
                    return true;
                } else if (mob.tickCount % 20 == 0 && navPath == null ||
                    (navPath != null && navPath.getEndNode() != null && mob.distanceToSqr(navPath.getEndNode().asBlockPos().getCenter()) < 4)) {
                    Path path = mob.getNavigation().createPath(finalTargetBp.getX(), finalTargetBp.getY(), finalTargetBp.getZ(), 0);
                    mob.getNavigation().moveTo(path, 1);
                }
                return false;
            });
        }
        synchronized (attackSuppressedNonUnits) {
            attackSuppressedNonUnits.removeIf(mob -> (mob.isDeadOrDying() || mob.isRemoved() || (mob.getNavigation().isDone() && mob.getTarget() == null)));
        }
        synchronized (moveSuppressedNonUnits) {
            moveSuppressedNonUnits.removeIf(mob -> (mob.isDeadOrDying() || mob.isRemoved() || (mob.getNavigation().isDone() && mob.getTarget() == null)));
        }

        if (evt.level.getServer() != null && evt.level.getServer().getGameRules().getRule(GameRuleRegistrar.NEUTRAL_AGGRO).get()) {
            int sysAggro = Sys.begin(Sys.NEUTRAL_AGGRO);
            Set<PathfinderMob> pfMobs = new HashSet<>();
            try {
                for (LivingEntity unit : UnitServerEvents.getAllUnits()) {
                    if (unit.tickCount % 20 != 0)
                        continue;
                    AABB aabb = new AABB(unit.blockPosition().offset(-10, -10, -10), unit.blockPosition().offset(10, 10, 10));
                    // units are not wild mobs: the selector drops them before the combat rules and the sight check
                    pfMobs.addAll(evt.level.getNearbyEntities(PathfinderMob.class, WILD_MOBS, unit, aabb));
                }
            } finally {
                Sys.end(sysAggro);
            }
            for (PathfinderMob pfMob : pfMobs) {
                if (!(shouldMobBeAggressive(pfMob)))
                    continue;

                boolean hasAttackGoal = false;
                for (WrappedGoal wrappedGoal : pfMob.targetSelector.getAvailableGoals())
                    if (wrappedGoal.getGoal() instanceof NearestAttackableTargetGoal)
                        hasAttackGoal = true;

                if (pfMob.getTarget() == null && hasAttackGoal && !attackSuppressedNonUnits.contains(pfMob)) {
                    LivingEntity target = MiscUtil.findClosestAttackableEntity(pfMob, 10, (ServerLevel) evt.level);
                    if (target != null)
                        pfMob.setTarget(target);
                }
            }
        }
    }

    public static Faction getNonUnitFaction(LivingEntity le) {
        if (le instanceof IronGolem || le instanceof AbstractIllager || le instanceof AbstractVillager)
            return Factions.VILLAGERS;
        else if (le instanceof AbstractPiglin || le instanceof Hoglin || le instanceof Ghast || le instanceof Blaze || le instanceof WitherSkeleton)
            return Factions.PIGLINS;
        else if (le instanceof AbstractSkeleton || le instanceof Zombie || le instanceof Creeper || le instanceof Spider || le instanceof Slime || le instanceof Warden)
            return Factions.MONSTERS;
        return Factions.NONE;
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent evt) {
        LivingEntity le = evt.getEntity();

        // prevent vanilla mobs attacking their RoN faction equivalents
        if (!(le instanceof Unit) && evt.getNewTarget() instanceof Unit unit)
            if (Factions.getFaction(unit).equals(getNonUnitFaction(le)))
                evt.setCanceled(true);

        // wildlife and stray mobs never go for a commander: with commanderDefeat on, losing it loses the match, and
        // neutralAggro turns every wild hunter near a unit (Alex's Mobs roadrunners, rattlesnakes, emus, bears...)
        // onto the closest unit - a roadrunner wandering into a base must not decide a 1v1. Only player-owned units
        // (and an Evoker's vexes) may hunt one; the commander can still fight wildlife, it just won't be chased.
        // ...and since the Oct 11 soak (a hard bot's workers killed by a gelada monkey and a fox while gathering,
        // leaving it unable to build), wildlife never hunts ANY unit: BAR has no fauna in the fight, and a worker that
        // pokes an animal for food must not lose the trade. Units may still hunt animals; animals just don't fight back.
        if (!evt.getEntity().level().isClientSide() && evt.getNewTarget() instanceof Unit
                && !(le instanceof Unit)
                && !(le instanceof Vex vex && vex.getOwner() instanceof Unit))
            evt.setCanceled(true);
    }

    private static boolean shouldMobBeAggressive(Mob mob) {
        return !(mob instanceof Vex) &&
                !(mob instanceof PhantomSummon);
    }
}
