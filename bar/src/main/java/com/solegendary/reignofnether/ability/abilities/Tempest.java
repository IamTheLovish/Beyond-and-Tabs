package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.mana.TempestServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Stormcallers Celestial Dragon: <b>Tempest</b> (design/stormcallers_plan.md, slice 4). Aim at a spot within
 * {@link #RANGE} blocks: a storm of radius {@link TempestServerEvents#RADIUS} gathers over the dragon and rolls toward
 * the spot for {@link TempestServerEvents#DURATION_TICKS} ticks, striking the most clumped enemy inside every
 * {@link TempestServerEvents#STRIKE_EVERY} ticks for {@link TempestServerEvents#DAMAGE} x power. The cost is the weakness:
 * {@link TempestServerEvents#ENERGY_UPFRONT} up front and {@link TempestServerEvents#ENERGY_PER_SECOND} a second while it
 * rolls, at spell priority (it stalls its own side's construction); starved, it fizzles. {@link #CD_SECONDS} s cooldown,
 * started only when the storm rises. A dragon grounded by wind cannot cast.
 */
public class Tempest extends Ability {

    public static final int CD_SECONDS = 45;
    public static final int RANGE = 18;

    public Tempest() {
        super(UnitAction.TEMPEST, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, TempestServerEvents.RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);   // autocast: a roaming storm: saved for a clump, paid up front
        this.autocastEnergy = TempestServerEvents.ENERGY_UPFRONT;
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Tempest",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/nether_star.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.TEMPEST,
            () -> false,
            () -> !(unit instanceof CelestialDragonUnit d && d.isGrounded()),   // greyed while grounded by wind
            () -> CursorClientEvents.setLeftClickAction(UnitAction.TEMPEST),
            null,
            List.of(
                FormattedCharSequence.forward("Tempest  (" + (int) TempestServerEvents.ENERGY_UPFRONT + " energy + "
                    + (int) TempestServerEvents.ENERGY_PER_SECOND + "/s, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A storm " + (int) TempestServerEvents.RADIUS + " blocks wide rolls from the dragon toward a spot up to "
                    + RANGE + " blocks away for " + TempestServerEvents.DURATION_TICKS / 20 + " s, striking the most clumped enemy inside every "
                    + TempestServerEvents.STRIKE_EVERY / 20f + " s for " + (int) TempestServerEvents.DAMAGE + ".", Style.EMPTY),
                FormattedCharSequence.forward("Drains energy every second at spell priority - it can stall your own construction. Starved, it fizzles.", Style.EMPTY),
                FormattedCharSequence.forward("Friends are never struck. A dragon grounded by wind cannot call it.", Style.EMPTY.withItalic(true))
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
        if (cast(sl, self, unitUsing.getOwnerName(), targetBp, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Raises the storm rolling toward {@code aim} (pulled in to {@link #RANGE}). Returns null when refused (nothing
     * paid: not enough energy, or a grounded dragon); the owner sees the usual warning unless {@code quiet}. Public for
     * the bot and the game test (no cooldown here).
     */
    public static TempestServerEvents.Storm cast(ServerLevel sl, LivingEntity self, String owner, BlockPos aim, boolean quiet) {
        if (owner == null || aim == null)
            return null;
        if (self instanceof CelestialDragonUnit d && d.isGrounded())
            return null;   // grounded by wind: no cast (the button is greyed too)
        double dx = aim.getX() + 0.5 - self.getX(), dz = aim.getZ() + 0.5 - self.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > RANGE + 1) {   // too far: the storm rolls to the end of the dragon's reach along the line
            dx *= RANGE / len;
            dz *= RANGE / len;
        }
        double aimX = self.getX() + dx, aimZ = self.getZ() + dz;
        // the storm rolls over the ground the aim is on (the floor by the aimed block, not the heightmap)
        int groundY = com.solegendary.reignofnether.mana.Blink.groundY(sl, aim.getX(), aim.getZ(), aim.getY() + 1);
        TempestServerEvents.Storm s = TempestServerEvents.raise(sl, self, owner, aimX, aimZ, groundY, quiet);
        if (s == null && !quiet && ManaServerEvents.lastRefusal() == ManaServerEvents.Refusal.NOT_ENOUGH_ENERGY)
            ResourcesClientboundPacket.warnInsufficientResources(owner, true, false, true, true);
        return s;
    }
}
