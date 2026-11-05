package com.solegendary.reignofnether.unit.modelling.motion;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The living army's review stage (design/living_army.md, section 4): `/parade [size]` lays out a block of every unit
 * type (size x size each, grouped in registry order, which follows the factions) on a flat pad in front of the caller
 * and runs a scripted loop - stand, march, wheel, march back, a hard halt, then blows from every side, light and heavy
 * - over and over, so motion can be judged at close and RTS zoom (the client's A/B toggle: Numpad 0 or
 * `/livingarmy on|off`). `/parade stop` clears it. A dev tool for operators: it uses only the existing spawn, order and
 * damage calls, changes no rule and is never part of a match. Units are healed at the start of each loop.
 */
public final class ParadeGround {

    public static final int STAND_END = 200, MARCH_END = 500, WHEEL_END = 600, BACK_END = 900, LOOP_TICKS = 1200;
    public static final int HIT_EVERY = 20;
    public static final float LIGHT_HIT = 1f, HEAVY_HIT_OF_MAX = 0.25f;
    private static final int MARCH_BLOCKS = 20, ROW_WIDTH = 64;

    private static ParadeGround active = null;

    private final ServerLevel level;
    private final List<Mob> units = new ArrayList<>();
    private final List<int[]> blocks = new ArrayList<>();   // {first index into units, count}
    private final List<BlockPos> home = new ArrayList<>();
    private final RandomSource random = RandomSource.create(20261027L);
    private int t = 0, loops = 0, hits = 0, kills = 0;

    private ParadeGround(ServerLevel level) {
        this.level = level;
    }

    /** Every unit type the mod registers that is a mob and a unit, in registry order. */
    @SuppressWarnings("unchecked")
    public static List<EntityType<? extends Mob>> unitTypes(ServerLevel level) {
        List<EntityType<? extends Mob>> out = new ArrayList<>();
        for (RegistryObject<EntityType<?>> ro : EntityRegistrar.ENTITIES.getEntries()) {
            Entity e = ro.get().create(level);
            if (e == null)
                continue;
            if (e instanceof Mob && e instanceof Unit)
                out.add((EntityType<? extends Mob>) ro.get());
            e.discard();
        }
        return out;
    }

    public static ParadeGround active() {
        return active;
    }

    public List<Mob> units() {
        return units;
    }

    public int loopsCompleted() {
        return loops;
    }

    /** Units the parade's own blows killed (must stay 0: heavy blows heal a unit first if they would). */
    public int kills() {
        return kills;
    }

