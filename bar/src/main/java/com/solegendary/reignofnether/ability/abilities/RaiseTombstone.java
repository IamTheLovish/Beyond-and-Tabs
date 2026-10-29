package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.TombcallerServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.TombstoneUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Tombcaller: <b>Raise Tombstone</b> (design/essence_casters.md "Gravebound Gravecaller", lovish's idea: "death can
 * summon tomb stones that spread creep and summon units passively"). Aimed at the ground within {@link #RANGE} blocks:
 * a {@link TombstoneUnit} rises there for the caster's owner - a summon (no essence, no XP), spreading grave creep and
 * raising a Risen Skeleton every 20 s (the stone's numbers are on TombstoneUnit). At most {@link #MAX_STONES} per
 * Tombcaller. {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s. Refused - nothing spent, no cooldown - out of reach,
 * where a stone does not fit, at the cap, or short of essence. Autocast on a crowd: the creep slows them, the skeleton
 * fights them.
 */
public class RaiseTombstone extends Ability {

    public static final int ESSENCE_COST = 10;
    public static final int CD_SECONDS = 25;
    public static final int RANGE = 10;
    public static final int MAX_STONES = 3;
    /** How far above or below the Tombcaller the stone's ground may be (not in the draft: a placeholder). */
    public static final int MAX_RISE = 6;
    public static final String MSG_NO_ROOM = "hud.essence.reignofnether.tombstone_no_room";
    public static final String MSG_TOO_FAR = "hud.essence.reignofnether.tombstone_too_far";
    public static final String MSG_MAX = "hud.essence.reignofnether.tombstone_max";

    public RaiseTombstone() {
        super(UnitAction.RAISE_TOMBSTONE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, TombstoneUnit.CREEP_MAX_RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    /**
     * The autocast waits for the essence and for room under the cap: no order it would refuse, so no "not enough
     * essence" or "already keeps three tombstones" every second while it fights (PR 366 review round 1).
     */
    @Override
    public boolean canAutocastNow(ServerLevel level, Unit unit) {
        return super.canAutocastNow(level, unit) && unit instanceof LivingEntity le && Essence.get(le) >= ESSENCE_COST
            && TombcallerServerEvents.stoneCount(le) < MAX_STONES;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Raise Tombstone",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/chiseled_deepslate.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.RAISE_TOMBSTONE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.RAISE_TOMBSTONE),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Raise Tombstone  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A tombstone (" + (int) TombstoneUnit.HEALTH + " HP) rises within " + RANGE + " blocks for "
                    + TombstoneUnit.LIFETIME_SECONDS + " s. Its grave creep spreads to " + (int) TombstoneUnit.CREEP_MAX_RADIUS
                    + " blocks: Gravebound friends on it regenerate " + (int) TombstoneUnit.CREEP_REGEN_PER_SECOND
                    + " HP/s, enemies move " + Math.round(TombstoneUnit.CREEP_ENEMY_SLOW * 100) + "% slower.", Style.EMPTY),
                FormattedCharSequence.forward("Every " + TombstoneUnit.RAISE_EVERY_SECONDS + " s it raises a skeleton for "
                    + TombstoneUnit.SKELETON_LIFETIME_SECONDS + " s. At most " + MAX_STONES + " stones.", Style.EMPTY),
                FormattedCharSequence.forward("Break the stone and the ground forgets.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetBp == null
                || !isOffCooldown(unitUsing))
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetBp, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** A click on a unit raises the stone at its feet. */
    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.blockPosition());
    }

    /**
     * The rule: checks the reach, the cap and the spot, then spends the essence, then raises the stone - a refusal
     * costs nothing. {@code at} is the clicked block (the ground) or the air above it. Returns the stone (null:
     * refused). Public for the bot and the game test (no cooldown handling here).
     */
    @Nullable
    public static TombstoneUnit cast(ServerLevel sl, LivingEntity caster, String owner, BlockPos at, boolean quiet) {
        if (owner == null || owner.isEmpty() || at == null)
            return null;
        // the clicked ground block: the stone stands on it; a click in the air over ground: it stands there
        BlockPos feet = solid(sl, at) ? at.above() : at;
        Vec3 spot = Vec3.atBottomCenterOf(feet);
        double dx = spot.x - caster.getX(), dz = spot.z - caster.getZ();
        if (dx * dx + dz * dz > (RANGE + 0.5) * (RANGE + 0.5) || Math.abs(spot.y - caster.getY()) > MAX_RISE) {
            if (!quiet)
                TombcallerServerEvents.warn(sl, owner, MSG_TOO_FAR);
            return null;
        }
        if (TombcallerServerEvents.stoneCount(caster) >= MAX_STONES) {
            if (!quiet)
                TombcallerServerEvents.warn(sl, owner, MSG_MAX);
            return null;
        }
        EntityType<TombstoneUnit> type = EntityRegistrar.TOMBSTONE_UNIT.get();
        if (!solid(sl, feet.below()) || !sl.noCollision(type.getDimensions().makeBoundingBox(spot))) {
            if (!quiet)
                TombcallerServerEvents.warn(sl, owner, MSG_NO_ROOM);
            return null;
        }
        TombstoneUnit stone = type.create(sl);
        if (stone == null)
            return null;
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            stone.discard();
            if (!quiet)
                TombcallerServerEvents.warn(sl, owner, Empower.MSG_SHORT);
            return null;
        }
        stone.moveTo(spot.x, spot.y, spot.z, caster.getYRot(), 0);
        stone.setOwnerName(owner);
        Essence.markSummoned(stone);   // before it joins: no essence, no XP, no veterancy for its killer
        stone.begin(caster, sl.getGameTime());
        sl.addFreshEntity(stone);
        sl.sendParticles(ParticleTypes.SOUL, spot.x, spot.y + 0.6, spot.z, 16, 0.4, 0.4, 0.4, 0.03);
        BarFx.shot(sl, caster.getEyePosition(), spot.add(0, 0.8, 0), BarFx.K_MAGIC, caster);
        SoundClientboundPacket.playSoundAtPos(SoundAction.WRAITH_POSSESS_PARTIAL, feet);
        return stone;
    }

    private static boolean solid(ServerLevel sl, BlockPos bp) {
        return !sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty();
    }
}
