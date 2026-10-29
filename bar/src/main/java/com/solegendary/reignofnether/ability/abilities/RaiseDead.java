package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gravebound Lich Regent: <b>Raise Dead</b> (design-factions.md, "every wreck is an army"). Up to {@link #MAX_RAISED}
 * wrecks within {@link #RADIUS} blocks rise as Ghouls (zombie units) under the commander's owner. Never free: each
 * Ghoul costs its normal metal, paid from the wreck first and the pool for any remainder. A Ghoul is a summon
 * (unit/Summons): it takes no population (lovish, Oct 29); at most {@link #MAX_GHOULS} stand per player. Wreck metal
 * beyond a Ghoul's cost stays on the wreck.
 * 30 s cooldown, instant cast.
 */
public class RaiseDead extends Ability {

    public static final int CD_SECONDS = 30;
    public static final double RADIUS = 10;
    public static final int MAX_RAISED = 3;
    /**
     * At most this many ghouls alive per player (lovish, Oct 29). A ghoul is a summon and takes no population, so this
     * is its only limit (it has no lifetime); a cast at the cap raises nothing, pays nothing and sets no cooldown.
     */
    public static final int MAX_GHOULS = 8;
    /** Marks a ghoul (saved with it), so the cap counts the ones that came back from a save too. */
    public static final String GHOUL_TAG = "bt_ghoul";

    /** Ghouls alive for {@code owner} (server side). Public for the game test. */
    public static int liveGhouls(String owner) {
        int n = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le.isAlive() && le.getTags().contains(GHOUL_TAG) && le instanceof Unit u && owner.equals(u.getOwnerName()))
                n++;
        return n;
    }

    public RaiseDead() {
        super(UnitAction.COMMANDER_RAISE_DEAD, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
        this.autoRule(AutoRule.MANUAL);   // autocast: a commander power that spends metal
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Raise Dead",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/rotten_flesh.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.COMMANDER_RAISE_DEAD),
            null,
            List.of(
                FormattedCharSequence.forward("Raise Dead  (commander, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Up to " + MAX_RAISED + " wrecks within " + (int) RADIUS
                    + " blocks rise as Ghouls. Each costs its metal - wreck first, then your pool.", Style.EMPTY),
                FormattedCharSequence.forward("Every wreck is an army.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        String owner = unitUsing.getOwnerName();
        int raised = raise(sl, self, owner);
        if (raised > 0) {
            sl.playSound(null, self.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.HOSTILE, 1.5f, 0.6f);
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Raises what it can (never past {@link #MAX_GHOULS} alive) and returns how many rose. Public for the game test. */
    public static int raise(ServerLevel level, LivingEntity self, String owner) {
        int room = Math.min(MAX_RAISED, MAX_GHOULS - liveGhouls(owner));
        if (room <= 0)
            return 0;   // at the cap: nothing paid, nothing drained (the bot casts on cooldown: this is its limit too)
        float cost = ResourceCosts.ZOMBIE.metal();
        Resources pool = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        List<Entity> near = new ArrayList<>();
        for (Entity w : WreckServerEvents.getWrecks())
            if (!w.isRemoved() && w.distanceToSqr(self) <= RADIUS * RADIUS)
                near.add(w);
        near.sort(Comparator.comparingDouble(w -> w.distanceToSqr(self)));
        int raised = 0;
        for (Entity w : near) {
            if (raised >= room)
                break;
            float fromWreck = Math.min(WreckServerEvents.metalOf(w), cost);
            float fromPool = cost - fromWreck;
            if (fromPool > 0 && (pool == null || pool.getMetal() < fromPool))
                continue;
            if (fromPool > 0)
                pool.addMetal(-fromPool);
            WreckServerEvents.drain(level, w, fromWreck);
            Entity ghoul = UnitServerEvents.spawnMob(EntityRegistrar.ZOMBIE_UNIT.get(), level, w.blockPosition().below(), owner);   // spawnMob adds one block
            if (ghoul != null) {
                com.solegendary.reignofnether.unit.Summons.mark(ghoul);   // a summon: no population (lovish, Oct 29) and no loop pay-outs
                ghoul.addTag(GHOUL_TAG);   // counted for the 8-per-player cap
                raised++;
                level.sendParticles(ParticleTypes.SOUL, w.getX(), w.getY() + 0.5, w.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
            }
        }
        return raised;
    }
}
