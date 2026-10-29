package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.AngelUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Angel Caller's three essence summons (design/essence_casters.md; lovish, Oct 29 2026: "the angels casters can
 * summon three different angels that help: one that heals, one that is a flying archer and another that is a melee
 * engager that can fly"). Each cast spends the caster's essence and calls one temporary angel down beside it: it fights
 * under the caster's owner, takes orders like any unit, is marked summoned (its death drops no wisp) and fades when its
 * time is up ({@link AngelUnit}).
 * <p>
 * At most one angel of each kind per caster: while it lives a recast is <b>refused</b> - nothing spent, no cooldown -
 * rather than replacing it, so an autocast never burns essence on a duplicate and the AutoCaster's "a refusal is not a
 * cast" holds. Refused the same way when the caster is short of essence.
 */
public class CallAngel extends Ability {

    /** Each angel: its order, essence, cooldown and lifetime (lovish's tiers: cheap 5 / 12 s, mid 10 / 25 s, big 20 / 60 s). */
    public enum Kind {
        MENDER(UnitAction.CALL_MENDER_ANGEL, 5, 12, 20, "Mender Angel", "mender_angel"),
        ARCHER(UnitAction.CALL_SERAPH_ARCHER, 10, 25, 25, "Seraph Archer", "seraph_archer"),
        SWORD(UnitAction.CALL_SWORD_SERAPH, 20, 60, 30, "Sword Seraph", "sword_seraph");

        public final UnitAction action;
        public final int essence, cooldownSeconds, lifeSeconds;
        public final String title, id;

        Kind(UnitAction action, int essence, int cooldownSeconds, int lifeSeconds, String title, String id) {
            this.action = action;
            this.essence = essence;
            this.cooldownSeconds = cooldownSeconds;
            this.lifeSeconds = lifeSeconds;
            this.title = title;
            this.id = id;
        }

        public EntityType<? extends AngelUnit> type() {
            return switch (this) {
                case MENDER -> EntityRegistrar.MENDER_ANGEL_UNIT.get();
                case ARCHER -> EntityRegistrar.SERAPH_ARCHER_UNIT.get();
                case SWORD -> EntityRegistrar.SWORD_SERAPH_UNIT.get();
            };
        }
    }

    public static final String MSG_SHORT = "hud.essence.reignofnether.angel_short";
    public static final String MSG_ALIVE = "hud.essence.reignofnether.angel_alive";

    public final Kind kind;

    public CallAngel(Kind kind) {
        super(kind.action, kind.cooldownSeconds * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
        this.kind = kind;
        // the healer comes when a friend is hurt, the fighters when the caster is in a fight (and its angel is not out)
        this.autoRule(kind == Kind.MENDER ? AutoRule.ALLY_HURT : AutoRule.SUMMON_WHEN_FREE);
        // the AutoCaster tries the call only with the essence for it (else the caster is ordered and refuses every look)
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton(kind.title,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/" + kind.id + ".png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(kind.action),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward(kind.title + "  (" + kind.essence + " essence, " + kind.cooldownSeconds + " s cooldown)",
                    Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(description(), Style.EMPTY),
                FormattedCharSequence.forward("It fights for " + kind.lifeSeconds + " s, then returns to the light. One at a time.", Style.EMPTY),
                FormattedCharSequence.forward("", Style.EMPTY),
                FormattedCharSequence.forward("Right-click: autocast on/off", Style.EMPTY)
            ),
            this,
            unit);
    }

    private String description() {
        return switch (kind) {
            case MENDER -> "Calls a flying angel that heals hurt friends near it.";
            case ARCHER -> "Calls a flying angel archer that shoots enemies from the air.";
            case SWORD -> "Calls a flying angel swordsman that dives onto enemies and fights them up close.";
        };
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        use(level, unitUsing);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing);
    }

    private void use(Level level, Unit unitUsing) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self))
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), kind, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Test hook: told of every refused call (the caster, the refusal's message key); null in play. */
    public static java.util.function.BiConsumer<LivingEntity, String> refusalListener;

    private static void refused(LivingEntity caster, String owner, String msg, boolean quiet) {
        if (!quiet)
            HudClientboundPacket.showTempMessageI18n(owner, msg);
        if (refusalListener != null)
            refusalListener.accept(caster, msg);
    }

    /** The caster stays behind its army: its fighters come when a friend within this many blocks is in a fight. */
    public static final double CALL_FOR_FIGHTS_WITHIN = 12;

    @Override
    public double summonForFightsWithin() {
        return CALL_FOR_FIGHTS_WITHIN;
    }

    /**
     * The AutoCaster leaves the call alone while its angel is out: the call would be refused ("already here" to the
     * owner, and the caster's behaviours reset) every look from the end of the cooldown until the angel fades.
     */
    @Override
    public boolean canAutocastNow(ServerLevel level, Unit unit) {
        return super.canAutocastNow(level, unit);
    }

    @Override
    public int liveSummons(ServerLevel level, Unit unit) {
        return unit instanceof LivingEntity le && AngelUnit.liveFor(le, kind) != null ? 1 : 0;
    }

    /**
     * The rule: refuses (null, nothing spent) for a caster with no owner, while its angel of this kind lives, or when
     * it is short of essence; otherwise calls the angel down and spends the essence last. Public for the bot and the
     * game test (no cooldown handling here).
     */
    @Nullable
    public static AngelUnit cast(ServerLevel sl, LivingEntity caster, String owner, Kind kind, boolean quiet) {
        if (owner == null || owner.isEmpty() || caster == null || !caster.isAlive())
            return null;
        if (AngelUnit.liveFor(caster, kind) != null) {
            refused(caster, owner, MSG_ALIVE, quiet);
            return null;
        }
        if (Essence.get(caster) < kind.essence) {
            refused(caster, owner, MSG_SHORT, quiet);
            return null;
        }
        Entity e = UnitServerEvents.spawnMob(kind.type(), sl, caster.blockPosition(), owner);
        if (!(e instanceof AngelUnit angel)) {
            if (e != null)
                e.discard();
            return null;
        }
        Essence.markSummoned(angel);
        if (!Essence.trySpend(caster, kind.essence)) {   // checked above; spent last so a failed spawn costs nothing
            angel.discard();
            return null;
        }
        double side = (kind.ordinal() - 1) * 1.2;   // the three angels never stack on one spot
        angel.moveTo(caster.getX() + side, caster.getY() + angel.flightHeight(), caster.getZ() + 0.8, caster.getYRot(), 0);
        angel.bind(caster, kind.lifeSeconds * ResourceCost.TICKS_PER_SECOND);
        sl.sendParticles(ParticleTypes.END_ROD, angel.getX(), angel.getY() + 0.5, angel.getZ(), 14, 0.3, 0.6, 0.3, 0.04);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, caster.getX(), caster.getY() + 1.2, caster.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
        sl.playSound(null, caster.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.7f, 1.6f);
        return angel;
    }
}
