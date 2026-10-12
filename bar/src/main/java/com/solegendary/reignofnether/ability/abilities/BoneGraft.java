package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.Blocks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Gravebound Bonesetter (combat engineer, design/combat_engineers.md): <b>Bone Graft</b>. Aim at a friendly unit within
 * {@link #RANGE} (or at the ground: the most damaged friend within {@link #RANGE} of the Bonesetter): the nearest wreck
 * within {@link #RANGE} of the Bonesetter is torn apart to mend it, {@link #HP_PER_METAL} HP for each metal, at most
 * {@link #MAX_METAL} metal a cast and never more than the unit is missing - what is not used stays in the wreck.
 * Refused, with no cooldown, without a wreck or a damaged friend in reach.
 * <p>
 * "Every wreck is an army" (the Gravebound signature) - and here also a splint. Not more reclaim speed (the Embalmer's
 * Soul Wisps have that) and not growth (the Ossuary Behemoth's Grave Feast): metal turned into health, now. Tempo:
 * saves a key unit mid-fight. Counter: no wreck, no graft - reclaim or hook the field first, wrecks decay; one burst on
 * one unit (focus another); and the metal is gone from both sides' economy. Free; {@link #CD_SECONDS} s cooldown.
 */
public class BoneGraft extends Ability {

    public static final int CD_SECONDS = 20;
    public static final float RANGE = 8f;
    public static final float HP_PER_METAL = 2f;
    public static final float MAX_METAL = 50f;
    public static final String MSG_NO_WRECK = "hud.reignofnether.bone_graft_no_wreck";
    public static final String MSG_NO_TARGET = "hud.reignofnether.bone_graft_no_target";

    public BoneGraft() {
        super(UnitAction.BONE_GRAFT, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Bone Graft",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/bone.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BONE_GRAFT,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BONE_GRAFT),
            null,
            List.of(
                FormattedCharSequence.forward("Bone Graft  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Tears a wreck within " + (int) RANGE + " blocks apart to mend a friendly unit: "
                    + (int) HP_PER_METAL + " HP per metal, up to " + (int) MAX_METAL + " metal a cast.", Style.EMPTY),
                FormattedCharSequence.forward("Aim at a unit, or at the ground for the most hurt friend nearby.", Style.EMPTY),
                FormattedCharSequence.forward("The dead lend a bone.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (graft(sl, self, unitUsing.getOwnerName(), targetEntity, false) > 0)
            startCooldown(unitUsing, self);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (graft(sl, self, unitUsing.getOwnerName(), null, false) > 0)
            startCooldown(unitUsing, self);
    }

    /** An instant cast: the Bonesetter keeps doing what it was. */
    @Override
    public boolean shouldResetBehaviours() {
        return false;
    }

    private void startCooldown(Unit unitUsing, LivingEntity self) {
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * Mends {@code target} (or, if null or not a valid friend, the most damaged friend in range) from the nearest wreck
     * in range. Returns the HP mended; 0 when refused (HUD message unless quiet). No cooldown handling here. Public for
     * the bot and the game test.
     */
    public static float graft(ServerLevel sl, LivingEntity setter, String owner, @Nullable LivingEntity target, boolean quiet) {
        if (owner == null || owner.isBlank())
            return 0;
        // an aimed graft may be the Bonesetter itself (a choice); the area pick never is (mostDamagedFriend skips it)
        LivingEntity patient = validPatient(setter, owner, target) ? target
            : CombatEngineers.mostDamagedFriend(sl, setter, owner, RANGE);
        if (patient == null) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_TARGET);
            return 0;
        }
        Entity wreck = nearestWreck(setter, RANGE);
        if (wreck == null) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_WRECK);
            return 0;
        }
        float missing = patient.getMaxHealth() - patient.getHealth();
        float metal = Math.min(Math.min(MAX_METAL, WreckServerEvents.metalOf(wreck)), missing / HP_PER_METAL);
        if (metal <= 0)
            return 0;
        float hp = metal * HP_PER_METAL;
        patient.heal(hp);
        WreckServerEvents.drain(sl, wreck, metal);
        // bone dust streaming from the heap to the patient
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState());
        double dx = patient.getX() - wreck.getX(), dy = patient.getY() + 1 - wreck.getY(), dz = patient.getZ() - wreck.getZ();
        for (int i = 0; i <= 6; i++) {
            double t = i / 6.0;
            sl.sendParticles(dust, wreck.getX() + dx * t, wreck.getY() + 0.3 + dy * t, wreck.getZ() + dz * t, 2, 0.1, 0.1, 0.1, 0.02);
        }
        sl.sendParticles(ParticleTypes.SCULK_SOUL, patient.getX(), patient.getY() + patient.getBbHeight() * 0.6, patient.getZ(),
            4, 0.25, 0.3, 0.25, 0.02);
        sl.playSound(null, patient.blockPosition(), SoundEvents.SKELETON_STEP, SoundSource.NEUTRAL, 1.2f, 0.6f);
        return hp;
    }

    /** A living friendly unit within reach that is missing health. */
    static boolean validPatient(LivingEntity setter, String owner, @Nullable LivingEntity le) {
        if (le == null || !le.isAlive() || !(le instanceof Unit u) || !CombatEngineers.isFriendly(owner, u.getOwnerName()))
            return false;
        double reach = RANGE + le.getBbWidth() / 2 + 0.5;
        return setter.distanceToSqr(le) <= reach * reach && le.getHealth() < le.getMaxHealth();
    }

    /** The nearest wreck with metal left within {@code range} of {@code e}, or null. Public for the bot. */
    @Nullable
    public static Entity nearestWreck(LivingEntity e, double range) {
        Entity best = null;
        double bestD = range * range;
        List<Entity> wrecks = WreckServerEvents.getWrecks();
        for (int i = 0, n = wrecks.size(); i < n; i++) {
            Entity w = wrecks.get(i);
            if (w.isRemoved() || w.level() != e.level() || WreckServerEvents.metalOf(w) <= 0)
                continue;
            double d = w.distanceToSqr(e);
            if (d <= bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }
}
