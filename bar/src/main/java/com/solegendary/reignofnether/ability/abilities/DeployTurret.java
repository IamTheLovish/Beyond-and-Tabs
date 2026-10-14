package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Brassworks commander (the Guildmaster): <b>Deploy Turret</b> (design/brassworks_plan.md section 3.2) - its third
 * button next to Overclock and the Rail Lance (the Ward precedent: CommanderAbility holds one Kind, extra powers are
 * their own abilities, granted by CommanderServerEvents.ensureAbility). Aimed within {@link #RANGE}: a
 * {@link DeployedTurretUnit} (a rifle on a tripod) stands there for {@link DeployedTurretUnit#LIFETIME_TICKS} and
 * fights like a Rifle Squad that can't walk, then falls apart. {@link #METAL_COST} metal, {@link #CD_SECONDS} s.
 */
public class DeployTurret extends Ability {

    public static final int CD_SECONDS = 30;
    public static final int RANGE = 8;
    public static final int METAL_COST = 40;

    public DeployTurret() {
        super(UnitAction.DEPLOY_TURRET, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        this.autoRule(AutoRule.MANUAL);   // autocast: a commander power that needs a chosen spot
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Deploy Turret",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/crossbow_standby.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.DEPLOY_TURRET,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.DEPLOY_TURRET),
            null,
            List.of(
                FormattedCharSequence.forward("Deploy Turret  (commander, " + METAL_COST + " metal, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Sets an auto-turret within " + RANGE + " blocks for " + DeployedTurretUnit.LIFETIME_TICKS / 20
                    + " s: a rifle on a tripod that fires at whatever comes, then falls apart.", Style.EMPTY),
                FormattedCharSequence.forward("A machine: it heats and vents like the rest.", Style.EMPTY),
                FormattedCharSequence.forward("Every Guildmaster carries a spare gun.", Style.EMPTY.withItalic(true))
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
        if (deploy(sl, self, unitUsing.getOwnerName(), targetBp, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Pays and plants the turret at the aim (pulled in to {@link #RANGE}). Returns the turret, or null when refused
     * (nothing paid). Public for the bot and the game test (no cooldown handling here).
     */
    public static DeployedTurretUnit deploy(ServerLevel sl, LivingEntity self, String owner, BlockPos aim, boolean quiet) {
        if (aim == null || owner == null)
            return null;
        Vec3 at = Vec3.atBottomCenterOf(aim);
        Vec3 d = at.subtract(self.position());
        if (d.horizontalDistanceSqr() > RANGE * RANGE)
            at = self.position().add(new Vec3(d.x, 0, d.z).normalize().scale(RANGE));
        Resources pool = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null || pool.getMetal() < METAL_COST) {
            if (!quiet)
                ResourcesClientboundPacket.warnInsufficientResources(owner, true, true, false, true);
            return null;
        }
        Entity e = UnitServerEvents.spawnMob(EntityRegistrar.DEPLOYED_TURRET_UNIT.get(), sl,
            BlockPos.containing(at.x, at.y, at.z).below(), owner);
        if (!(e instanceof DeployedTurretUnit turret))
            return null;
        pool.addMetal(-METAL_COST);
        turret.setYRot(self.getYRot());
        sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 10, 0.4, 0.3, 0.4, 0.05);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 1.2f, 1.2f);
        return turret;
    }
}
