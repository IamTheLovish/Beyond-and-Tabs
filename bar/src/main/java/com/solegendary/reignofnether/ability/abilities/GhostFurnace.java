package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.Summons;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.GhostFurnaceUnit;

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
import java.util.List;

/**
 * Brassworks essence caster (the Soul Engineer; design/essence_casters.md, lovish approved draft 2 on Oct 29, Revision
 * 1): <b>Ghost Furnace</b>, the mid spell. Aim a spot within {@link #RANGE} blocks: a {@link GhostFurnaceUnit} - a
 * summoned spirit-steam furnace, 150 HP - stands there for {@link GhostFurnaceUnit#LIFETIME_TICKS} and once a second
 * scalds every enemy ground unit within {@link #RADIUS} for {@link #SCALD_DAMAGE} ({@link #scald}). Marked summoned
 * (Revision 1: its death drops no essence); siege lobbers hit it at the defence rate (GhostFurnaceUnit.hurt).
 * {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s cooldown.
 * <p>
 * Not the Guildmaster's Deploy Turret: that is a metal-paid rifle on a tripod (40 HP, one target at a time at 12 blocks,
 * heats and vents); this is an essence-paid area burner (150 HP, every enemy within 5 at once, never heats), a short
 * zone to hold or deny rather than a gun.
 * <p>
 * Refused - nothing spent, no cooldown - when there is no ground to stand it on near the spot, or the caster is short of
 * essence. Autocast: on a crowd (3 enemies within the scald around one of them).
 */
public class GhostFurnace extends Ability {

    public static final int ESSENCE_COST = 10;
    public static final int CD_SECONDS = 25;
    /** Not in lovish's draft: how far the caster sets it (the Deploy Turret's reach), a placeholder for him to tune. */
    public static final int RANGE = 8;
    public static final int RADIUS = 5;
    public static final float SCALD_DAMAGE = 4f;
    public static final int SCALD_TICKS = 20;
    /** Enemies this far above or below the furnace still feel the steam (a slope, not a cliff top). */
    static final double MAX_DY = 3;
    public static final String MSG_NO_GROUND = "hud.essence.reignofnether.furnace_no_ground";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public GhostFurnace() {
        super(UnitAction.GHOST_FURNACE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Ghost Furnace",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/soul_campfire_fire.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.GHOST_FURNACE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.GHOST_FURNACE),
            null,
            List.of(
                FormattedCharSequence.forward("Ghost Furnace  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Sets a " + (int) GhostFurnaceUnit.HEALTH + " HP spirit-steam furnace within " + RANGE
                    + " blocks for " + GhostFurnaceUnit.LIFETIME_TICKS / 20 + " s: once a second it scalds every enemy ground unit within "
                    + RADIUS + " blocks for " + (int) SCALD_DAMAGE + ".", Style.EMPTY),
                FormattedCharSequence.forward("A summon: its death gives no essence. Siege lobbers hit it as a defence.", Style.EMPTY),
                FormattedCharSequence.forward("The dead stoke it; the living keep their distance.", Style.EMPTY.withItalic(true))
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
        if (place(sl, self, unitUsing.getOwnerName(), targetBp, false) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * The cell above solid ground nearest the aimed ground block {@code aim}: scanned up and down from its height (game
     * test arenas sit under generated terrain, so a heightmap would land on the surface far above). Null when none.
     */
    @Nullable
    public static BlockPos standingCell(ServerLevel sl, BlockPos aim) {
        for (int d = 0; d <= 4; d++)
            for (int sign : new int[] { 1, -1 }) {
                BlockPos ground = aim.above(d * sign);
                BlockPos cell = ground.above();
                if (sl.getBlockState(ground).isFaceSturdy(sl, ground, net.minecraft.core.Direction.UP)
                        && sl.getBlockState(cell).getCollisionShape(sl, cell).isEmpty()
                        && sl.getBlockState(cell.above()).getCollisionShape(sl, cell.above()).isEmpty())
                    return cell;
                if (d == 0)
                    break;
            }
        return null;
    }

    /**
     * The rule: pulls the aim in to {@link #RANGE}, finds ground there, then spends the essence, then sets the furnace
     * (marked summoned) - so a refusal never costs essence. Returns the furnace, or null when refused. Public for the
     * bot and the game test (no cooldown handling here).
     */
    @Nullable
    public static GhostFurnaceUnit place(ServerLevel sl, LivingEntity caster, String owner, BlockPos aim, boolean quiet) {
        if (true) return null;   // STUB (red proof)
        if (owner == null || owner.isBlank() || aim == null)
            return null;
        Vec3 at = Vec3.atBottomCenterOf(aim);
        Vec3 d = at.subtract(caster.position());
        if (d.horizontalDistanceSqr() > RANGE * RANGE)
            at = caster.position().add(new Vec3(d.x, 0, d.z).normalize().scale(RANGE));
        BlockPos cell = standingCell(sl, BlockPos.containing(at.x, aim.getY(), at.z));
        if (cell == null) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_GROUND);
            return null;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return null;
        }
        Entity e = UnitServerEvents.spawnMob(EntityRegistrar.GHOST_FURNACE_UNIT.get(), sl, cell.below(), owner);   // stands on top of the given block
        if (!(e instanceof GhostFurnaceUnit furnace)) {
            Essence.add(caster, ESSENCE_COST);   // nothing came of it: the essence goes back
            if (e != null)
                e.discard();
            return null;
        }
        Summons.mark(furnace);
        furnace.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, caster.getYRot(), 0);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, furnace.getX(), furnace.getY() + 0.6, furnace.getZ(), 16, 0.4, 0.4, 0.4, 0.03);
        sl.sendParticles(ParticleTypes.CLOUD, furnace.getX(), furnace.getY() + 1.0, furnace.getZ(), 10, 0.4, 0.3, 0.4, 0.05);
        sl.playSound(null, cell, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.NEUTRAL, 2f, 0.6f);
        return furnace;
    }

    /** A living enemy ground unit of {@code owner} (own and allied units are friends; flyers ride above the steam). */
    public static boolean isScaldable(String owner, LivingEntity le) {
        if (!(le instanceof Unit u) || !le.isAlive() || u.isFlyingUnit() || owner == null || owner.isBlank())
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isBlank() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }

    /** One second of steam: every enemy ground unit within {@link #RADIUS} of the furnace takes {@code damage}. Returns how many. */
    public static int scald(ServerLevel sl, GhostFurnaceUnit furnace, String owner, float damage) {
        int hit = 0;
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity le : UnitGrid.near(sl, furnace.getX(), furnace.getZ(), RADIUS, scratch)) {
            if (le == furnace || le.level() != sl || !isScaldable(owner, le))
                continue;
            double dx = le.getX() - furnace.getX(), dz = le.getZ() - furnace.getZ();
            if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(le.getY() - furnace.getY()) > MAX_DY)
                continue;
            targets.add(le);
        }
        scratch.clear();
        var src = sl.damageSources().indirectMagic(furnace, furnace);
        for (LivingEntity le : targets) {
            le.hurt(src, damage);
            hit++;
            sl.sendParticles(ParticleTypes.CLOUD, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
        }
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, furnace.getX(), furnace.getY() + 1.1, furnace.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
        sl.sendParticles(ParticleTypes.CLOUD, furnace.getX(), furnace.getY() + 0.3, furnace.getZ(), 10, RADIUS * 0.4, 0.1, RADIUS * 0.4, 0.03);
        return hit;
    }
}
