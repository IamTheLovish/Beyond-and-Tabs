package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.BrassZeppelinUnit;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Brassworks (Brass Zeppelin): <b>Carpet Bomb</b> (second-t3-design.md). The Zeppelin marks a {@link #LENGTH}-block line
 * along the way it faces (its target's way, else its heading) with a red strip for {@link #TELEGRAPH_TICKS} ticks
 * (1.5 s). Then, over {@link #BOMB_COUNT} x {@link #BOMB_GAP_TICKS} = 2 s, {@link #BOMB_COUNT} bombs drop one after the
 * other down the line, {@link #BOMB_SPACING} blocks apart. A bomb is the Bombard Crew's {@link BombardShell} set to
 * {@link BombardShell#groundOnly}: it bursts for {@link #BOMB_DAMAGE} on enemy ground units and dents enemy buildings,
 * and never touches a flyer. The line is fixed at cast time. {@link #CD_SECONDS} s cooldown and {@link #ENERGY_COST}
 * energy a sortie (an owner without an energy pool - a test, a scenario - flies free).
 * <p>
 * Honest version: the design has the airship dive along the line while it bombs; here it stays hovering where it is and
 * the bombs fall at the line's points from its altitude (its body does not move). It cannot cast while venting.
 */
public class CarpetBomb extends Ability {

    public static final int CD_SECONDS = 30;
    public static final float LENGTH = 20f;
    public static final int TELEGRAPH_TICKS = 30;
    public static final int BOMB_COUNT = 5;
    public static final int BOMB_GAP_TICKS = 8;
    public static final double BOMB_SPACING = 4;
    public static final float BOMB_DAMAGE = 30f;
    public static final int ENERGY_COST = 100;
    public static final double HALF_WIDTH = BombardShell.SPLASH_RADIUS;

    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 1.4f);

    public CarpetBomb() {
        super(UnitAction.CARPET_BOMB, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, LENGTH, false, true);
        this.autoRule(AutoRule.MANUAL);   // a sortie costs energy and is aimed along the facing: the player picks the moment
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Carpet Bomb",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/fire_charge.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.CARPET_BOMB),
            null,
            List.of(
                FormattedCharSequence.forward("Carpet Bomb  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Marks a " + (int) LENGTH + "-block line in front of the Zeppelin. After 1.5 s " + BOMB_COUNT
                    + " bombs fall down it over 2 s: " + (int) BOMB_DAMAGE + " damage each to enemy ground units and buildings. Flyers are not touched.", Style.EMPTY),
                FormattedCharSequence.forward("The red line shows first: step off it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof BrassZeppelinUnit zep) || !(level instanceof ServerLevel sl))
            return;
        if (!Heat.canFire(zep))
            return;   // venting (Ballast Dump): no firing
        if (!payEnergy(zep))
            return;
        Vec3 from = zep.position();
        Vec3 dir = zep.facing();
        telegraph(sl, from, dir);
        sl.playSound(null, zep.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL, 3f, 0.5f);
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS / 2, () -> telegraph(sl, from, dir));
        for (int i = 0; i < BOMB_COUNT; i++) {
            final int n = i;
            TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS + i * BOMB_GAP_TICKS, () -> {
                if (zep.isAlive() && Heat.canFire(zep))   // killed mid-run, or the ballast was dumped: no more bombs
                    drop(sl, zep, from, dir, n);
            });
        }
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(zep.getId(), this.action, this.cooldownMax);
    }

    /** Pays {@link #ENERGY_COST} through the Mana core; an owner without a pool pays nothing. False if refused for energy. */
    static boolean payEnergy(BrassZeppelinUnit zep) {
        String owner = zep.getOwnerName();
        float power = ManaServerEvents.tryCast(zep, owner, ENERGY_COST);
        return power > 0 || ManaServerEvents.lastRefusal() == ManaServerEvents.Refusal.NO_POOL;
    }

    /** The point of the line {@code distance} blocks from {@code from} along {@code dir}. */
    public static Vec3 pointAt(Vec3 from, Vec3 dir, double distance) {
        return new Vec3(from.x + dir.x * distance, from.y, from.z + dir.z * distance);
    }

    /** The distance of bomb {@code n} from the start of the line: the middle of its slot. */
    public static double bombDistance(int n) {
        return BOMB_SPACING * (n + 0.5);
    }

    static void telegraph(ServerLevel sl, Vec3 from, Vec3 dir) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int steps = (int) (LENGTH * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = pointAt(from, dir, LENGTH * i / steps);
            double y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p.x), (int) Math.floor(p.z)) + 0.2;
            for (int s = -1; s <= 1; s += 2)
                sl.sendParticles(RED, p.x + side.x * HALF_WIDTH * s, y, p.z + side.z * HALF_WIDTH * s, 1, 0.03, 0.02, 0.03, 0.0);
        }
        for (int n = 0; n < BOMB_COUNT; n++) {   // a cross at every bomb's mark
            Vec3 p = pointAt(from, dir, bombDistance(n));
            double y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p.x), (int) Math.floor(p.z)) + 0.2;
            sl.sendParticles(RED, p.x, y, p.z, 4, 0.5, 0.02, 0.5, 0.0);
        }
    }

    /** Drops bomb {@code n} of the run from {@code zep}'s altitude over its mark; it lands on the ground under it. Returns the bomb. Public for the game test. */
    public static BombardShell drop(ServerLevel sl, BrassZeppelinUnit zep, Vec3 from, Vec3 dir, int n) {
        Vec3 p = pointAt(from, dir, bombDistance(n));
        double ground = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(p.x), (int) Math.floor(p.z));
        BombardShell bomb = new BombardShell(sl, zep, BOMB_DAMAGE).groundOnly();
        bomb.setPos(p.x, zep.getY(), p.z);
        bomb.aimAt(p.x, ground, p.z);
        sl.addFreshEntity(bomb);
        sl.playSound(null, zep.blockPosition(), SoundEvents.TNT_PRIMED, SoundSource.NEUTRAL, 1.5f, 1.2f);
        return bomb;
    }
}
