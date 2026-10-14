package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.defence.Trap;
import com.solegendary.reignofnether.building.buildings.defence.WallGate;
import com.solegendary.reignofnether.building.buildings.defence.WallSegment;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
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

import javax.annotation.Nullable;
import java.util.List;

/**
 * Horde Wrecker (combat engineer, design/combat_engineers.md): <b>Tear Down</b>. Aim at an enemy wall segment, gate or
 * revealed trap whose nearest block is within {@link #REACH}: the Wrecker rips it down at once (enough damage, through
 * the ordinary attacker path, to bring an unbraced one down) and its owner reclaims {@link #REFUND_SHARE} of its metal.
 * Refused, with no cooldown, on anything else - towers, labs, economy, a hidden trap (it can't be targeted at all) or a
 * friend's wall - and beyond reach (the player or the bot walks the Wrecker up first, PlantCharge's rule).
 * <p>
 * BAR lets constructors reclaim fortification walls, the enemy's too, to open a path; the Horde do it with a crowbar.
 * Tempo: opens a breach for a Momentum charge. Counter: one segment per {@link #CD_SECONDS} s (a long wall still
 * costs time), the Wrecker must stand at the wall under the towers' fire, and a Sunforged Field Mason's Bulwark halves
 * the blow, so a braced segment survives it. Free.
 */
public class TearDown extends Ability {

    public static final int CD_SECONDS = 25;
    public static final float REACH = 4f;
    public static final float REFUND_SHARE = 0.5f;
    public static final String MSG_BAD_TARGET = "hud.reignofnether.tear_down_bad_target";
    public static final String MSG_TOO_FAR = "hud.reignofnether.engineer_too_far";

    public TearDown() {
        super(UnitAction.TEAR_DOWN, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, REACH, 0, false, true);
        this.autoRule(AutoRule.MANUAL);   // autocast: wants a chosen wall or trap
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Tear Down",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/netherite_axe.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.TEAR_DOWN,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.TEAR_DOWN),
            null,
            List.of(
                FormattedCharSequence.forward("Tear Down  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Rips down an enemy wall segment, gate or revealed trap within " + (int) REACH
                    + " blocks at once, and reclaims half its metal.", Style.EMPTY),
                FormattedCharSequence.forward("Towers and buildings are out of its reach.", Style.EMPTY),
                FormattedCharSequence.forward("Walls are just scrap that hasn't met us yet.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (tearDown(sl, self, unitUsing.getOwnerName(), BuildingUtils.findBuilding(false, targetBp), false)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** An instant cast in reach: the Wrecker keeps its place and its orders. */
    @Override
    public boolean shouldResetBehaviours() {
        return false;
    }

    /** A wall segment, gate or trap (the field fortifications a crowbar can take apart). */
    public static boolean isTearable(@Nullable Building b) {
        return b instanceof WallSegment || b instanceof WallGate || b instanceof Trap;
    }

    /**
     * Rips down {@code b} if it is an enemy fortification in reach; returns false when refused (HUD message unless
     * quiet). The metal comes back only if it actually came down. No cooldown handling here. Public for the bot and the
     * game test.
     */
    public static boolean tearDown(ServerLevel sl, LivingEntity wrecker, String owner, @Nullable BuildingPlacement b, boolean quiet) {
        if (owner == null || owner.isBlank())
            return false;
        // isAttackable: false for a trap its side still hides (and anything outside the world border)
        if (b == null || b.isDestroyedServerside || !isTearable(b.getBuilding()) || !b.isAttackable()
                || b.ownerName == null || b.ownerName.isBlank() || CombatEngineers.isFriendly(owner, b.ownerName)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return false;
        }
        if (CombatEngineers.distanceToBuilding(wrecker, b) > REACH) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_TOO_FAR);
            return false;
        }
        // health is normalised to 0 at the point of destruction and a hit of A takes A / minBlocksPercent off it, so
        // health x minBlocksPercent (+ a block's worth for rounding) is exactly one unbraced segment's worth - and a
        // Bulwark, which halves the blow, leaves a braced one standing (the readable Sunforged answer)
        double blow = Math.max(0, b.getHealth()) * b.minBlocksPercent + b.getHealthPerBlock() + 1;
        b.destroyRandomBlocks(blow, wrecker);
        sl.playSound(null, b.centrePos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.0f, 0.8f);
        sl.sendParticles(ParticleTypes.CRIT, b.centrePos.getX() + 0.5, b.centrePos.getY() + 1.0, b.centrePos.getZ() + 0.5,
            12, 0.6, 0.5, 0.6, 0.15);
        if (b.shouldBeDestroyed()) {
            float refund = b.getBuilding().cost == null ? 0 : b.getBuilding().cost.metal() * REFUND_SHARE;
            EconomyServerEvents.addReclaimedMetal(owner, refund);
        }
        return true;
    }
}
