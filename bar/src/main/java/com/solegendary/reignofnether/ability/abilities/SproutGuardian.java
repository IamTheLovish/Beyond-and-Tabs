package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Verdant essence caster (the Thornweaver; design/essence_casters.md, lovish approved draft 2 on Oct 29): <b>Sprout
 * Guardian</b>, the big spell. Aim a spot within {@link #RANGE} blocks: a sapling treant wakes there and fights for the
 * caster's owner for {@link #LIFETIME_TICKS} ticks, then withers back into the ground. {@link #ESSENCE_COST} essence,
 * {@link #CD_SECONDS} s cooldown; refused (nothing spent, no cooldown) when the caster is short of essence or no treant
 * could be raised. Autocast: in a fight, one guardian per caster at a time.
 * <p>
 * The body is the Elder Druid's awakened treant (the Sentinel Treant, the Court's T1 tank, through
 * {@link AwakenThicket#awakenOn}), so it shares that summon's plumbing: free, no wreck, expired by AwakenThicket's
 * server tick and discarded if it loads from a save (its timer is not on disk). It is also marked summoned
 * ({@link Essence#markSummoned}), so its death drops no essence wisp. Registered as an event class (the cap's list
 * is emptied when the server stops).
 */
public class SproutGuardian extends Ability {

    public static final int ESSENCE_COST = 20;
    public static final int CD_SECONDS = 60;
    public static final int LIFETIME_TICKS = 25 * 20;
    /** Not in lovish's draft: how far the caster raises it, a placeholder for him to tune. */
    public static final int RANGE = 8;
    public static final String TAG = "bt_sprout_guardian";
    static final String KEY_CASTER = "bt_sprout_caster";

    // this ability's live guardians (server thread only), for the autocast's one-at-a-time cap
    static final List<Entity> GUARDIANS = new ArrayList<>();

    public SproutGuardian() {
        super(UnitAction.SPROUT_GUARDIAN, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        this.autoRule(AutoRule.SUMMON_WHEN_FREE);
        this.showRangeCircle = true;
    }

    @Override
    public boolean canAutocastNow(ServerLevel level, Unit unit) {
        return super.canAutocastNow(level, unit) && unit instanceof LivingEntity le && Essence.get(le) >= ESSENCE_COST;
    }

    /** The guardians {@code unit} raised that still stand. */
    @Override
    public int liveSummons(ServerLevel level, Unit unit) {
        if (!(unit instanceof LivingEntity caster))
            return 0;
        GUARDIANS.removeIf(e -> e.isRemoved() || !e.isAlive());
        int n = 0;
        UUID id = caster.getUUID();
        for (Entity e : GUARDIANS)
            if (e.getPersistentData().hasUUID(KEY_CASTER) && id.equals(e.getPersistentData().getUUID(KEY_CASTER)))
                n++;
        return n;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Sprout Guardian",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/azalea_top.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SPROUT_GUARDIAN,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SPROUT_GUARDIAN),
            null,
            List.of(
                FormattedCharSequence.forward("Sprout Guardian  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A sapling treant wakes at a spot up to " + RANGE + " blocks away and fights for you for "
                    + LIFETIME_TICKS / 20 + " s, then withers.", Style.EMPTY),
                FormattedCharSequence.forward("Lent strength, returned to the soil.", Style.EMPTY.withItalic(true))
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
        if (cast(sl, self, unitUsing.getOwnerName(), at, false) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    public static boolean isGuardian(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    /**
     * The rule: checks the essence, raises the treant on the ground by {@code at} (the aimed ground block), then
     * spends - so a refusal (short of essence, no ground there) never costs essence. Returns the treant, or null when
     * refused. Public for the bot and the game test (no cooldown handling here).
     */
    public static LivingEntity cast(ServerLevel sl, LivingEntity caster, String owner, BlockPos at, boolean quiet) {
        if (owner == null || owner.isBlank() || at == null)
            return null;
        if (Essence.get(caster) < ESSENCE_COST) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return null;
        }
        BlockPos cell = SeedOfRenewal.standingCell(sl, at.getX(), at.getZ(), at.getY() + 1);
        if (cell == null) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, SeedOfRenewal.MSG_NO_GROUND);
            return null;
        }
        LivingEntity treant = AwakenThicket.awakenOn(sl, owner, cell.below(), LIFETIME_TICKS);
        if (treant == null)
            return null;
        Essence.markSummoned(treant);
        treant.addTag(TAG);
        treant.getPersistentData().putUUID(KEY_CASTER, caster.getUUID());
        if (!Essence.trySpend(caster, ESSENCE_COST)) {   // checked above; never true unless something spent in between
            AwakenThicket.dismiss(treant);
            return null;
        }
        GUARDIANS.add(treant);
        return treant;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        GUARDIANS.clear();
    }
}
