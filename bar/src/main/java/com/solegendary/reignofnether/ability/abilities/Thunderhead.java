package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.barfx.LightningFx;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.Blink;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stormcallers essence caster (the Spark Sage; design/essence_casters.md, "Last three casters", revision-scaled):
 * <b>Thunderhead</b>, the big spell. A storm cloud over the aimed spot (within {@link #RANGE} blocks) for
 * {@link #DURATION_TICKS} ticks: once a second it strikes the enemy unit nearest its centre within {@link #RADIUS}
 * blocks for {@link #DAMAGE} indirect magic from the Sage - ten strikes in all, a {@link LightningFx#bolt} each. It
 * stays where it was cast and blows out if the Sage dies (the Celestial Dragon's Tempest's rule: kill the caster).
 * {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s cooldown. Refused - nothing spent, no cooldown - when no enemy
 * stands within {@link #RADIUS} of the spot or the caster is short of essence. Autocast: on a crowd (3 enemies in the
 * circle), so the bots never spend 20 essence on a lone scout.
 * <p>
 * The Tempest's area-record pattern: a short list of live clouds ticked from the server tick, one UnitGrid query per
 * strike into a shared scratch list, a ring of cloud motes every {@link #FX_TICKS} ticks. Not saved: a cloud lasts 10 s.
 */
public class Thunderhead extends Ability {

    public static final int ESSENCE_COST = 20;
    public static final int CD_SECONDS = 60;
    public static final float DAMAGE = 25f;
    public static final int RADIUS = 6;
    public static final int DURATION_TICKS = 10 * ResourceCost.TICKS_PER_SECOND;
    public static final int STRIKE_EVERY = ResourceCost.TICKS_PER_SECOND;
    /** Not in the design: how far away the Sage can raise the cloud, a placeholder for lovish. */
    public static final int RANGE = 12;
    /** A foe this far above or below the cloud's ground still counts. */
    static final double HEIGHT = 8;
    /** How high above its ground the cloud (and every bolt's start) sits. */
    public static final double CLOUD_HEIGHT = 7;
    static final int FX_TICKS = 4;
    public static final String MSG_NO_TARGET = "hud.essence.reignofnether.thunderhead_no_target";

    /** One live cloud. Public for the game test. */
    public static final class Cloud {
        public final ServerLevel level;
        public final String owner;
        public final LivingEntity caster;
        public final double x, y, z;    // the ground under its centre
        public final long startTick;
        public int strikes = 0;
        boolean ended = false;

        Cloud(ServerLevel level, String owner, LivingEntity caster, double x, double y, double z, long startTick) {
            this.level = level;
            this.owner = owner;
            this.caster = caster;
            this.x = x;
            this.y = y;
            this.z = z;
            this.startTick = startTick;
        }

        public boolean isActive() { return !ended; }

        boolean covers(LivingEntity le) {
            double dx = le.getX() - x, dz = le.getZ() - z;
            return le.level() == level && dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(le.getY() - y) <= HEIGHT;
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public Thunderhead() {
        super(UnitAction.THUNDERHEAD, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Thunderhead",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/firework_star.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.THUNDERHEAD,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.THUNDERHEAD),
            null,
            List.of(
                FormattedCharSequence.forward("Thunderhead  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A storm cloud over a spot up to " + RANGE + " blocks away for " + DURATION_TICKS / 20
                    + " s: every second it strikes the enemy nearest its centre within " + RADIUS + " blocks for " + (int) DAMAGE + ".", Style.EMPTY),
                FormattedCharSequence.forward("It blows out if the Sage falls.", Style.EMPTY),
                FormattedCharSequence.forward("The sky keeps what the battlefield gave it.", Style.EMPTY.withItalic(true))
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
        BlockPos at = MyMath.getXZRangeLimitedBlockPos(self.blockPosition(), targetBp, RANGE);
        at = new BlockPos(at.getX(), targetBp.getY(), at.getZ());
        if (cast(sl, self, unitUsing.getOwnerName(), at, false) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        if (self.level().getEntity(self.getId()) == self)
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * The rule: looks for an enemy under the cloud's circle round {@code at} (a ground block or the cell above it), then
     * spends the essence, then raises the cloud - so a refusal never costs essence. Returns the cloud, or null when
     * refused. Public for the bot and the game test (no cooldown handling here).
     */
    @Nullable
    public static Cloud cast(ServerLevel sl, LivingEntity caster, String owner, BlockPos at, boolean quiet) {
        if (true) return null;   // STUB (tests first)
        if (owner == null || owner.isBlank() || at == null)
            return null;
        double cx = at.getX() + 0.5, cz = at.getZ() + 0.5;
        int groundY = Blink.groundY(sl, at.getX(), at.getZ(), at.getY() + 1);
        Cloud c = new Cloud(sl, owner, caster, cx, groundY, cz, sl.getGameTime());
        if (nearestFoe(c) == null) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_TARGET);
            return null;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return null;
        }
        CLOUDS.add(c);
        motes(c, 24);
        sl.playSound(null, BlockPos.containing(cx, groundY, cz), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 2.0f, 0.7f);
        return c;
    }

    /** Read-only view (game test / debugging). */
    public static List<Cloud> clouds() {
        return Collections.unmodifiableList(CLOUDS);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || CLOUDS.isEmpty())
            return;
        for (int i = CLOUDS.size() - 1; i >= 0; i--) {
            Cloud c = CLOUDS.get(i);
            if (c.level.getServer() != evt.getServer())
                continue;
            if (!tickCloud(c))
                end(CLOUDS.remove(i), true);
        }
    }

    /**
     * One tick of a cloud: a strike on every whole second of its life (seconds 1 to 10), motes in between; false once
     * it is over or its caster is gone. Public for the game test.
     */
    public static boolean tickCloud(Cloud c) {
        if (c.ended || !c.caster.isAlive())
            return false;
        long age = c.level.getGameTime() - c.startTick;
        if (age > 0 && age % STRIKE_EVERY == 0 && age <= DURATION_TICKS)
            strike(c);
        if (age % FX_TICKS == 0)
            motes(c, 6);
        return age < DURATION_TICKS;
    }

    /** One strike: the enemy unit nearest the centre takes {@link #DAMAGE}. Returns the unit struck, or null. */
    @Nullable
    static LivingEntity strike(Cloud c) {
        LivingEntity target = nearestFoe(c);
        if (target == null)
            return null;
        double x1 = target.getX(), y1 = target.getY() + target.getBbHeight() * 0.6, z1 = target.getZ();
        LightningFx.bolt(c.level, c.x, c.y + CLOUD_HEIGHT, c.z, x1, y1, z1);
        c.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x1, y1, z1, 8, 0.3, 0.3, 0.3, 0.1);
        // indirect magic from the Sage: kill credit and veterancy go to it, nothing catches fire
        target.hurt(c.level.damageSources().indirectMagic(c.caster, c.caster), DAMAGE);
        c.level.playSound(null, BlockPos.containing(x1, y1, z1), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.6f, 1.5f);
        c.strikes++;
        return target;
    }

    @Nullable
    static LivingEntity nearestFoe(Cloud c) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : UnitGrid.near(c.level, c.x, c.z, RADIUS, scratch)) {
            if (!le.isAlive() || !(le instanceof Unit u) || !c.covers(le) || !Blink.isEnemy(c.owner, u))
                continue;
            double dx = le.getX() - c.x, dz = le.getZ() - c.z, d = dx * dx + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        scratch.clear();
        return best;
    }

    static void end(Cloud c, boolean fx) {
        if (c.ended)
            return;
        c.ended = true;
        if (fx)
            c.level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + CLOUD_HEIGHT, c.z, 20, RADIUS * 0.4, 0.6, RADIUS * 0.4, 0.04);
    }

    /** Takes a cloud down now (the game test cleans up with this). */
    public static void dispel(Cloud c) {
        if (CLOUDS.remove(c))
            end(c, false);
    }

    // the cloud: a turning ring of motes at cloud height and a few in the middle
    private static void motes(Cloud c, int n) {
        double phase = (c.level.getGameTime() % 60) / 60.0 * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = phase + Math.PI * 2 * i / n;
            c.level.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * RADIUS * 0.8, c.y + CLOUD_HEIGHT, c.z + Math.sin(a) * RADIUS * 0.8,
                1, 0.2, 0.1, 0.2, 0.0);
        }
        c.level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + CLOUD_HEIGHT, c.z, Math.max(1, n / 2), RADIUS * 0.3, 0.3, RADIUS * 0.3, 0.0);
    }

    /** Defeat: the owner's clouds blow out. */
    public static void clearOwner(String owner) {
        for (int i = CLOUDS.size() - 1; i >= 0; i--)
            if (CLOUDS.get(i).owner.equals(owner))
                end(CLOUDS.remove(i), false);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        for (Cloud c : CLOUDS)
            end(c, false);
        CLOUDS.clear();
    }
}
