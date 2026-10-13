package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.DecoyServerEvents;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Stormcallers Mirror Mage: <b>Mirror Images</b> (design/stormcallers_plan.md, slice 3). Aim at a friendly ground unit
 * within {@link #RANGE} blocks: {@link #COPIES} decoys of the same type appear round it for
 * {@link DecoyServerEvents#LIFETIME_TICKS} ticks. Decoys draw fire like the original (enemies cannot tell them apart),
 * deal no damage and shatter at the first hit (DecoyServerEvents); they are never population and leave no wreck.
 * Never copies a commander, a flyer, an experimental ({@link #MAX_POPULATION} population or more) or another decoy;
 * at most {@link DecoyServerEvents#MAX_PER_OWNER} decoys per player at once. {@link #ENERGY_COST} energy through the
 * Mana core, {@link #CD_SECONDS} s cooldown; refused = nothing paid, no cooldown.
 */
public class MirrorImages extends Ability {

    public static final int CD_SECONDS = 30;
    public static final int RANGE = 10;
    public static final int ENERGY_COST = 80;
    public static final int COPIES = 3;
    /** Anything this costly in population (T3s, the War Mammoth) is never mirrored. */
    public static final int MAX_POPULATION = 8;
    public static final String MSG_BAD_TARGET = "hud.mana.reignofnether.mirror_bad_target";
    public static final String MSG_CAP = "hud.mana.reignofnether.mirror_cap";

    public MirrorImages() {
        super(UnitAction.MIRROR_IMAGES, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, true);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Mirror Images",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/amethyst_shard.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.MIRROR_IMAGES,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.MIRROR_IMAGES),
            null,
            List.of(
                FormattedCharSequence.forward("Mirror Images  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(COPIES + " decoys of a friendly ground unit within " + RANGE + " blocks for "
                    + DecoyServerEvents.LIFETIME_TICKS / 20 + " s: they draw fire, deal no damage and shatter at a touch.", Style.EMPTY),
                FormattedCharSequence.forward("Never a commander, a flyer or an experimental. At most " + DecoyServerEvents.MAX_PER_OWNER + " decoys at once.", Style.EMPTY),
                FormattedCharSequence.forward("Which of us did you mean to strike?", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetEntity == null)
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetEntity, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Ground clicks do nothing: a mirror needs a unit to reflect. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    /** Can this unit be mirrored by {@code owner}: a friendly, living, grounded, non-commander, non-T3, non-decoy unit. */
    public static boolean canMirror(String owner, LivingEntity target) {
        if (owner == null || target == null || !target.isAlive() || !(target instanceof Unit u) || !(target instanceof Mob))
            return false;
        if (!AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner))
            return false;
        if (CommanderServerEvents.isCommander(target) || u.isFlyingUnit() || DecoyServerEvents.isDecoy(target))
            return false;
        var cost = u.getCost();
        return cost == null || cost.population < MAX_POPULATION;
    }

    /**
     * Conjures the decoys round {@code target}: checks it first (mirrorable, in reach, under the cap), then pays, then
     * spawns. Returns the decoys, or null when refused (nothing paid). Public for the bot and the game test (no
     * cooldown handling here).
     */
    @SuppressWarnings("unchecked")
    public static List<LivingEntity> cast(ServerLevel sl, LivingEntity mage, String owner, LivingEntity target, boolean quiet) {
        if (!canMirror(owner, target)) {
            if (!quiet && owner != null)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return null;
        }
        double reach = RANGE + target.getBbWidth() / 2 + 0.5;
        if (mage.distanceToSqr(target) > reach * reach)
            return null;
        if (DecoyServerEvents.countOf(owner) + COPIES > DecoyServerEvents.MAX_PER_OWNER) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_CAP);
            return null;
        }
        float power = quiet ? ManaServerEvents.tryCastQuiet(mage, owner, ENERGY_COST) : ManaServerEvents.tryCast(mage, owner, ENERGY_COST);
        if (power <= 0)
            return null;
        List<LivingEntity> out = new ArrayList<>(COPIES);
        EntityType<? extends Mob> type = (EntityType<? extends Mob>) target.getType();
        for (int i = 0; i < COPIES; i++) {
            double a = i * (2 * Math.PI / COPIES) + target.getYRot() * Math.PI / 180;
            BlockPos at = BlockPos.containing(target.getX() + Math.cos(a) * 1.5, target.getY(), target.getZ() + Math.sin(a) * 1.5).below();
            Entity e = UnitServerEvents.spawnMob(type, sl, at, ((Unit) target).getOwnerName());
            if (!(e instanceof LivingEntity decoy))
                continue;
            decoy.setYRot(target.getYRot());
            decoy.setYHeadRot(target.getYHeadRot());
            DecoyServerEvents.register(sl, decoy);
            // the copy picks up the original's fight, so it draws fire at once
            if (decoy instanceof Mob m && target instanceof Mob t && t.getTarget() != null)
                m.setTarget(t.getTarget());
            out.add(decoy);
        }
        sl.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1, target.getZ(), 20, 1.2, 0.6, 1.2, 0.05);
        sl.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5f, 0.8f);
        com.solegendary.reignofnether.barfx.BarFx.shot(sl, mage.getEyePosition(), target.getEyePosition(),
            com.solegendary.reignofnether.barfx.BarFx.K_MAGIC, mage);
        return out;
    }
}
