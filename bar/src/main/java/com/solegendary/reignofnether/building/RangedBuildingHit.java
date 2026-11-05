package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import javax.annotation.Nullable;

/**
 * The one way a ranged unit's shot damages a building (lovish, Nov 5: "I want archers to be able to damage defenses").
 * Every arrow or trident a unit fires ({@code AbstractArrowMixin}, whoever the shooter is) and every hit-scan shot at a
 * building's block (the Rifle Squad's musket, the Harpooner's harpoon, the Seraph Archer's bolt, through
 * {@code RangedAttackBuildingGoal}) lands here, so a new archer chips buildings without a list to join.
 * <p>
 * Damage: the shooter's damage x the building's melee multiplier ({@link Building#getMeleeDamageMult}) x the shooter's
 * building-damage multiplier x {@link BuildingDamage#RANGED_MULT}. An own or allied building is not hit (the shot
 * behaves as a plain block hit), and {@code destroyRandomBlocks} drops friendly fire again on its own.
 * <p>
 * Hits are counted by unit type and logged every {@link #LOG_EVERY} hits, so a log names who is chipping buildings.
 */
public final class RangedBuildingHit {
    private RangedBuildingHit() { }

    /** A "[BuildingDamage] ranged hits" line every this many hits (all types together). */
    static final int LOG_EVERY = 200;

    private static final Object2IntOpenHashMap<String> HITS = new Object2IntOpenHashMap<>();
    private static int sinceLog = 0;

    /**
     * A ranged shot from {@code shooter} lands on {@code pos}: if that is an attackable enemy building, it takes the
     * ranged share of the shooter's damage. Server only. Returns whether a building was hit (the caller spends the
     * projectile); false for a shooter that is not a unit, no building, an own or allied one, or the client.
     */
    public static boolean hit(@Nullable Entity shooter, BlockPos pos) {
        if (!(shooter instanceof AttackerUnit au) || !(shooter instanceof Unit unit) || shooter.level().isClientSide())
            return false;
        BuildingPlacement building = BuildingUtils.findBuilding(false, pos);
        if (building == null || !building.isAttackable() || building.ownerName == null)
            return false;
        if (building.isFriendlyTo(unit.getOwnerName()) || building.ownerName.equals(unit.getOwnerName()))
            return false;
        building.destroyRandomBlocks(amount(au, building), shooter);
        count(shooter);
        return true;
    }

    /** The building damage one ranged shot of {@code shooter} does to {@code building} (2 health a point). */
    public static float amount(AttackerUnit shooter, BuildingPlacement building) {
        return shooter.getUnitAttackDamage() * building.getMeleeDamageMult() * shooter.getBuildingDamageMultiplier()
            * BuildingDamage.RANGED_MULT;
    }

    private static synchronized void count(Entity shooter) {
        HITS.addTo(EntityType.getKey(shooter.getType()).getPath(), 1);
        if (++sinceLog >= LOG_EVERY) {
            sinceLog = 0;
            ReignOfNether.LOGGER.info("[BuildingDamage] ranged hits on buildings by unit type: {}", HITS);
        }
    }
}
