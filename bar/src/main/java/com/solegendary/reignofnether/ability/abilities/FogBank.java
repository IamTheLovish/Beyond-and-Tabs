package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.tide.FogBankServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Tidewrought commander (the Admiral): <b>Fog Bank</b> (design/tidewrought_plan.md, slice 2). Aim a spot within
 * {@link #RANGE} blocks: a sea-fog cloud of radius {@link FogBankServerEvents#RADIUS} rolls in for
 * {@link FogBankServerEvents#LIFETIME_TICKS} ticks, and the owner's and allies' units inside it are hidden from enemies
 * further than ThicketCover.REVEAL_RADIUS away - the Verdant thicket's cover rules (attacking gives a unit away, a Holy
 * Bell rings it out). One bank per Admiral: recasting moves it. {@link #CD_SECONDS} s cooldown, no resource cost.
 * Granted to Tidewrought commanders by CommanderServerEvents.ensureAbility, alongside Riptide and Broadside.
 */
public class FogBank extends Ability {

    public static final int CD_SECONDS = 60;
    public static final int RANGE = 16;

    public FogBank() {
        super(UnitAction.FOG_BANK, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, FogBankServerEvents.RADIUS, false, true);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Fog Bank",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/white_wool.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.FOG_BANK,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.FOG_BANK),
            null,
            List.of(
                FormattedCharSequence.forward("Fog Bank  (commander, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Sea fog rolls in for " + FogBankServerEvents.LIFETIME_TICKS / 20 + " s, "
                    + FogBankServerEvents.RADIUS + " blocks round a spot up to " + RANGE + " away.", Style.EMPTY),
                FormattedCharSequence.forward("Friendly units inside are hidden unless an enemy is right beside them or they attack.", Style.EMPTY),
                FormattedCharSequence.forward("The fleet was never there.", Style.EMPTY.withItalic(true))
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
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        BlockPos at = MyMath.getXZRangeLimitedBlockPos(self.blockPosition(), targetBp, RANGE);
        at = new BlockPos(at.getX(), targetBp.getY(), at.getZ());
        if (cast(sl, unitUsing, at) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * Raises the bank over {@code ground} (the clicked ground block) for the Admiral's owner. Returns it, or null for an
     * ownerless caster. Public for the bot and the game test (no cooldown handling here).
     */
    public static FogBankServerEvents.Bank cast(ServerLevel sl, Unit admiral, BlockPos ground) {
        String owner = admiral.getOwnerName();
        if (owner == null || owner.isBlank() || !(admiral instanceof LivingEntity self))
            return null;
        FogBankServerEvents.Bank b = FogBankServerEvents.raise(sl, owner, self.getId(),
                ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5);
        sl.playSound(null, ground.above(), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.NEUTRAL, 3f, 0.6f);
        sl.playSound(null, self.blockPosition(), SoundEvents.CONDUIT_AMBIENT, SoundSource.NEUTRAL, 2f, 0.7f);
        // cover is decided on the cover pass (every 5 ticks); run one now so the fleet vanishes as the fog rolls in
        com.solegendary.reignofnether.blocks.ThicketCover.refresh(sl.getServer());
        return b;
    }
}
