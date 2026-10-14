package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.mana.SealServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.CombatEngineers;
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
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Stormcallers Jade Mender (combat engineer, design/combat_engineers.md): <b>Mending Rite</b>. Aim at a friendly unit
 * within {@link #RANGE} (not the Mender itself): a jade beam restores {@link #HP_PER_SECOND} HP a second to it, times
 * the caster's spell power (Overcharge, a Mana Well), for up to {@link #MAX_TICKS} ticks, draining
 * {@link #ENERGY_PER_SECOND} energy a second through the Mana core (a sustained spell, so it stalls building like every
 * Stormcaller spell). It stops when the friend is whole, dies or leaves {@link #BREAK_RANGE}, when the mana runs out -
 * and it <b>breaks</b> when the Mender is hurt, sealed or moved. Refused, with no cooldown, without a pool that can pay
 * the first second. {@link #CD_SECONDS} s cooldown from the cast.
 * <p>
 * The engineers' field repair is a free trickle; this is mana turned into a fast heal on the front. Tempo: one key unit
 * kept in the fight. Counter: the beam is plain to see - hit the Mender and it breaks, the Ink Scribe's Seal stops it,
 * and it costs the caster's economy. Ticked by CombatEngineerServerEvents.
 */
public class MendingRite extends Ability {

    public static final int CD_SECONDS = 25;
    public static final float RANGE = 8f;
    public static final float BREAK_RANGE = 10f;
    public static final float HP_PER_SECOND = 8f;
    public static final float ENERGY_PER_SECOND = 15f;
    public static final int MAX_TICKS = 6 * 20;
    static final int HEAL_EVERY_TICKS = 10;
    static final double LEASH = 1.5;
    public static final String MSG_BAD_TARGET = "hud.reignofnether.mending_rite_bad_target";
    public static final String MSG_BROKEN = "hud.reignofnether.mending_rite_broken";

    /** One rite in progress. Public for the game test. */
    public static final class Rite {
        public final LivingEntity mender;
        public final LivingEntity target;
        public final String owner;
        final ManaServerEvents.Channel channel;
        final long start;
        final Vec3 standing;
        float lastHealth;
        /** HP restored so far (for the game test and the bot's bookkeeping). */
        public float healed = 0;

        Rite(LivingEntity mender, LivingEntity target, String owner, ManaServerEvents.Channel channel, long start) {
            this.mender = mender;
            this.target = target;
            this.owner = owner;
            this.channel = channel;
            this.start = start;
            this.standing = mender.position();
            this.lastHealth = mender.getHealth();
        }
    }

    static final List<Rite> ACTIVE = new ArrayList<>();

    public MendingRite() {
        super(UnitAction.MENDING_RITE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.autoRule(AutoRule.ALLY_HURT);   // autocast: the jade beam on the most hurt friend, the first second paid from the pool
        this.autocastEnergy = ENERGY_PER_SECOND;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Mending Rite",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/emerald.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.MENDING_RITE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.MENDING_RITE),
            null,
            List.of(
                FormattedCharSequence.forward("Mending Rite  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Channels into a friendly unit within " + (int) RANGE + " blocks: " + (int) HP_PER_SECOND
                    + " HP a second for up to " + MAX_TICKS / 20 + " s, " + (int) ENERGY_PER_SECOND + " energy a second.", Style.EMPTY),
                FormattedCharSequence.forward("Breaks if the Mender is hurt, sealed or moved.", Style.EMPTY),
                FormattedCharSequence.forward("Stillness is the first stitch.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (begin(sl, self, unitUsing.getOwnerName(), targetEntity, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** A ground click has nothing to mend. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /**
     * Opens the rite on {@code target} if it is another friendly, hurt unit in range and the first second of mana is
     * paid; null (HUD message unless quiet) when refused. No cooldown handling here. Public for the bot and the test.
     */
    @Nullable
    public static Rite begin(ServerLevel sl, LivingEntity mender, String owner, @Nullable LivingEntity target, boolean quiet) {
        if (owner == null || owner.isBlank())
            return null;
        if (target == null || target == mender || !target.isAlive() || !(target instanceof Unit u)
                || !CombatEngineers.isFriendly(owner, u.getOwnerName()) || target.getHealth() >= target.getMaxHealth()
                || mender.distanceToSqr(target) > (RANGE + target.getBbWidth() / 2) * (RANGE + target.getBbWidth() / 2)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return null;
        }
        ManaServerEvents.Channel ch = ManaServerEvents.openChannel(mender, owner, ENERGY_PER_SECOND);
        if (ch == null) {
            if (!quiet && ManaServerEvents.lastRefusal().langKey != null)
                HudClientboundPacket.showTempMessageI18n(owner, ManaServerEvents.lastRefusal().langKey);
            return null;
        }
        ACTIVE.removeIf(r -> r.mender == mender);
        Rite rite = new Rite(mender, target, owner, ch, sl.getGameTime());
        ACTIVE.add(rite);
        sl.playSound(null, mender.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.4f, 1.2f);
        return rite;
    }

    /** The Mender's live rite, or null. Public for the game test. */
    @Nullable
    public static Rite riteOf(LivingEntity mender) {
        for (int i = 0, n = ACTIVE.size(); i < n; i++)
            if (ACTIVE.get(i).mender == mender)
                return ACTIVE.get(i);
        return null;
    }

    /** One tick of every rite: break checks, the heal every half second, the mana once a second, the beam. */
    public static void tick(long gameTime) {
        if (ACTIVE.isEmpty())
            return;
        Iterator<Rite> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Rite r = it.next();
            LivingEntity m = r.mender, t = r.target;
            if (!m.isAlive() || m.isRemoved() || !(m.level() instanceof ServerLevel sl)) {
                it.remove();
                r.channel.stop();
                continue;
            }
            float h = m.getHealth();
            boolean hurt = h < r.lastHealth - 0.01f;
            r.lastHealth = h;
            if (hurt || SealServerEvents.isSealed(m) || m.position().distanceToSqr(r.standing) > LEASH * LEASH) {
                it.remove();
                r.channel.stop();
                HudClientboundPacket.showTempMessageI18n(r.owner, MSG_BROKEN);
                sl.sendParticles(ParticleTypes.SMOKE, m.getX(), m.getY() + 1, m.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
                continue;
            }
            long age = gameTime - r.start;
            if (!t.isAlive() || t.isRemoved() || t.level() != m.level() || t.distanceToSqr(m) > BREAK_RANGE * BREAK_RANGE
                    || t.getHealth() >= t.getMaxHealth() || age >= MAX_TICKS || !r.channel.isActive()) {
                it.remove();
                r.channel.stop();
                continue;
            }
            // the first second was paid when the rite opened; each later second is paid as it starts
            if (age > 0 && age % 20 == 0 && ManaServerEvents.channel(r.channel) <= 0) {
                it.remove();
                continue;
            }
            if (age % HEAL_EVERY_TICKS == 0) {
                float amount = Math.min(t.getMaxHealth() - t.getHealth(),
                    HP_PER_SECOND * HEAL_EVERY_TICKS / 20f * r.channel.power() * Math.max(0.25f, r.channel.fraction()));
                t.heal(amount);
                r.healed += amount;
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, t.getX(), t.getY() + t.getBbHeight() * 0.7, t.getZ(),
                    3, 0.25, 0.25, 0.25, 0.01);
            }
            if (age % 4 == 0)
                com.solegendary.reignofnether.barfx.BarFx.beam(sl, new Vec3(m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ()),
                    new Vec3(t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ()), com.solegendary.reignofnether.barfx.BarFx.N_STORM, 6);
        }
    }

    public static void clearAll() {
        for (Rite r : ACTIVE)
            r.channel.stop();
        ACTIVE.clear();
    }
}
