package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * MiscUtil.findClosestAttackableEntity as it was before the Oct 21 perf plan, PR 5 - an entity-section walk of the
 * cube round the unit, a sort of everything in it (friends included), then the checks in order - kept so the [Stress]
 * benchmark can time the old way against unit/TargetScan in the same run (TargetScan.legacyScans) and the equivalence
 * GameTest can hold the two to the same answers. Only the priority list and the deprioritised kinds now come from the
 * MiscUtil helpers both scans share, so neither can drift from the other. Not used in play.
 */
public final class TargetScansLegacy {
    private TargetScansLegacy() { }

    public static LivingEntity findClosestAttackableEntity(Mob unitMob, float range, ServerLevel level) {
        Vec3 pos = new Vec3(unitMob.position().x, unitMob.position().y, unitMob.position().z);
        boolean neutralAggro = unitMob.level().getGameRules().getRule(GameRuleRegistrar.NEUTRAL_AGGRO).get();
        AABB aabb = new AABB(pos.x - range, pos.y - range, pos.z - range, pos.x + range, pos.y + range, pos.z + range);
        List<LivingEntity> rawEntities = level.getEntitiesOfClass(LivingEntity.class, aabb);
        boolean isMelee = MiscUtil.isMeleeAttacker(unitMob);
        // entities outside the sphere (the cube's corners) are dropped up front; each sort key is computed once.
        // List.sort is stable, so ties keep the entity-section order
        record Candidate(LivingEntity entity, double key) {}
        ArrayList<Candidate> candidates = new ArrayList<>(rawEntities.size());
        for (LivingEntity e : rawEntities) {
            double dist = e.position().distanceTo(pos);
            if (dist > range)
                continue;
            if (MiscUtil.isDeprioritisedTarget(e, isMelee))
                dist += 100;
            candidates.add(new Candidate(e, dist));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::key));
        ArrayList<LivingEntity> entities = new ArrayList<>(candidates.size());
        for (Candidate c : candidates)
            entities.add(c.entity());

        Predicate<LivingEntity> priorityFilter = MiscUtil.targetPriority(unitMob);
        Predicate<LivingEntity> baseFilter = e ->
                e.position().distanceTo(pos) <= range &&
                e.level().getWorldBorder().isWithinBounds(e.blockPosition());

        // Try priority pass first, then fall back to base filter
        List<Predicate<LivingEntity>> passes = priorityFilter != null
                ? List.of(baseFilter.and(priorityFilter), baseFilter)
                : List.of(baseFilter);

        for (Predicate<LivingEntity> filter : passes) {
            for (LivingEntity entity : entities) {
                if (filter.test(entity) &&
                        MiscUtil.isIdleOrMoveAttackable(unitMob, entity, neutralAggro) &&
                        MiscUtil.hasLineOfSightForAttacks(unitMob, entity) &&
                        !(entity instanceof Unit unit && unit.isGarrisoned()) &&
                        !Concealment.isConcealed(entity)) {
                    return entity;
                }
            }
        }
        return null;
    }
}
