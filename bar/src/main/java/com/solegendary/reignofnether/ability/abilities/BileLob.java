package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
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
 * Gravebound (Plague Abomination): <b>Bile Lob</b> (second-t3-design.md). Aim at the ground within {@link #RANGE}: a ring
 * of {@link #RADIUS} blocks is drawn in spores for {@link #TELEGRAPH_TICKS} ticks (the danger shows first), then the
 * bile lands and a pool lies there for {@link #POOL_TICKS} ticks: once a second every enemy standing in it takes
 * {@link #POOL_DAMAGE}. The anti-turtle tool: it makes a clump, a gate or a tower base costly to hold. (The design's
 * "blocks pathing" is left out of this slice: no terrain or navigation change.) {@link #CD_SECONDS} s cooldown.
 */
public class BileLob extends Ability {

    public static final int CD_SECONDS = 20;
    public static final float RANGE = 18f;
    public static final float RADIUS = 3f;
    public static final int TELEGRAPH_TICKS = 16;
    public static final int POOL_TICKS = 6 * 20;
    public static final int TICK_EVERY = 20;
    public static final float POOL_DAMAGE = 8f;

    public BileLob() {
        super(UnitAction.BILE_LOB, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);
        this.showRangeLine = true;
        this.showRadiusCircle = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Bile Lob",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/slime_ball.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BILE_LOB,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BILE_LOB),
            null,
            List.of(
                FormattedCharSequence.forward("Bile Lob  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Lobs acid at the spot (" + (int) RANGE + " range). After 0.8 s a "
                    + (int) RADIUS + "-block pool burns enemies in it for " + (int) POOL_DAMAGE + " a second, for "
                    + POOL_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("The ring shows first: step out of it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || targetBp == null || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        Vec3 centre = new Vec3(targetBp.getX() + 0.5, targetBp.getY() + 1.0, targetBp.getZ() + 0.5);
        String owner = unitUsing.getOwnerName();
        telegraph(sl, centre);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 3f, 0.5f);
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS / 2, () -> telegraph(sl, centre));
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS, () -> {
            if (!self.isAlive())   // killed mid-cast: the bile never lands
                return;
            for (int t = 0; t < POOL_TICKS; t += TICK_EVERY)
                TaskSchedulerServerEvents.schedule(t, () -> burn(sl, self, owner, centre));
        });
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    static void telegraph(ServerLevel sl, Vec3 centre) {
        int points = (int) Math.ceil(2 * Math.PI * RADIUS);
        for (int i = 0; i < points; i++) {
            double a = i * 2 * Math.PI / points;
            sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, centre.x + Math.cos(a) * RADIUS, centre.y + 0.3,
                centre.z + Math.sin(a) * RADIUS, 1, 0.1, 0.05, 0.1, 0);
        }
    }

    /**
     * One second of the pool: {@link #POOL_DAMAGE} to every hostile unit within {@link #RADIUS} of {@code centre}.
     * Returns how many were hit. Public for the game test.
     */
    public static int burn(ServerLevel sl, LivingEntity self, String owner, Vec3 centre) {
        int hit = 0;
        for (LivingEntity le : UnitGrid.near(sl, centre.x, centre.z, RADIUS + 1, new ArrayList<>())) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl)
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(le.getY() + le.getBbHeight() / 2 - centre.y) > 4)
                continue;
            le.hurt(sl.damageSources().indirectMagic(self, self), POOL_DAMAGE);   // not mobAttack: RoN rewrites that to melee damage
            hit++;
        }
        sl.sendParticles(ParticleTypes.ITEM_SLIME, centre.x, centre.y + 0.2, centre.z, 12, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0);
        sl.sendParticles(ParticleTypes.COMPOSTER, centre.x, centre.y + 0.3, centre.z, 6, RADIUS * 0.5, 0.2, RADIUS * 0.5, 0);
        return hit;
    }
}
