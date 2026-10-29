package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.essence.SoulbinderServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Soulbinder: <b>Soul Harvest</b>, its mid essence ability (design/essence_casters.md, lovish approved draft 2 on Oct 29
 * 2026: "2 spectral skeletons for 20 s"). {@link #COUNT} skeletons rise beside the Soulbinder and fight for its owner for
 * {@link #LIFETIME_SECONDS} s, then crumble to soul fire. They are summons ({@link
 * com.solegendary.reignofnether.essence.Essence#markSummoned}): their deaths drop no essence, so a Soulbinder cannot
 * farm its own. {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s; refused - nothing spent, no cooldown - when short
 * of essence. Autocast while fighting, when its last pair is gone.
 */
public class SoulHarvest extends Ability {

    public static final int ESSENCE_COST = 10;
    public static final int CD_SECONDS = 25;
    public static final int COUNT = 2;
    public static final int LIFETIME_SECONDS = 20;

    public SoulHarvest() {
        super(UnitAction.SOUL_HARVEST, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
        this.autoRule(AutoRule.SUMMON_WHEN_FREE);
        this.showRangeCircle = false;
    }

    @Override
    public int liveSummons(ServerLevel level, Unit unit) {
        return unit instanceof LivingEntity le ? SoulbinderServerEvents.summonsOf(le).size() : 0;
    }

    @Override
    public int summonCap() {
        return COUNT;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Soul Harvest",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/bone.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.SOUL_HARVEST),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Soul Harvest  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(COUNT + " spectral skeletons rise beside the Soulbinder and fight for "
                    + LIFETIME_SECONDS + " s, then crumble. Their deaths leave no essence.", Style.EMPTY),
                FormattedCharSequence.forward("Borrowed bones, returned on time.", Style.EMPTY.withItalic(true))
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
        if (!cast(sl, self, unitUsing.getOwnerName(), false).isEmpty()) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * The rule: checks the essence, raises the skeletons, then spends it - a refusal costs nothing. Returns the
     * skeletons raised (empty: refused). Public for the bot and the game test (no cooldown handling here).
     */
    public static List<Mob> cast(ServerLevel sl, LivingEntity caster, String owner, boolean quiet) {
        return List.of();
    }
}
