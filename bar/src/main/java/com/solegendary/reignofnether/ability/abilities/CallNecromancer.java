package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.GraveAdeptServerEvents;
import com.solegendary.reignofnether.essence.TombcallerServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.GraveAdeptUnit;

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
 * Tombcaller: <b>Call the Necromancer</b> (design/essence_casters.md "Gravebound Gravecaller"; lovish: "they can summon
 * a necromancer that can use exp to grow in strength"). A {@link GraveAdeptUnit} rises beside the Tombcaller for its
 * owner and stays until killed; one per Tombcaller (counting one whose chunk unloaded; not one a Wraith possessed).
 * {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s. Refused - nothing spent, no cooldown - while its Adept stands or
 * short of essence; with no room beside it, it rises where the Tombcaller stands. Autocast while fighting with none
 * standing.
 */
public class CallNecromancer extends Ability {

    public static final int ESSENCE_COST = 20;
    public static final int CD_SECONDS = 60;
    public static final String MSG_ALIVE = "hud.essence.reignofnether.adept_alive";

    public CallNecromancer() {
        super(UnitAction.CALL_NECROMANCER, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
        this.autoRule(AutoRule.SUMMON_WHEN_FREE);
        this.autocastEssence = ESSENCE_COST;   // the AutoCaster's essence gate (essence base Revision 1)
        this.showRangeCircle = false;
    }

    @Override
    public int liveSummons(ServerLevel level, Unit unit) {
        return unit instanceof LivingEntity le && GraveAdeptServerEvents.hasAdept(le) ? 1 : 0;
    }

    @Override
    public int summonCap() {
        return 1;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Call the Necromancer",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/wither_skeleton_skull.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.CALL_NECROMANCER),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Call the Necromancer  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A Grave Adept rises and stays until killed (one per Tombcaller). Enemy deaths within "
                    + GraveAdeptUnit.XP_RANGE + " blocks feed it XP: " + GraveAdeptUnit.MAX_LEVEL + " levels of health, damage and raised skeletons.", Style.EMPTY),
                FormattedCharSequence.forward("Kill it and its XP flies to your heroes.", Style.EMPTY),
                FormattedCharSequence.forward("It remembers every death it saw.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        useNow(level, unitUsing);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        useNow(level, unitUsing);
    }

    private void useNow(Level level, Unit unitUsing) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || !isOffCooldown(unitUsing))
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * The rule: checks the caster has no Adept standing, then spends the essence, then raises it - a
     * refusal costs nothing. Returns the Adept (null: refused). Public for the bot and the game test.
     */
    @Nullable
    public static GraveAdeptUnit cast(ServerLevel sl, LivingEntity caster, String owner, boolean quiet) {
        if (owner == null || owner.isEmpty())
            return null;
        if (GraveAdeptServerEvents.hasAdept(caster)) {
            if (!quiet)
                TombcallerServerEvents.warn(sl, owner, MSG_ALIVE);
            return null;
        }
        EntityType<GraveAdeptUnit> type = EntityRegistrar.GRAVE_ADEPT_UNIT.get();
        // beside the caster, to its right, where it fits; else where the caster stands (always room there)
        double ang = Math.toRadians(caster.getYRot());
        Vec3 at = new Vec3(caster.getX() - Math.cos(ang) * 1.5, caster.getY(), caster.getZ() - Math.sin(ang) * 1.5);
        BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
        if (!sl.noCollision(type.getDimensions().makeBoundingBox(at)) || sl.getBlockState(below).getCollisionShape(sl, below).isEmpty())
            at = caster.position();
        GraveAdeptUnit adept = type.create(sl);
        if (adept == null)
            return null;
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            adept.discard();
            if (!quiet)
                TombcallerServerEvents.warn(sl, owner, Empower.MSG_SHORT);
            return null;
        }
        long now = sl.getGameTime();
        adept.moveTo(at.x, at.y, at.z, caster.getYRot(), 0);
        adept.setOwnerName(owner);
        Essence.markSummoned(adept);   // before it joins: no essence, no orbs, no veterancy for its own death
        adept.setCaster(caster);
        adept.setNextRaise(now + GraveAdeptUnit.RAISE_EVERY_SECONDS * 20L);
        adept.setNextBolt(now);
        sl.addFreshEntity(adept);
        sl.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.8, at.z, 24, 0.4, 0.6, 0.4, 0.03);
        SoundClientboundPacket.playSoundAtPos(SoundAction.WRAITH_POSSESS_FULL, adept.blockPosition());
        return adept;
    }
}
