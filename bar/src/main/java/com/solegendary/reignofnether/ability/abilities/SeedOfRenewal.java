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
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verdant essence caster (the Thornweaver; design/essence_casters.md, lovish approved draft 2 on Oct 29): <b>Seed of
 * Renewal</b>, the mid spell. Aim a spot within {@link #RANGE} blocks: a flowering sapling sprouts there and, once a
 * second for {@link #LIFETIME_TICKS} ticks, heals every friendly unit within {@link #RADIUS} blocks of it by
 * {@link #HEAL_PER_SECOND} HP (12 pulses, 72 HP to a friend that stays). {@link #ESSENCE_COST} essence,
 * {@link #CD_SECONDS} s cooldown; refused (nothing spent, no cooldown) when the caster is short of essence. Autocast:
 * at a hurt friend's feet.
 * <p>
 * The sapling is a block display, not a unit - it cannot be attacked, and it is marked summoned
 * ({@link Essence#markSummoned}) so nothing could ever farm a wisp from it. Its owner, end and next pulse live in its
 * persistent data, so a sapling saved with the world or reloaded with its chunk keeps healing on its own clock.
 * Registered as an event class: the server tick pulses and withers the saplings.
 */
public class SeedOfRenewal extends Ability {

    public static final int ESSENCE_COST = 10;
    public static final int CD_SECONDS = 25;
    public static final int RADIUS = 6;
    public static final float HEAL_PER_SECOND = 6f;
    public static final int PULSE_TICKS = 20;
    public static final int LIFETIME_TICKS = 12 * 20;
    /** Not in lovish's draft: how far the caster plants it, a placeholder for him to tune. */
    public static final int RANGE = 8;
    /** How far above or below the aimed ground the sapling (or a Sprout Guardian) may stand: a hillside, not a cliff. */
    public static final int MAX_DY = 4;
    public static final String TAG = "bt_renewal_sapling";
    public static final String MSG_NO_GROUND = "hud.essence.reignofnether.no_ground";
    static final String KEY_OWNER = "bt_renewal_owner";
    static final String KEY_END = "bt_renewal_end";
    static final String KEY_NEXT = "bt_renewal_next";

    // every live sapling (server thread only): a handful at most, so the tick walks them all
    static final List<Entity> SAPLINGS = new ArrayList<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();

    public SeedOfRenewal() {
        super(UnitAction.SEED_OF_RENEWAL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.ALLY_HURT);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public boolean canAutocastNow(ServerLevel level, Unit unit) {
        return super.canAutocastNow(level, unit) && unit instanceof LivingEntity le && Essence.get(le) >= ESSENCE_COST;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Seed of Renewal",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_side.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SEED_OF_RENEWAL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SEED_OF_RENEWAL),
            null,
            List.of(
                FormattedCharSequence.forward("Seed of Renewal  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Plants a sapling up to " + RANGE + " blocks away: for " + LIFETIME_TICKS / 20
                    + " s it heals friendly units within " + RADIUS + " blocks for " + (int) HEAL_PER_SECOND + " HP a second.", Style.EMPTY),
                FormattedCharSequence.forward("Where the old fell, the new take root.", Style.EMPTY.withItalic(true))
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

    public static boolean isSapling(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    /** The live saplings (for the game test). */
    public static List<Entity> getSaplings() {
        return SAPLINGS;
    }

    /**
     * The rule: checks the essence, plants the sapling on the ground by {@code at} (the aimed ground block), then
     * spends - so a refusal (short of essence, no ground there, or no sapling could be made) never costs essence.
     * Returns the sapling, or null when refused. Public for the bot and the game test (no cooldown handling here).
     */
    public static Entity cast(ServerLevel sl, LivingEntity caster, String owner, BlockPos at, boolean quiet) {
        if (true) return null;   // STUB for the tests-first (red) run: the rule lands in the next commit
        if (owner == null || owner.isBlank() || at == null)
            return null;
        if (Essence.get(caster) < ESSENCE_COST) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return null;
        }
        BlockPos cell = standingCell(sl, at.getX(), at.getZ(), at.getY() + 1);
        if (cell == null) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_GROUND);
            return null;
        }
        Entity sapling = plant(sl, owner, cell);
        if (sapling == null)
            return null;
        if (!Essence.trySpend(caster, ESSENCE_COST)) {   // checked above; never true unless something spent in between
            sapling.discard();
            SAPLINGS.remove(sapling);
            return null;
        }
        return sapling;
    }

    /**
     * The open cell nearest {@code refY} (within {@link #MAX_DY} up or down) in column (x, z) that something can stand
     * in: air or a replaceable plant, dry, on a sturdy top face. Null when there is none (water, a cliff). Scanned
     * outwards from the clicked height, not read off the heightmap: a canopy, a roof or the terrain over a game-test
     * arena must not lift the spell onto it (PlantThicket.openSpot has the same reason). Shared with Sprout Guardian.
     */
    public static BlockPos standingCell(ServerLevel sl, int x, int z, int refY) {
        for (int i = 0; i <= 2 * MAX_DY; i++) {
            int dy = i % 2 == 1 ? -(i + 1) / 2 : i / 2;   // 0, -1, +1, -2, +2 ...
            BlockPos p = new BlockPos(x, refY + dy, z);
            BlockState here = sl.getBlockState(p);
            if ((!here.isAir() && !here.canBeReplaced()) || !here.getFluidState().isEmpty())
                continue;
            BlockPos below = p.below();
            if (sl.getBlockState(below).isFaceSturdy(sl, below, Direction.UP))
                return p;
        }
        return null;
    }

    /** A sapling for {@code owner} standing in the block {@code cell} (its bottom face on the ground below). */
    static Entity plant(ServerLevel sl, String owner, BlockPos cell) {
        Entity display = EntityType.BLOCK_DISPLAY.create(sl);
        if (display == null)
            return null;
        CompoundTag tag = new CompoundTag();
        display.saveWithoutId(tag);
        tag.put("block_state", NbtUtils.writeBlockState(Blocks.FLOWERING_AZALEA.defaultBlockState()));
        CompoundTag tf = new CompoundTag();
        tf.put("left_rotation", floats(0, 0, 0, 1));
        tf.put("right_rotation", floats(0, 0, 0, 1));
        tf.put("translation", floats(-0.5f, 0, -0.5f));   // the block centred on the entity, standing on its feet
        tf.put("scale", floats(1, 1, 1));
        tag.put("transformation", tf);
        display.load(tag);
        display.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0, 0);
        display.addTag(TAG);
        Essence.markSummoned(display);
        long now = sl.getGameTime();
        display.getPersistentData().putString(KEY_OWNER, owner);
        display.getPersistentData().putLong(KEY_END, now + LIFETIME_TICKS);
        display.getPersistentData().putLong(KEY_NEXT, now + PULSE_TICKS);
        sl.addFreshEntity(display);   // onJoin tracks it
        if (!SAPLINGS.contains(display))
            SAPLINGS.add(display);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, display.getX(), display.getY() + 0.6, display.getZ(), 12, 0.5, 0.4, 0.5, 0.0);
        sl.playSound(null, cell, SoundEvents.AZALEA_PLACE, SoundSource.NEUTRAL, 1.5f, 0.8f);
        return display;
    }

    private static ListTag floats(float... v) {
        ListTag list = new ListTag();
        for (float f : v)
            list.add(FloatTag.valueOf(f));
        return list;
    }

    /**
     * One pulse: {@code amount} HP to every friendly unit (the owner's or an ally's, never a foe) within {@link #RADIUS}
     * of the sapling that is not at full health. Returns how many it healed.
     */
    static int pulse(ServerLevel sl, Entity sapling, String owner, float amount) {
        int n = 0;
        double r2 = (double) RADIUS * RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, sapling.getX(), sapling.getZ(), RADIUS, scratch)) {
            if (!le.isAlive() || le.level() != sl || !(le instanceof Unit u) || le.distanceToSqr(sapling) > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || !AlliancesServerEvents.isAlliedOrOwned(owner, o) || le.getHealth() >= le.getMaxHealth())
                continue;
            le.heal(amount);
            n++;
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() * 0.8, le.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
        }
        if (n > 0)
            sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, sapling.getX(), sapling.getY() + 1.2, sapling.getZ(), 3, 0.6, 0.2, 0.6, 0.0);
        return n;
    }

    /** A sapling that (re)loads from disk is a new entity object: adopt it, its clock is in its persistent data. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() || !isSapling(evt.getEntity()))
            return;
        SAPLINGS.removeIf(Entity::isRemoved);
        if (!SAPLINGS.contains(evt.getEntity()))
            SAPLINGS.add(evt.getEntity());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null || SAPLINGS.isEmpty())
            return;
        Iterator<Entity> it = SAPLINGS.iterator();
        List<Entity> withered = new ArrayList<>();
        while (it.hasNext()) {
            Entity s = it.next();
            if (s.isRemoved()) {
                it.remove();
                continue;
            }
            if (!(s.level() instanceof ServerLevel sl) || sl.getServer() != evt.getServer())
                continue;
            CompoundTag data = s.getPersistentData();
            long now = sl.getGameTime(), end = data.getLong(KEY_END), next = data.getLong(KEY_NEXT);
            String owner = data.getString(KEY_OWNER);
            // every pulse whose second has come, up to the last one at the end (a lagging tick never skips a pulse)
            while (next <= now && next <= end) {
                pulse(sl, s, owner, HEAL_PER_SECOND);
                next += PULSE_TICKS;
            }
            data.putLong(KEY_NEXT, next);
            if (now >= end) {
                it.remove();
                withered.add(s);
            }
        }
        for (Entity s : withered) {
            if (s.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, s.getX(), s.getY() + 0.8, s.getZ(), 8, 0.4, 0.3, 0.4, 0.0);
                sl.playSound(null, s.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 1f, 0.8f);
            }
            s.discard();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        SAPLINGS.clear();
    }
}
