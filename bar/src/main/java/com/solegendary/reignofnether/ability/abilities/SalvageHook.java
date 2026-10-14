package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
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
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tidewrought Salvager (combat engineer, design/combat_engineers.md): <b>Salvage Hook</b>. Click near a wreck within
 * {@link #RANGE} (the wreck nearest the click, within {@link #AIM_SLACK} of it): a hook line takes it and reels it to
 * the Salvager's feet over {@link #REEL_TICKS} ticks, where its ordinary reclaim strips it. Refused, with no cooldown,
 * when there is no wreck there. {@link #CD_SECONDS} s cooldown, free.
 * <p>
 * The Wreck Harbour's people salvage for a living: the trick is taking a contested wreck out of the enemy's reach (or
 * out from under its towers). Tempo: the metal changes sides of the fight. Counter: the wreck crawls in a straight,
 * visible line and can still be reclaimed by anyone in reach on the way; kill the Salvager and the hook drops where it
 * is. The Harpooner hooks units; this hooks scrap. Ticked by CombatEngineerServerEvents; nothing per tick but the reels.
 */
public class SalvageHook extends Ability {

    public static final int CD_SECONDS = 18;
    public static final float RANGE = 16f;
    public static final float AIM_SLACK = 4f;
    public static final int REEL_TICKS = 30;
    /** The wreck stops this far in front of the Salvager (on top of it, the heap would hide its feet). */
    static final double LAND_GAP = 1.2;
    public static final String MSG_NO_WRECK = "hud.reignofnether.salvage_hook_no_wreck";

    /** One wreck on the line. Public for the game test. */
    public static final class Reel {
        public final LivingEntity salvager;
        public final Entity wreck;
        final Vec3 from;
        final long start;

        Reel(LivingEntity salvager, Entity wreck, long start) {
            this.salvager = salvager;
            this.wreck = wreck;
            this.from = wreck.position();
            this.start = start;
        }
    }

    static final List<Reel> ACTIVE = new ArrayList<>();

    public SalvageHook() {
        super(UnitAction.SALVAGE_HOOK, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, AIM_SLACK, false, true);
        this.autoRule(AutoRule.MANUAL);   // autocast: an economy order
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Salvage Hook",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/fishing_rod.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SALVAGE_HOOK,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SALVAGE_HOOK),
            null,
            List.of(
                FormattedCharSequence.forward("Salvage Hook  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Hooks a wreck within " + (int) RANGE + " blocks and reels it to the Salvager's feet in "
                    + REEL_TICKS / 20f + " s.", Style.EMPTY),
                FormattedCharSequence.forward("Anyone can still reclaim it on the way.", Style.EMPTY),
                FormattedCharSequence.forward("Finders keepers.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (hook(sl, self, unitUsing.getOwnerName(), targetBp, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** An instant cast: the Salvager keeps its place and its orders. */
    @Override
    public boolean shouldResetBehaviours() {
        return false;
    }

    /**
     * Hooks the wreck nearest {@code aim} (within {@link #AIM_SLACK} of it and {@link #RANGE} of the Salvager); null
     * (HUD message unless quiet) when there is none. A wreck already on a line is not hooked twice. No cooldown
     * handling here. Public for the bot and the game test.
     */
    @Nullable
    public static Reel hook(ServerLevel sl, LivingEntity salvager, String owner, BlockPos aim, boolean quiet) {
        Entity best = null;
        double bestD = AIM_SLACK * AIM_SLACK, range2 = RANGE * RANGE;
        List<Entity> wrecks = WreckServerEvents.getWrecks();
        for (int i = 0, n = wrecks.size(); i < n; i++) {
            Entity w = wrecks.get(i);
            if (w.isRemoved() || w.level() != sl || w.distanceToSqr(salvager) > range2 || isReeled(w))
                continue;
            double d = w.distanceToSqr(aim.getX() + 0.5, w.getY(), aim.getZ() + 0.5);
            if (d <= bestD) {
                bestD = d;
                best = w;
            }
        }
        if (best == null) {
            if (!quiet && owner != null && !owner.isBlank())
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_WRECK);
            return null;
        }
        ACTIVE.removeIf(r -> r.salvager == salvager);   // one line per Salvager: a recast drops the old one where it is
        Reel reel = new Reel(salvager, best, sl.getGameTime());
        ACTIVE.add(reel);
        sl.playSound(null, salvager.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 1.0f, 0.6f);
        return reel;
    }

    static boolean isReeled(Entity wreck) {
        for (int i = 0, n = ACTIVE.size(); i < n; i++)
            if (ACTIVE.get(i).wreck == wreck)
                return true;
        return false;
    }

    /** One tick of every line: the wreck moves along it; a dead Salvager drops it. From CombatEngineerServerEvents. */
    public static void tick(long gameTime) {
        if (ACTIVE.isEmpty())
            return;
        Iterator<Reel> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Reel r = it.next();
            LivingEntity s = r.salvager;
            if (r.wreck.isRemoved() || !s.isAlive() || s.isRemoved() || s.level() != r.wreck.level()
                    || !(s.level() instanceof ServerLevel sl)) {
                it.remove();
                continue;
            }
            float t = Math.min(1f, (gameTime - r.start) / (float) REEL_TICKS);
            Vec3 to = landingSpot(s, r.from);
            Vec3 at = r.from.lerp(to, t);
            r.wreck.moveTo(at.x, at.y, at.z, r.wreck.getYRot(), 0);
            if (gameTime % 4 == 0) {
                // the line: a few crit sparks from the Salvager's hands to the heap
                Vec3 hands = new Vec3(s.getX(), s.getY() + s.getBbHeight() * 0.6, s.getZ());
                for (int i = 1; i <= 4; i++) {
                    Vec3 p = hands.lerp(at.add(0, 0.3, 0), i / 5.0);
                    sl.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
            if (t >= 1f) {
                it.remove();
                sl.playSound(null, s.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0f, 0.8f);
            }
        }
    }

    /** {@link #LAND_GAP} in front of the Salvager, toward where the wreck came from, at the Salvager's feet. */
    static Vec3 landingSpot(LivingEntity s, Vec3 from) {
        double dx = from.x - s.getX(), dz = from.z - s.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-3)
            return new Vec3(s.getX(), s.getY(), s.getZ());
        return new Vec3(s.getX() + dx / len * LAND_GAP, s.getY(), s.getZ() + dz / len * LAND_GAP);
    }

    /** Is this wreck on a line right now? Public for the game test. */
    public static boolean isOnLine(Entity wreck) {
        return isReeled(wreck);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }
}
