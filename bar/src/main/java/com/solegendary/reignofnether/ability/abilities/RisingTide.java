package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.tide.TideSeerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Tidewrought essence caster (the Tide Seer; design/essence_casters.md): <b>Rising Tide</b>, the big spell. A wave breaks
 * outward from the caster: every enemy ground unit within {@link #RADIUS} blocks is shoved {@link #PUSH_BLOCKS} blocks
 * away from it (TideSeerServerEvents.shove; knockback resistance shortens it), and every friendly unit within the same
 * reach that is on fire is put out. {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s cooldown. Refused - nothing
 * spent, no cooldown - when there is no enemy to push and no burning friend to soak, or the caster is short of essence.
 * Autocast: on a crowd (3 enemies within its reach).
 */
public class RisingTide extends Ability {

    public static final int ESSENCE_COST = 20;
    public static final int CD_SECONDS = 60;
    public static final int RADIUS = 8;
    public static final double PUSH_BLOCKS = 6;
    public static final String MSG_NO_TARGET = "hud.essence.reignofnether.rising_tide_no_target";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public RisingTide() {
        // self-centred (no range): the CROWD rule then counts enemies round the caster in this radius
        super(UnitAction.RISING_TIDE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Rising Tide",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/nautilus_shell.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.RISING_TIDE),
            null,
            List.of(
                FormattedCharSequence.forward("Rising Tide  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A wave breaks round the Tide Seer: enemy ground units within " + RADIUS
                    + " blocks are pushed " + (int) PUSH_BLOCKS + " blocks back, and burning allies are put out.", Style.EMPTY),
                FormattedCharSequence.forward("The tide comes in for everyone.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, (BlockPos) null);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), false) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** What one wave did. */
    public record Wave(List<LivingEntity> pushed, List<LivingEntity> soaked) { }

    /**
     * The rule: finds the enemy ground units to push and the burning friends to soak round {@code caster}, then spends
     * the essence, then breaks the wave - so a refusal never costs essence. Returns what it did, or null when refused.
     * Public for the bot and the game test (no cooldown handling here).
     */
    public static Wave cast(ServerLevel sl, LivingEntity caster, String owner, boolean quiet) {
        if (owner == null || owner.isBlank())
            return null;
        List<LivingEntity> enemies = new ArrayList<>(), burning = new ArrayList<>();
        for (LivingEntity le : UnitGrid.near(sl, caster.getX(), caster.getZ(), RADIUS + 1, scratch)) {
            double dx = le.getX() - caster.getX(), dz = le.getZ() - caster.getZ();
            if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(le.getY() - caster.getY()) > 4 || !le.isAlive())
                continue;
            if (TideSeerServerEvents.isEnemyGround(owner, le))
                enemies.add(le);
            else if (le.isOnFire() && le instanceof Unit u && AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner))
                burning.add(le);
        }
        if (enemies.isEmpty() && burning.isEmpty()) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_TARGET);
            return null;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return null;
        }
        Vec3 from = caster.position();
        for (LivingEntity le : enemies)
            TideSeerServerEvents.shove(le, from, PUSH_BLOCKS);
        for (LivingEntity le : burning) {
            le.clearFire();
            sl.sendParticles(ParticleTypes.CLOUD, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
        }
        // the look: a ring of breaking water running out to the edge, and a burst at the caster's feet
        for (int r = 2; r <= RADIUS; r += 2) {
            int n = r * 6;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                sl.sendParticles(ParticleTypes.SPLASH, from.x + Math.cos(a) * r, from.y + 0.3, from.z + Math.sin(a) * r,
                    2, 0.2, 0.2, 0.2, 0.1);
            }
        }
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, from.x, from.y + 0.3, from.z, 30, 1.0, 0.3, 1.0, 0.1);
        sl.playSound(null, caster.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 3f, 0.5f);
        sl.playSound(null, caster.blockPosition(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.NEUTRAL, 2f, 0.7f);
        return new Wave(enemies, burning);
    }
}
