package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.LeyNetwork;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
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
 * Stormcallers Geomancer: the <b>Ley Link</b> order (design/stormcallers_plan.md 3.4). Two clicks: an own completed
 * building, then a second one within {@link LeyNetwork#MAX_DISTANCE} blocks of it - the two are ley-linked (a jade beam
 * joins them). Ordering a linked pair again unlinks it. Wind generators in one network resonate: each earns +10% energy
 * per other generator in it, up to +30% (LeyNetwork). Free; {@link #CD_SECONDS} s cooldown after each link.
 * <p>
 * The first click is remembered per Geomancer on the server ({@link #PENDING}, forgotten after
 * {@link #PENDING_TICKS}); on the client the order re-arms the cursor after the first click (ManaClientEvents reads
 * {@link #rearmCursor} on the next client tick), so the second building is one click away.
 */
public class LeyLink extends Ability {

    public static final int CD_SECONDS = 2;
    public static final int PENDING_TICKS = 30 * 20;
    public static final String MSG_FIRST = "hud.mana.reignofnether.ley_link_first";
    public static final String MSG_LINKED = "hud.mana.reignofnether.ley_linked";
    public static final String MSG_UNLINKED = "hud.mana.reignofnether.ley_unlinked";

    private record Pending(BlockPos centre, long since) { }

    /** Geomancer entity id -> its first clicked building (server thread only). */
    private static final Int2ObjectOpenHashMap<Pending> PENDING = new Int2ObjectOpenHashMap<>();

    /** Client: the next client tick re-arms the Ley Link cursor (the click that set this cleared it). */
    public static boolean rearmCursor = false;
    private static BlockPos clientFirst = null;

    public LeyLink() {
        super(UnitAction.LEY_LINK, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, true);
        this.autoRule(AutoRule.MANUAL);   // autocast: a two-click building order
        this.showRangeCircle = false;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Ley Link",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/amethyst_shard.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.LEY_LINK,
            () -> false,
            () -> true,
            () -> {
                clientFirst = null;
                CursorClientEvents.setLeftClickAction(UnitAction.LEY_LINK);
            },
            null,
            List.of(
                FormattedCharSequence.forward("Ley Link  (free, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Click one of your buildings, then another within " + LeyNetwork.MAX_DISTANCE
                    + " blocks, to link them. Link a pair again to unlink it.", Style.EMPTY),
                FormattedCharSequence.forward("Linked Wind-Chime Towers earn +" + Math.round(LeyNetwork.RESONANCE_PER_GENERATOR * 100)
                    + "% energy per other tower in the network (max +" + Math.round(LeyNetwork.RESONANCE_CAP * 100) + "%).", Style.EMPTY),
                FormattedCharSequence.forward("Up to " + LeyNetwork.LINKS_PER_BUILDING + " links per building, "
                    + LeyNetwork.LINKS_PER_PLAYER + " in all.", Style.EMPTY),
                FormattedCharSequence.forward("The earth remembers the lines between our towers.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) { }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (targetBp == null || !(unitUsing instanceof LivingEntity self))
            return;
        String owner = unitUsing.getOwnerName();
        if (level.isClientSide()) {
            // the client's own guess, only to keep the cursor armed for the second click; the server decides
            BuildingPlacement b = BuildingUtils.findBuilding(true, targetBp);
            if (b != null && owner != null && owner.equals(b.ownerName) && b.isBuilt && clientFirst == null) {
                clientFirst = b.centrePos;
                rearmCursor = true;
            } else {
                clientFirst = null;
            }
            return;
        }
        if (!(level instanceof ServerLevel sl))
            return;
        if (order(sl, self, owner, targetBp)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * One click of the order on the server: the first remembers the building, the second links (or unlinks) the pair.
     * Returns true only when a link was made or removed (the cooldown starts then). Public for the bot and the game test.
     */
    public static boolean order(ServerLevel sl, LivingEntity geomancer, String owner, BlockPos clicked) {
        BuildingPlacement b = BuildingUtils.findBuilding(false, clicked);
        if (owner == null || b == null || !owner.equals(b.ownerName) || !b.isBuilt) {
            PENDING.remove(geomancer.getId());
            HudClientboundPacket.showTempMessageI18n(owner, ManaServerEvents.Refusal.LINK_INVALID.langKey);
            return false;
        }
        Pending p = PENDING.get(geomancer.getId());
        long now = sl.getGameTime();
        if (p == null || now - p.since > PENDING_TICKS || p.centre.equals(b.centrePos)) {
            PENDING.put(geomancer.getId(), new Pending(b.centrePos, now));
            HudClientboundPacket.showTempMessageI18n(owner, MSG_FIRST);
            return false;
        }
        PENDING.remove(geomancer.getId());
        BuildingPlacement a = BuildingUtils.findBuilding(false, p.centre);
        if (a == null || !owner.equals(a.ownerName) || a.isDestroyedServerside) {
            HudClientboundPacket.showTempMessageI18n(owner, ManaServerEvents.Refusal.LINK_INVALID.langKey);
            return false;
        }
        return link(sl, owner, a, b, false);
    }

    /**
     * Links (or, when already linked, unlinks) two own buildings. Returns whether the network changed; the owner is told
     * why not unless {@code quiet}. Public for the bot and the game test.
     */
    public static boolean link(ServerLevel sl, String owner, BuildingPlacement a, BuildingPlacement b, boolean quiet) {
        if (LeyNetwork.isLinked(owner, a.centrePos, b.centrePos)) {
            LeyNetwork.unlink(owner, a.centrePos, b.centrePos);
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_UNLINKED);
            return true;
        }
        ManaServerEvents.Refusal r = LeyNetwork.link(owner, a.centrePos, a.getBuilding() instanceof WindGenerator,
            b.centrePos, b.getBuilding() instanceof WindGenerator);
        if (r != ManaServerEvents.Refusal.NONE) {
            if (!quiet && r.langKey != null)
                HudClientboundPacket.showTempMessageI18n(owner, r.langKey);
            return false;
        }
        LeyNetwork.setLevel(owner, sl);
        if (!quiet)
            HudClientboundPacket.showTempMessageI18n(owner, MSG_LINKED);
        sl.playSound(null, b.centrePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2f, 0.7f);
        sl.playSound(null, a.centrePos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 2f, 0.9f);
        com.solegendary.reignofnether.barfx.BarFx.beam(sl, net.minecraft.world.phys.Vec3.atCenterOf(a.centrePos).add(0, 1, 0),
            net.minecraft.world.phys.Vec3.atCenterOf(b.centrePos).add(0, 1, 0), com.solegendary.reignofnether.barfx.BarFx.N_STORM, 40);
        return true;
    }

    /** The Geomancer died or the match reset: forget its half-made order. */
    public static void forget(int geomancerId) {
        PENDING.remove(geomancerId);
    }

    public static void clearAll() {
        PENDING.clear();
    }
}