    /**
     * Lays the parade out from `origin` (the pad's corner, at the units' feet) and starts the loop; any parade already
     * running is stopped first. `stampPad` = lay smooth stone under the field and clear the air above it.
     */
    public static ParadeGround start(ServerLevel level, BlockPos origin, String owner, int size, boolean stampPad) {
        stop();
        ParadeGround p = new ParadeGround(level);
        size = Mth.clamp(size, 1, 5);
        int x = 0, z = 0, rowDepth = 0;
        List<EntityType<? extends Mob>> types = unitTypes(level);
        List<int[]> cells = new ArrayList<>();          // {x, z, cell} per type
        for (EntityType<? extends Mob> type : types) {
            int cell = Math.max(2, Mth.ceil(type.getWidth()) + 1);
            int span = size * cell + 2;
            if (x + span > ROW_WIDTH) {
                x = 0;
                z += rowDepth;
                rowDepth = 0;
            }
            cells.add(new int[] {x, z, cell});
            x += span;
            rowDepth = Math.max(rowDepth, span);
        }
        int depth = z + rowDepth;
        if (stampPad) {
            int margin = MARCH_BLOCKS + 4;
            for (int dx = -2; dx < ROW_WIDTH + margin; dx++)
                for (int dz = -2; dz < depth + margin; dz++) {
                    BlockPos floor = origin.offset(dx, -1, dz);
                    level.setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int dy = 0; dy < 6; dy++)
                        level.setBlockAndUpdate(floor.above(1 + dy), Blocks.AIR.defaultBlockState());
                }
        }
        for (int i = 0; i < types.size(); i++) {
            int[] c = cells.get(i);
            int first = p.units.size();
            for (int a = 0; a < size; a++)
                for (int b = 0; b < size; b++) {
                    BlockPos at = origin.offset(c[0] + a * c[2], -1, c[1] + b * c[2]);
                    Entity e = UnitServerEvents.spawnMob(types.get(i), level, at, owner);
                    if (e instanceof Mob m) {
                        m.setYRot(0f);
                        m.setYBodyRot(0f);
                        p.units.add(m);
                        p.home.add(at.above());
                    }
                }
            p.blocks.add(new int[] {first, p.units.size() - first});
        }
        active = p;
        return p;
    }

    public static void stop() {
        if (active == null)
            return;
        for (Mob m : active.units)
            m.discard();
        active = null;
    }

    private void tick() {
        int phase = t % LOOP_TICKS;
        if (phase == 0) {
            for (Mob m : units)
                if (m.isAlive())
                    m.setHealth(m.getMaxHealth());
        }
        if (phase == STAND_END)
            orderAll(MARCH_BLOCKS, 0);
        else if (phase == MARCH_END)
            orderAll(MARCH_BLOCKS, MARCH_BLOCKS);   // the head of the march wheels right
        else if (phase == WHEEL_END)
            orderAll(0, 0);
        else if (phase == BACK_END)
            for (Mob m : units)
                if (m instanceof Unit u)
                    u.setMoveTarget(null);       // the hard halt
        if (phase > BACK_END && phase % HIT_EVERY == 0 && !blocks.isEmpty())
            strikeABlock();
        t++;
        if (t % LOOP_TICKS == 0)
            loops++;
    }

    /** Every unit to its home cell moved `forward` blocks along +z and `right` blocks along -x (the units face +z). */
    private void orderAll(int forward, int right) {
        for (int i = 0; i < units.size(); i++)
            if (units.get(i) instanceof Unit u && units.get(i).isAlive())
                u.setMoveTarget(home.get(i).offset(-right, 0, forward));
    }

    /** One block takes a blow from one side, light and heavy in turn: a source point 2 blocks off each unit's side. */
    private void strikeABlock() {
        int[] blk = blocks.get(random.nextInt(blocks.size()));
        float sideDeg = random.nextInt(4) * 90f;    // 0 front, 90 right, 180 rear, 270 left (relative to the unit)
        boolean heavy = (hits++ & 1) == 1;
        var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC);
        for (int i = blk[0]; i < blk[0] + blk[1]; i++) {
            Mob m = units.get(i);
            if (!m.isAlive())
                continue;
            float yaw = (m.yBodyRot + sideDeg) * Mth.DEG_TO_RAD;
            // the unit faces (-sin yaw, cos yaw); its right hand is (-cos yaw, -sin yaw): rotate the facing by sideDeg
            Vec3 src = m.position().add(-Mth.sin(yaw) * 2.0, 1.0, Mth.cos(yaw) * 2.0);
            float amount = heavy ? m.getMaxHealth() * HEAVY_HIT_OF_MAX : LIGHT_HIT;
            if (m.getHealth() - amount < 1f)
                m.setHealth(m.getMaxHealth());
            m.hurt(new DamageSource(type, src), amount);
            if (!m.isAlive())
                kills++;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase == TickEvent.Phase.END && active != null)
            active.tick();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("parade")
            .requires(src -> src.hasPermission(2))
            .executes(c -> startHere(c.getSource(), 1))
            .then(Commands.literal("stop").executes(c -> {
                stop();
                c.getSource().sendSuccess(() -> Component.literal("Parade cleared"), false);
                return 1;
            }))
            .then(Commands.argument("size", IntegerArgumentType.integer(1, 5))
                .executes(c -> startHere(c.getSource(), IntegerArgumentType.getInteger(c, "size")))));
    }

    private static int startHere(CommandSourceStack src, int size) {
        BlockPos at = BlockPos.containing(src.getPosition()).offset(4, 0, 4);
        ParadeGround p = start(src.getLevel(), at, src.getTextName(), size, true);
        int n = p.units.size();
        src.sendSuccess(() -> Component.literal("Parade: " + n + " units, " + p.blocks.size()
            + " types. Loop " + LOOP_TICKS / 20 + " s. Numpad 0 or /livingarmy on|off toggles the living army. /parade stop clears."), false);
        return n;
    }
}
