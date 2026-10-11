package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.Blink;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Stormcallers Blade Dancer: <b>Blink Strike</b> (design/stormcallers_plan.md, slice 1). A teleport (no path, unlike the
 * Leafblade's dash) onto an enemy {@link #MIN_RANGE}-{@link #RANGE} blocks away: the Dancer lands a step short of it and
 * makes it its attack target. {@link #ENERGY_COST} energy through the Mana core, {@link #CD_SECONDS} s cooldown.
 * <ul>
 *   <li>Manual: aim at an enemy unit (or the ground, which does nothing).</li>
 *   <li>Autocast (always on, {@link #autocast}): a Dancer in a fight looks every half second for the nearest enemy
 *       <i>ranged</i> unit in reach and blinks onto it - the archer-diver BAR raiders are - paid quietly
 *       ({@link ManaServerEvents#tryCastQuiet}: no HUD spam) and only while its owner keeps {@link #AUTOCAST_RESERVE}
 *       energy over the cost, so a swarm of Dancers never eats the Thunder Sage's Heaven's Judgement.</li>
 * </ul>
 * Refused (no energy, no standable spot in sight beside the target): nothing paid, cooldown untouched.
 */
public class BlinkStrike extends Ability {

    public static final int CD_SECONDS = 10;
    public static final int RANGE = 9;
    public static final int MIN_RANGE = 3;
    public static final int ENERGY_COST = 25;
    /** Autocast keeps this much energy for the commander's D-gun (a Heaven's Judgement costs 150). */
    public static final float AUTOCAST_RESERVE = 150f;
    /** A unit is "ranged" (a Blink Strike autocast target) from this attack range on (tools/unit_stats.py's rule). */
    public static final float RANGED_FROM = 5f;

    public BlinkStrike() {
        super(UnitAction.BLINK_STRIKE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, true, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Blink Strike",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/amethyst_shard.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BLINK_STRIKE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BLINK_STRIKE),
            null,
            List.of(
                FormattedCharSequence.forward("Blink Strike  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)",
                    Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Teleport beside an enemy up to " + RANGE + " blocks away and attack it.",
                    Style.EMPTY),
                FormattedCharSequence.forward("In a fight it blinks onto enemy archers by itself.", Style.EMPTY),
                FormattedCharSequence.forward("The arrow arrives; the dancer is already gone.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || targetEntity == null)
            return;
        if (strike(sl, self, unitUsing.getOwnerName(), targetEntity, false))
            startCooldown(unitUsing, self);
    }

    void startCooldown(Unit unit, LivingEntity self) {
        this.setToMaxCooldown(unit);
        if (self.level().getEntity(self.getId()) == self)   // the packet looks the unit up; units outside the level skip it
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * Blinks {@code self} onto {@code target}: an enemy unit within {@link #RANGE} (flat). Finds the landing spot first,
     * then pays; returns false (nothing paid) when refused. Public for the game test (no cooldown handling here).
     */
    public static boolean strike(ServerLevel sl, LivingEntity self, String owner, LivingEntity target, boolean quiet) {
        if (owner == null || !target.isAlive() || !(target instanceof Unit tu) || !Blink.isEnemy(owner, tu))
            return false;
        double dx = target.getX() - self.getX(), dz = target.getZ() - self.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > RANGE + target.getBbWidth() / 2 || dist < 0.5)
            return false;
        // a step short of the target, on the Dancer's side of it
        double back = 1.0 + target.getBbWidth() / 2;
        Vec3 aim = new Vec3(target.getX() - dx / dist * back, target.getY(), target.getZ() - dz / dist * back);
        Vec3 spot = Blink.landingSpot(sl, self, aim, RANGE + 1);
        if (spot == null)
            return false;
        float power = quiet ? ManaServerEvents.tryCastQuiet(self, owner, ENERGY_COST) : ManaServerEvents.tryCast(self, owner, ENERGY_COST);
        if (power <= 0)
            return false;
        if (self instanceof Unit u)
            Unit.fullResetBehaviours(u);   // the blink is an order: drop the old path and target first
        Blink.blinkTo(sl, self, spot);
        self.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        if (self instanceof AttackerUnit au)
            au.setUnitAttackTarget(target);
        return true;
    }

    /**
     * The autocast, called by the Blade Dancer every half second while it has a target: the nearest enemy ranged unit
     * {@link #MIN_RANGE}-{@link #RANGE} blocks away (not a flyer - the Dancer can't follow it up), if the owner can pay
     * and keep {@link #AUTOCAST_RESERVE}. One grid query; returns whether it blinked. Public for the game test.
     */
    public boolean autocast(ServerLevel sl, LivingEntity self, Unit unit, List<LivingEntity> scratch) {
        if (!isOffCooldown(unit))
            return false;
        String owner = unit.getOwnerName();
        if (!canAffordWithReserve(owner))
            return false;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), RANGE + 1, scratch)) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || !(le instanceof AttackerUnit au) || !Blink.isEnemy(owner, u))
                continue;
            if (u.isFlyingUnit() || au.getAttackRange() < RANGED_FROM)
                continue;
            double dx = le.getX() - self.getX(), dz = le.getZ() - self.getZ(), d = dx * dx + dz * dz;
            if (d < MIN_RANGE * MIN_RANGE || d > RANGE * RANGE || Math.abs(le.getY() - self.getY()) > 4 || d >= bestD)
                continue;
            best = le;
            bestD = d;
        }
        if (best == null || !strike(sl, self, owner, best, true))
            return false;
        startCooldown(unit, self);
        return true;
    }

    /** The cost (x2 overcharged) plus the reserve is in the pool; owners without a pool never autocast (tryCast refuses them). */
    static boolean canAffordWithReserve(String owner) {
        Resources res = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                res = r;
        return res != null && res.getEnergy() >= ENERGY_COST * ManaServerEvents.spellCostMultiplier(owner) + AUTOCAST_RESERVE;
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }
}
