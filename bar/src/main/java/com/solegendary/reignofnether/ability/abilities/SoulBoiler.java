package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Brassworks essence caster (the Soul Engineer; design/essence_casters.md, lovish approved draft 2 on Oct 29, Revision
 * 1): <b>Soul Boiler</b>, the big spell. Aim at a friendly (own or allied) building whose footprint comes within
 * {@link #RANGE} blocks of the caster (Revision 1: 24): for {@link #BOIL_TICKS} it builds and produces
 * {@link #SPEED_MULT} times as fast ({@link #speedMult}, read by BuildingPlacement's construction and
 * ProductionPlacement's production build power). Faster production draws the owner's metal and energy faster too: the
 * flow economy pays for the speed, the essence buys it. {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s cooldown.
 * <p>
 * Refused - nothing spent, no cooldown - with no friendly building there, one out of reach, one already boiling, or one
 * with nothing to speed up (built, and no production queued), or short of essence. Autocast: the Soul Engineer's own
 * tick ({@link #autocastByUnit}; SoulEngineerUnit.autoBoil), once a second, at the nearest friendly building in reach
 * that is going up or has a queue - no AutoRule aims at a busy building.
 */
public class SoulBoiler extends Ability {

    public static final int ESSENCE_COST = 20;
    public static final int CD_SECONDS = 60;
    public static final int RANGE = 24;
    public static final int BOIL_TICKS = 20 * ResourceCost.TICKS_PER_SECOND;
    public static final float SPEED_MULT = 1.5f;
    public static final String MSG_NO_BUILDING = "hud.essence.reignofnether.boiler_no_building";
    public static final String MSG_TOO_FAR = "hud.essence.reignofnether.boiler_too_far";
    public static final String MSG_IDLE = "hud.essence.reignofnether.boiler_idle";
    public static final String MSG_BOILING = "hud.essence.reignofnether.boiler_boiling";

    public SoulBoiler() {
        super(UnitAction.SOUL_BOILER, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        // only the switch: the unit's own tick casts it (the Brace precedent)
        this.autoRule(AutoRule.SELF_IN_COMBAT);
        this.autocastByUnit = true;
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Soul Boiler",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/blast_furnace_front_on.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SOUL_BOILER,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SOUL_BOILER),
            null,
            List.of(
                FormattedCharSequence.forward("Soul Boiler  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A friendly building within " + RANGE + " blocks builds and produces "
                    + Math.round((SPEED_MULT - 1) * 100) + "% faster for " + BOIL_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("Right-click: it boils a busy building by itself.", Style.EMPTY),
                FormattedCharSequence.forward("Burn what the fallen left; the works never sleep.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.blockPosition());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), buildingAt(sl, targetBp), false))
            startCooldown(unitUsing, self);
    }

    public void startCooldown(Unit unitUsing, LivingEntity self) {
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * The build/production speed factor of {@code bp} now: {@link #SPEED_MULT} while it boils (server), else 1. The boil
     * belongs to the owner it was cast for: a building that changes hands (a capture rewrites ownerName in place) stops
     * boiling, so the capturer never inherits it.
     */
    public static float speedMult(BuildingPlacement bp) {
        Level l = bp.getLevel();
        return l != null && !l.isClientSide() && l.getGameTime() < bp.boilUntilTick
            && bp.ownerName != null && bp.ownerName.equals(bp.boilOwner) ? SPEED_MULT : 1f;
    }

    /** Boils {@code b} for its current owner until game tick {@code untilTick}. Public for the game test. */
    public static void startBoil(BuildingPlacement b, long untilTick) {
        b.boilUntilTick = untilTick;
        b.boilOwner = b.ownerName;
    }

    public static boolean isBoiling(BuildingPlacement bp) {
        return speedMult(bp) > 1f;
    }

    /** Whether the boil would do anything: the building is going up, or it is built and has production queued. */
    public static boolean hasWork(BuildingPlacement bp) {
        return !bp.isBuilt || (bp instanceof ProductionPlacement pp && !pp.productionQueue.isEmpty());
    }

    /** The building whose box (one block of slack round it) holds {@code pos}, on the server; null when none. */
    @Nullable
    public static BuildingPlacement buildingAt(ServerLevel sl, BlockPos pos) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.level == sl && !b.isDestroyedServerside && b.isPosInsideBuilding(pos, 1))
                return b;
        return null;
    }

    /** Blocks from {@code self} to the nearest edge of {@code b}'s footprint (0 inside it). */
    public static double edgeDistance(LivingEntity self, BuildingPlacement b) {
        double dx = Math.max(0, Math.max(b.minCorner.getX() - self.getX(), self.getX() - (b.maxCorner.getX() + 1)));
        double dz = Math.max(0, Math.max(b.minCorner.getZ() - self.getZ(), self.getZ() - (b.maxCorner.getZ() + 1)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean isFriendly(BuildingPlacement b, String owner) {
        return b != null && b.ownerName != null && b.isFriendlyTo(owner);
    }

    /**
     * The rule: checks the building (friendly, within reach, not boiling, with work to do), then spends the essence,
     * then boils it - so a refusal never costs essence. True when cast. Public for the bot and the game test (no
     * cooldown handling here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity caster, String owner, @Nullable BuildingPlacement b, boolean quiet) {
        if (owner == null || owner.isBlank() || !isFriendly(b, owner) || b.isDestroyedServerside) {
            refuse(owner, MSG_NO_BUILDING, quiet);
            return false;
        }
        if (edgeDistance(caster, b) > RANGE) {
            refuse(owner, MSG_TOO_FAR, quiet);
            return false;
        }
        if (isBoiling(b)) {
            refuse(owner, MSG_BOILING, quiet);
            return false;
        }
        if (!hasWork(b)) {
            refuse(owner, MSG_IDLE, quiet);
            return false;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            refuse(owner, Empower.MSG_SHORT, quiet);
            return false;
        }
        startBoil(b, sl.getGameTime() + BOIL_TICKS);
        BlockPos c = b.centrePos;
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.getX() + 0.5, b.maxCorner.getY() + 1, c.getZ() + 0.5, 24, 1.2, 0.4, 1.2, 0.02);
        sl.sendParticles(ParticleTypes.CLOUD, c.getX() + 0.5, b.maxCorner.getY() + 1.5, c.getZ() + 0.5, 12, 1.0, 0.4, 1.0, 0.04);
        sl.playSound(null, c, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 2f, 0.7f);
        com.solegendary.reignofnether.barfx.BarFx.shot(sl, caster.getEyePosition(), net.minecraft.world.phys.Vec3.atCenterOf(c),
            com.solegendary.reignofnether.barfx.BarFx.K_MAGIC, caster);
        return true;
    }

    /**
     * Whether the boil would speed anything up right now (the autocast's test; a player's hand-cast only needs
     * {@link #hasWork}): a site that someone is building (a worker on it, an annex lending power, or one that builds
     * itself), or a built building whose first queued item is not held at the population cap.
     */
    public static boolean isWorking(BuildingPlacement b) {
        if (!b.isBuilt)
            return b.selfBuilding || !b.getBuilders().isEmpty()
                || (b.getLevel() != null && b.assistUntilTick > b.getLevel().getGameTime());
        if (!(b instanceof ProductionPlacement pp) || pp.productionQueue.isEmpty())
            return false;
        return pp.productionQueue.get(0).item.isBelowPopulationSupply(pp);
    }

    /**
     * The autocast's pick: the nearest friendly building within reach that is not boiling and is working
     * ({@link #isWorking}), or null. Public for the game test.
     */
    @Nullable
    public static BuildingPlacement busiestInReach(ServerLevel sl, LivingEntity caster, String owner) {
        BuildingPlacement best = null;
        double bestD = Double.MAX_VALUE;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (b.level != sl || b.isDestroyedServerside || !isFriendly(b, owner) || isBoiling(b) || !isWorking(b))
                continue;
            double d = edgeDistance(caster, b);
            if (d <= RANGE && d < bestD) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }

    private static void refuse(String owner, String msg, boolean quiet) {
        if (!quiet && owner != null && !owner.isBlank())
            HudClientboundPacket.showTempMessageI18n(owner, msg);
    }
}
