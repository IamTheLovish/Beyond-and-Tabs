package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.SoulbinderServerEvents;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Soulbinder: <b>Wail</b>, its cheap essence ability (design/essence_casters.md, lovish approved draft 2 on Oct 29 2026:
 * "enemies in a cone flee for 2 s"). Aimed at a unit or the ground: every enemy unit in the {@link #HALF_ANGLE_DEGREES}
 * cone in front of the Soulbinder, toward the aim and up to {@link #RANGE} blocks out, flees straight away from it for
 * {@link #FLEE_SECONDS} s - the Wraith's Fear (uncontrollable and scared) on a crowd, shorter. Heroes and units that
 * cannot be interrupted stand firm, as they do against Fear. {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s.
 * Refused - nothing spent, no cooldown - with no enemy in the cone or short of essence. Autocast at the nearest enemy.
 */
public class Wail extends Ability {

    public static final int ESSENCE_COST = 5;
    public static final int CD_SECONDS = 12;
    /** How far the cone reaches (not in lovish's draft: a placeholder for him). */
    public static final int RANGE = 6;
    /** Half the cone's opening: 45 degrees each side of the aim, a quarter circle (placeholder). */
    public static final int HALF_ANGLE_DEGREES = 45;
    public static final int FLEE_SECONDS = 2;
    /** Where a fleeing unit runs to: Fear's two blocks per second of fear, straight away from the Soulbinder. */
    public static final int FLEE_DISTANCE = FLEE_SECONDS * 2;
    public static final String MSG_NO_ENEMY = "hud.essence.reignofnether.wail_no_enemy";

    public Wail() {
        super(UnitAction.WAIL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);
        this.showRangeCircle = true;
    }

    /** The autocast waits for the essence: no order, so no "not enough essence" every second while it fights. */
    @Override
    public boolean canAutocastNow(ServerLevel level, Unit unit) {
        return super.canAutocastNow(level, unit) && unit instanceof LivingEntity le && Essence.get(le) >= ESSENCE_COST;
    }

    /** Fear's exceptions: the autocast does not aim at a unit Wail would not move. */
    @Override
    public boolean autocastTargetOk(Unit caster, LivingEntity target) {
        return !(target instanceof HeroUnit) && !(target instanceof Unit u && u.uninterruptable());
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Wail",
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/abilities/chilling_screech.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.WAIL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.WAIL),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Wail  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Enemies in a cone up to " + RANGE + " blocks in front of the Soulbinder flee from it for "
                    + FLEE_SECONDS + " s. Heroes stand firm.", Style.EMPTY),
                FormattedCharSequence.forward("The dead remember how they died, and tell the living.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            useAt(level, unitUsing, targetEntity.position());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (targetBp != null)
            useAt(level, unitUsing, Vec3.atBottomCenterOf(targetBp));
    }

    private void useAt(Level level, Unit unitUsing, Vec3 aim) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || !isOffCooldown(unitUsing))
            return;
        if (!cast(sl, self, unitUsing.getOwnerName(), aim, false).isEmpty()) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * The rule: finds the enemies in the cone toward {@code aim}, then spends the essence, then scares them - so a
     * refusal never costs essence. Returns the units that flee (empty: refused). Public for the bot and the game test
     * (no cooldown handling here).
     */
    public static List<LivingEntity> cast(ServerLevel sl, LivingEntity caster, String owner, Vec3 aim, boolean quiet) {
        if (owner == null || owner.isEmpty() || !(caster instanceof Mob self))
            return List.of();
        List<LivingEntity> scared = inCone(sl, self, aim);
        if (scared.isEmpty()) {
            if (!quiet)
                SoulbinderServerEvents.warn(sl, owner, MSG_NO_ENEMY);
            return List.of();
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                SoulbinderServerEvents.warn(sl, owner, Empower.MSG_SHORT);
            return List.of();
        }
        for (LivingEntity le : scared)
            scare(caster, le);
        SoundClientboundPacket.playSoundAtPos(SoundAction.WRAITH_FEAR, caster.blockPosition());
        Vec3 dir = facing(caster, aim);
        for (int i = 1; i <= RANGE; i++)
            sl.sendParticles(ParticleTypes.SCULK_SOUL, caster.getX() + dir.x * i, caster.getY() + 1.0, caster.getZ() + dir.z * i,
                2 + i, 0.25 * i, 0.3, 0.25 * i, 0.01);
        return scared;
    }

    /** The horizontal unit vector from the caster toward {@code aim}; its own facing when aimed at its feet. */
    static Vec3 facing(LivingEntity caster, Vec3 aim) {
        Vec3 d = new Vec3(aim.x - caster.getX(), 0, aim.z - caster.getZ());
        if (d.lengthSqr() < 0.25)
            d = Vec3.directionFromRotation(0, caster.getYRot());
        return new Vec3(d.x, 0, d.z).normalize();
    }

    /**
     * The enemy units Wail moves: within {@link #RANGE} blocks (to their near side) and {@link #HALF_ANGLE_DEGREES} of
     * the aim, not heroes, not uninterruptable (Fear's exceptions).
     */
    static List<LivingEntity> inCone(ServerLevel sl, Mob caster, Vec3 aim) {
        Vec3 dir = facing(caster, aim);
        double cos = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity le : HeroFx.enemies(sl, caster, caster.position(), RANGE + 2)) {
            if (!(le instanceof Unit u) || le instanceof HeroUnit || u.uninterruptable())
                continue;
            Vec3 to = new Vec3(le.getX() - caster.getX(), 0, le.getZ() - caster.getZ());
            double dist = to.length();
            if (dist - le.getBbWidth() / 2 > RANGE)
                continue;
            if (dist > 0.5 && to.scale(1 / dist).dot(dir) < cos)
                continue;
            out.add(le);
        }
        return out;
    }

    /** Fear's flight, shorter: uncontrollable and scared for the flee, sent straight away from the caster. */
    static void scare(LivingEntity caster, LivingEntity le) {
        Unit u = (Unit) le;
        Vec3 away = new Vec3(le.getX() - caster.getX(), 0, le.getZ() - caster.getZ());
        away = away.lengthSqr() < 1e-4 ? facing(caster, le.position()) : away.normalize();
        Vec3 to = le.position().add(away.scale(FLEE_DISTANCE));
        Unit.fullResetBehaviours(u);
        if (u.getMoveGoal() != null)
            u.getMoveGoal().setMoveTarget(BlockPos.containing(to));
        le.addEffect(new MobEffectInstance(MobEffectRegistrar.UNCONTROLLABLE.get(), FLEE_SECONDS * 20, 0, true, false));
        le.addEffect(new MobEffectInstance(MobEffectRegistrar.FEARFUL.get(), FLEE_SECONDS * 20, 0, true, false));
    }
}
