package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.BloodTotemUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * <b>Blood Totem</b>, the Voodoo Shaman's mid essence spell (design/essence_casters.md, lovish's draft 2): a totem at
 * the clicked spot within {@link #RANGE} for {@link #DURATION_SECONDS} s, for {@link #ESSENCE_COST} essence and a
 * {@link #CD_SECONDS} s cooldown. Within {@link #RADIUS} of it allies attack {@link BloodTotemUnit#ATTACK_SPEED_BONUS}
 * faster and enemies move {@link BloodTotemUnit#ENEMY_SLOW} slower ({@link BloodTotemUnit} runs the aura). The totem is
 * a summon ({@link Essence#markSummoned}): its death drops no essence. Refused - nothing spent, no cooldown - out of
 * reach, with no room for the totem, or when the caster is short of essence. Autocast while fighting, at its own feet.
 */
public class BloodTotem extends Ability {

    public static final int ESSENCE_COST = 10;
    public static final int CD_SECONDS = 25;
    public static final int DURATION_SECONDS = 15;
    public static final int RADIUS = 8;
    /** How far from the shaman the totem may be planted. */
    public static final int RANGE = 8;

    public BloodTotem() {
        super(UnitAction.BLOOD_TOTEM, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.SELF_IN_COMBAT);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Blood Totem",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/abilities/hero_ember_totem.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BLOOD_TOTEM,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BLOOD_TOTEM),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Blood Totem  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Plants a totem for " + DURATION_SECONDS + " s: allies within " + RADIUS + " blocks attack "
                    + Math.round(BloodTotemUnit.ATTACK_SPEED_BONUS * 100) + "% faster, enemies move "
                    + Math.round(BloodTotemUnit.ENEMY_SLOW * 100) + "% slower.", Style.EMPTY),
                FormattedCharSequence.forward("Drums of the clans, painted in what they spilled.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    /** A click on a unit plants the totem at its feet. */
    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetBp == null)
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetBp, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * The rule: checks the spot (within reach, room to stand), then spends the essence, then plants the totem - so a
     * refusal never costs essence. {@code clicked} is the block clicked (the totem stands on it) or an open block (the
     * totem stands in it). The totem, or null when refused; no cooldown handling here (the bot and the tests).
     */
    public static BloodTotemUnit cast(ServerLevel sl, LivingEntity caster, String owner, BlockPos clicked, boolean quiet) {
        if (true) return null;   // STUB (tests-first commit): the real rule lands with the implementation
        if (owner == null || owner.isBlank())
            return null;
        BlockPos at = sl.getBlockState(clicked).isAir() ? clicked : clicked.above();
        double reach = RANGE + 0.5;
        if (caster.distanceToSqr(at.getX() + 0.5, caster.getY(), at.getZ() + 0.5) > reach * reach)
            return null;
        if (!sl.getBlockState(at).getCollisionShape(sl, at).isEmpty())
            return null;
        BloodTotemUnit totem = EntityRegistrar.BLOOD_TOTEM_UNIT.get().create(sl);
        if (totem == null)
            return null;
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            totem.discard();
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return null;
        }
        totem.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, caster.getYRot(), 0);
        totem.setOwnerName(owner);
        Essence.markSummoned(totem);   // a summon: its death drops no wisp
        sl.addFreshEntity(totem);
        sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, totem.getX(), totem.getY() + 1, totem.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
        SoundClientboundPacket.playSoundAtPos(SoundAction.TOTEM_PLACE, at);
        return totem;
    }
}
