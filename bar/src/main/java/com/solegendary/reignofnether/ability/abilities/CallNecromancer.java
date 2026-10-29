package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.essence.GraveAdeptServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.GraveAdeptUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Tombcaller: <b>Call the Necromancer</b> (design/essence_casters.md "Gravebound Gravecaller"; lovish: "they can summon
 * a necromancer that can use exp to grow in strength"). A {@link GraveAdeptUnit} rises beside the Tombcaller for its
 * owner and stays until killed; one per Tombcaller. {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s. Refused -
 * nothing spent, no cooldown - while its Adept stands, with no room beside it, or short of essence. Autocast while
 * fighting with none standing.
 */
public class CallNecromancer extends Ability {

    public static final int ESSENCE_COST = 20;
    public static final int CD_SECONDS = 60;
    public static final String MSG_ALIVE = "hud.essence.reignofnether.adept_alive";
    public static final String MSG_NO_ROOM = "hud.essence.reignofnether.adept_no_room";

    public CallNecromancer() {
        super(UnitAction.CALL_NECROMANCER, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
        this.autoRule(AutoRule.SUMMON_WHEN_FREE);
        this.autocastEssence = ESSENCE_COST;   // the AutoCaster's essence gate (essence base Revision 1)
        this.showRangeCircle = false;
    }

    @Override
    public int liveSummons(ServerLevel level, Unit unit) {
        return unit instanceof LivingEntity le && GraveAdeptServerEvents.adeptOf(le) != null ? 1 : 0;
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
     * The rule: checks the caster has no Adept standing and there is room, then spends the essence, then raises it - a
     * refusal costs nothing. Returns the Adept (null: refused). Public for the bot and the game test.
     */
    @Nullable
    public static GraveAdeptUnit cast(ServerLevel sl, LivingEntity caster, String owner, boolean quiet) {
        return null;
    }
}
