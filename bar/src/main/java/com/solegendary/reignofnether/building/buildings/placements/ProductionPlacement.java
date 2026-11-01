package com.solegendary.reignofnether.building.buildings.placements;

import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import static com.solegendary.reignofnether.building.BuildingUtils.getMinCorner;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.GraveyardUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProdDupeRule;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public class ProductionPlacement extends BuildingPlacement {
    protected ArrayList<BlockPos> rallyPoints = new ArrayList<>();
    protected LivingEntity rallyPointEntity;
    public List<Button> productionButtons;
    // counts its changes, so a per-tick cache over the queues is never stale
    public final List<ActiveProduction> productionQueue = new ProductionQueueList<>();
    /**
     * Depth of one building's production queue (BAR factories take a long queue; RoN had no cap at all, so a held
     * key could pile up hundreds of items that only ever showed as "+N"). Hitting it says "Queue full" and refuses the
     * item - existing items are never touched.
     */
    public static final int MAX_QUEUE = 16;
    /** BAR's repeat queue: when on, a completed trained unit re-queues itself at the back. */
    public boolean repeatQueue = false;
    /** Persists the repeat toggle in the per-building save data so it survives reloads. */
    public static final com.solegendary.reignofnether.building.data.DataType<Boolean> REPEAT_QUEUE =
            com.solegendary.reignofnether.building.data.DataType.createRegistered(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            com.solegendary.reignofnether.ReignOfNether.MOD_ID, "repeat_queue"),
                    (tag, server) -> tag.getBoolean("repeat"),
                    repeat -> {
                        CompoundTag tag = new CompoundTag();
                        tag.putBoolean("repeat", repeat);
                        return tag;
                    },
                    () -> false);

    /** Sets the repeat toggle and records it in the building's save data (server side). */
    public void setRepeatQueue(boolean repeat) {
        this.repeatQueue = repeat;
        if (getLevel() != null && !getLevel().isClientSide())
            getDataStorage().setData(REPEAT_QUEUE, repeat);
    }
    /** The Steward (StewardServerEvents) is paused at this capitol: the owner toggles it from the HUD. */
    public boolean stewardPaused = false;
    public static final com.solegendary.reignofnether.building.data.DataType<Boolean> STEWARD_PAUSED =
            com.solegendary.reignofnether.building.data.DataType.createRegistered(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            com.solegendary.reignofnether.ReignOfNether.MOD_ID, "steward_paused"),
                    (tag, server) -> tag.getBoolean("paused"),
                    paused -> {
                        CompoundTag tag = new CompoundTag();
                        tag.putBoolean("paused", paused);
                        return tag;
                    },
                    () -> false);

    /** Pauses or resumes the Steward at this capitol and records it in the building's save data (server side). */
    public void setStewardPaused(boolean paused) {
        this.stewardPaused = paused;
        if (getLevel() != null && !getLevel().isClientSide())
            getDataStorage().setData(STEWARD_PAUSED, paused);
    }

    /**
     * BAR's factoryguard (Ctrl+G, tester request): while on, every builder this factory produces is given a guard
     * order on it as its first order - it assists the factory's production instead of walking to the rally point.
     * Combat units are unaffected. Off by default (bots never set it).
     */
    public boolean factoryGuard = false;
    public static final com.solegendary.reignofnether.building.data.DataType<Boolean> FACTORY_GUARD =
            com.solegendary.reignofnether.building.data.DataType.createRegistered(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            com.solegendary.reignofnether.ReignOfNether.MOD_ID, "factory_guard"),
                    (tag, server) -> tag.getBoolean("guard"),
                    on -> {
                        CompoundTag tag = new CompoundTag();
                        tag.putBoolean("guard", on);
                        return tag;
                    },
                    () -> false);

    /** Sets the factory guard toggle and records it in the building's save data (server side). */
    public void setFactoryGuard(boolean on) {
        this.factoryGuard = on;
        if (getLevel() != null && !getLevel().isClientSide())
            getDataStorage().setData(FACTORY_GUARD, on);
    }

    /** Reads the factory guard toggle back from this building's save data (BuildingServerEvents, on load). */
    public void restoreFactoryGuard() {
        this.factoryGuard = Boolean.TRUE.equals(getDataStorage().getData(FACTORY_GUARD));
    }

    // ---- build power lent by guarding builders (GuardServerEvents): BAR's assist on a factory's production ----
    private float guardAssistPower = 0;
    private long guardAssistTick = Long.MIN_VALUE / 2;
    /** A guard pass's assist is good for this many ticks (the pass runs every 10). */
    public static final int GUARD_ASSIST_TICKS = 15;

    /** One guarding builder lends {@code power} to this factory's production in the guard pass of tick {@code now}. */
    public void addGuardAssist(float power, long now) {
        if (guardAssistTick != now) {
            guardAssistTick = now;
            guardAssistPower = 0;
        }
        guardAssistPower += power;
    }

    /** The build power guarding builders lend this factory now (0 when no recent guard pass lent any). */
    public float getGuardAssistPower() {
        if (getLevel() == null || getLevel().getGameTime() - guardAssistTick > GUARD_ASSIST_TICKS)
            return 0;
        return guardAssistPower;
    }

    @Override
    public float getProductionBuildPower() {
        return (super.getProductionBuildPower() + getGuardAssistPower())
            * com.solegendary.reignofnether.ability.abilities.SoulBoiler.speedMult(this);
    }
    public boolean attackRally = false;
    public ResourceName rallyResourceName = ResourceName.NONE; // used to set worker resource targets

    public ProductionPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName, ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
        if (building instanceof ProductionBuilding productionBuilding) {
            productionButtons = productionBuilding.productions.getButtons(this);
        }
        // every production building takes a queue while it is still going up (Joisco, 2026-10-23: "pre queue units in
        // workshops while the building is being built"); ProductionItem.tick keeps the queue frozen until isBuilt
        this.allowProdWhileBuilding = true;
    }

    @Nullable
    public BlockPos getFinalRallyPoint() {
        if (!this.rallyPoints.isEmpty())
            return this.rallyPoints.get(this.rallyPoints.size() - 1);
        else
            return null;
    }

    public ArrayList<BlockPos> getRallyPoints() {
        return rallyPoints;
    }

    public LivingEntity getRallyPointEntity() {
        if (this.rallyPointEntity == null)
            return null;
        if (!this.rallyPointEntity.isAlive()) {
            this.rallyPointEntity = null;
            return null;
        }
        return this.rallyPointEntity;
    }

    public void setRallyPoint(BlockPos rallyPoint) {
        this.rallyPoints = new ArrayList<>();
        if (!canSetRallyPoint())
            return;
        if (!isPosInsideBuilding(rallyPoint))
            this.rallyPoints.add(rallyPoint);
        this.rallyPointEntity = null;
        rallyResourceName = ResourceSources.getBlockResourceName(rallyPoint, level);
    }

    public void addRallyPoint(BlockPos rallyPoint) {
        if (!canSetRallyPoint())
            return;
        if (!isPosInsideBuilding(rallyPoint))
            this.rallyPoints.add(rallyPoint);
        this.rallyPointEntity = null;
        rallyResourceName = ResourceSources.getBlockResourceName(rallyPoint, level);
    }

    public boolean canSetRallyPoint() {
        return ((ProductionBuilding) getBuilding()).canSetRallyPoint;
    }

    public void setRallyPointEntity(LivingEntity entity) {
        if (!canSetRallyPoint() || entity == null || !entity.isAlive())
            return;
        this.rallyPointEntity = entity;
        this.rallyPoints.clear();
        attackRally = isRallyEntityAttackable();
    }

    private boolean isRallyEntityAttackable() {
        return rallyPointEntity != null && rallyPointEntity.isAlive() &&
                (ResourceSources.isHuntableAnimal(rallyPointEntity) ||
                (rallyPointEntity instanceof Unit unit1 && !AlliancesServerEvents.isAlliedOrOwned(unit1.getOwnerName(), ownerName)));
    }

    private boolean isProducing() {
        return !this.productionQueue.isEmpty();
    }
    // start with the centre pos then go down and look at adjacent blocks until we reach a non-solid block
    public BlockPos getIndoorSpawnPoint(Level level) {
        ProductionBuilding building = (ProductionBuilding) getBuilding();
        return building.getIndoorSpawnPoint(level, this);
    }

    // start with the centre pos then go down and look at adjacent blocks until we reach a non-solid block
    public BlockPos getDefaultOutdoorSpawnPoint() {
        ProductionBuilding building = (ProductionBuilding) getBuilding();
        return building.getDefaultOutdoorSpawnPoint(getMinCorner(blocks), this);
    }

    public Entity produceUnit(ServerLevel level, EntityType<? extends Unit> entityType, String ownerName, boolean spawnIndoors) {
        return produceUnit(level, entityType, ownerName, spawnIndoors, new Vec3i(0,0,0));
    }

    public Entity produceUnit(ServerLevel level, EntityType<? extends Unit> entityType, String ownerName, boolean spawnIndoors, Vec3i spawnOffset) {
        ExitProbe probe = EXIT_PROBE.get();
        if (probe != null) {   // exitSpec's dry run: note how this item spawns, place nothing
            probe.found = true;
            probe.indoors = spawnIndoors;
            probe.offset = spawnOffset;
            probe.type = entityType;
            return null;
        }
        BlockPos spawnPoint = spawnIndoors ? getIndoorSpawnPoint(level) : outdoorSpawnPoint();

        spawnPoint = spawnPoint.offset(spawnOffset);
        BlockPos safe = spawnCell(level, spawnPoint, entityType, spawnOffset, false);
        boolean roof = safe == null;
        if (roof) {
            safe = roofSpawnCell();
            com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("No spawn cell fits a {} within 10 blocks of {} at {}; spawning it on the roof",
                    EntityType.getKey(entityType), getBuilding().name, originPos.toShortString());
        }
        spawnPoint = safe;

        Entity entity = entityType.spawn(level, (CompoundTag) null,
                null,
                spawnPoint,
                MobSpawnType.SPAWNER,
                true,
                false
        );
        // EntityType.spawn hands back an entity that is NOT in the world when a FinalizeSpawn handler (any mod's)
        // cancelled the spawn: the old code set its owner and counted it, and the player paid for nothing
        if (entity == null || entity.isRemoved() || (entity instanceof net.minecraft.world.entity.Mob mob && mob.isSpawnCancelled())) {
            if (entity != null && !entity.isRemoved())
                entity.discard();
            onSpawnFailed(entityType, "the spawn was cancelled");
            return null;
        }
        if (entity instanceof Unit unit) {
            unit.setOwnerName(ownerName);
            unit.setupEquipmentAndUpgradesServer();
            // factory guard: an own builder's first order is to guard (assist) this factory, not the rally point
            if (factoryGuard && unit instanceof WorkerUnit && this.ownerName.equals(ownerName)) {
                com.solegendary.reignofnether.unit.GuardServerEvents.setGuardBuilding(unit, this);
                if (unit.getMoveGoal() != null)
                    unit.getMoveGoal().stopMoving();   // it spawned beside the factory: assist from there, not its centre
            } else
                setDelayedRally(unit);
            recordUnitProduced(ownerName, entity);
            watchSpawn(entity);
            if (roof)
                entity.getPersistentData().putBoolean(com.solegendary.reignofnether.unit.UnitUnstick.KEY_ROOF_SPAWN, true);
        }
        return entity;
    }

    /** Where an outdoor unit is raised: beside the footprint nearest the rally point (or rally entity), else the corner. */
    private BlockPos outdoorSpawnPoint() {
        int offset = (int) ((ProductionBuilding) getBuilding()).spawnRadiusOffset;
        if (!rallyPoints.isEmpty())
            return getClosestGroundPos(rallyPoints.get(0), offset);
        if (rallyPointEntity != null)
            return getClosestGroundPos(rallyPointEntity.getOnPos(), offset);
        return getDefaultOutdoorSpawnPoint();
    }

    /**
     * A cell outside the footprint for a unit whose cell inside has no way out: {@code outdoor} (produceUnit: the rally side),
     * then the middle of each side nearest it - the first the body fits in, outside the footprint, that it can walk
     * away from. Null if there is none.
     */
    @Nullable
    private BlockPos outsideSpawnPoint(ServerLevel level, EntityType<?> type, BlockPos outdoor, Vec3i spawnOffset) {
        BlockPos wanted = outdoor.offset(spawnOffset);
        List<BlockPos> candidates = new ArrayList<>(5);
        candidates.add(wanted);
        candidates.addAll(sideSpawnPoints(type, wanted));
        for (BlockPos c : candidates) {
            BlockPos s = safeSpawnPoint(level, c, type);
            if (s != null && !BuildingUtils.isPosInsideAnyBuilding(false, s)
                    && com.solegendary.reignofnether.unit.UnitUnstick.leavesFootprint(level, s, minCorner, maxCorner))
                return s;
        }
        return null;
    }

    // a harbour's ship is raised in water on purpose; the ground-move rules of leavesFootprint are not its rules
    private static boolean inWater(ServerLevel level, BlockPos cell) {
        return com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level, cell.getX(), cell.getY(),
            cell.getZ()) == com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.KIND_WATER;
    }

    /**
     * The cell produceUnit puts a body of this type in for a wanted spawn point (the exit arrow asks the same): the
     * spawn safety net's cell; for a wide body (the War Mammoth is 3.3 across) that fits nowhere near the corner or the
     * rally side, the middle of each side of the footprint, nearest to where it was wanted first. Null when nothing
     * within reach fits it at all (produceUnit then uses {@link #roofSpawnCell()}).
     */
    @Nullable
    public BlockPos fittingSpawnCell(ServerLevel level, BlockPos spawnPoint, EntityType<?> entityType) {
        BlockPos safe = safeSpawnPoint(level, spawnPoint, entityType);
        if (safe == null) {
            for (BlockPos side : sideSpawnPoints(entityType, spawnPoint)) {
                safe = safeSpawnPoint(level, side, entityType);
                if (safe != null)
                    break;
            }
        }
        return safe;
    }

    /**
     * The cell produceUnit raises a body in (the exit arrow asks the same, so the two cannot drift): fittingSpawnCell,
     * moved outside when that cell has no way out of the building. Null when nothing fits (then the roof). The arrow
     * ({@code forArrow}) shows the spawn with no rally set (exit_arrow_ac4) and logs nothing.
     */
    @Nullable
    private BlockPos spawnCell(ServerLevel level, BlockPos spawnPoint, EntityType<?> entityType, Vec3i spawnOffset, boolean forArrow) {
        BlockPos safe = fittingSpawnCell(level, spawnPoint, entityType);
        if (safe != null && !inWater(level, safe)
                && !com.solegendary.reignofnether.unit.UnitUnstick.leavesFootprint(level, safe, minCorner, maxCorner)) {
            // "stuck units in the graveyard" (lovish, Oct 22 20:23): a cell inside the building with no way out of it.
            // The Graveyard raises its units in a walled yard whose one gate, two blocks wide, any building placed
            // flush against it (buildings may touch) or a 2-block bank outside it closes off; the yard is too big for
            // the pocket rule above or UnitUnstick to call it a trap, so every unit stood in it for good
            // (GraveyardExitGameTests). Interiors are allowed, trapped units are not (design/LANES.md, Oct 20): it is
            // raised outside instead, on the rally side.
            BlockPos out = outsideSpawnPoint(level, entityType, forArrow ? getDefaultOutdoorSpawnPoint() : outdoorSpawnPoint(), spawnOffset);
            if (out != null)
                safe = out;
            else if (!forArrow)
                com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("A {} raised in {} at {} has no way out of it, and no cell outside fits it",
                        EntityType.getKey(entityType), getBuilding().name, originPos.toShortString());
        }
        return safe;
    }

    /**
     * Last resort: on top of the building, where a unit is at least alive and visible, flagged for UnitUnstick to bring
     * down to the nearest fitting ground cell at its first check.
     */
    public BlockPos roofSpawnCell() {
        return new BlockPos(centrePos.getX(), maxCorner.getY() + 1, centrePos.getZ());
    }

    /**
     * Fallback spawn cells for a wide body: the middle of each of the footprint's four sides, far enough out that the
     * body clears the wall, at the building's standing level, nearest to {@code wanted} first. The corner the default
     * spawn point names is the worst cell for a wide body (both walls of the corner are inside it).
     */
    private List<BlockPos> sideSpawnPoints(EntityType<?> type, BlockPos wanted) {
        int out = (int) Math.ceil(type.getWidth() / 2f) + 1;
        int y = minCorner.getY() + 1;
        List<BlockPos> sides = new ArrayList<>(4);
        sides.add(new BlockPos(minCorner.getX() - out, y, centrePos.getZ()));
        sides.add(new BlockPos(maxCorner.getX() + out, y, centrePos.getZ()));
        sides.add(new BlockPos(centrePos.getX(), y, minCorner.getZ() - out));
        sides.add(new BlockPos(centrePos.getX(), y, maxCorner.getZ() + out));
        sides.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(wanted)));
        return sides;
    }

    // ------------------------------------------------------------------ the exit arrow
    // Tester request (2026-10-26): "all structures should have an arrow that shows where the units they produce comes
    // out of". The arrow's spot is computed by the same calls produceUnit makes (getIndoorSpawnPoint or
    // getDefaultOutdoorSpawnPoint, the item's spawn offset, safeSpawnPoint for its body), for the building's primary
    // item - its first unit item - with no rally set (the rally moves outdoor spawns; the arrow marks the exit).

    /** How a building's primary unit item spawns: indoors or at the outdoor spawn point, offset, which body. */
    public record ExitSpec(boolean indoors, Vec3i offset, EntityType<? extends Unit> type, ProductionItem item) { }

    private static final class ExitProbe {
        boolean found;
        boolean indoors;
        Vec3i offset;
        EntityType<? extends Unit> type;
    }

    private static final ThreadLocal<ExitProbe> EXIT_PROBE = new ThreadLocal<>();
    /** Per building type (the spec depends only on its production items); empty = it produces no units. */
    private static final java.util.Map<Building, java.util.Optional<ExitSpec>> EXIT_SPECS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The spawn rule of this building's primary unit item, found by a dry run of the item's real onComplete: produceUnit
     * records its arguments and returns at once (nothing is spawned, paid or counted). Null if the building produces no
     * units through produceUnit (research-only labs, hero-only items, custom buildings).
     */
    @Nullable
    public ExitSpec exitSpec(ServerLevel serverLevel) {
        if (!(getBuilding() instanceof ProductionBuilding pb))
            return null;
        java.util.Optional<ExitSpec> cached = EXIT_SPECS.get(pb);
        if (cached != null)
            return cached.orElse(null);
        ExitSpec spec = null;
        ExitProbe probe = new ExitProbe();
        EXIT_PROBE.set(probe);
        try {
            for (ProductionItem item : pb.productions.get()) {
                if (!(item instanceof IUnitProductionItem) || item.onComplete == null)
                    continue;
                probe.found = false;
                item.onComplete.accept(serverLevel, this);
                if (probe.found) {
                    spec = new ExitSpec(probe.indoors, probe.offset, probe.type, item);
                    break;
                }
            }
        } finally {
            EXIT_PROBE.remove();
        }
        EXIT_SPECS.put(pb, java.util.Optional.ofNullable(spec));
        return spec;
    }

    /**
     * The nominal exit cell for a spawn rule: the building's own rotation-aware spawn point plus the item's offset,
     * before the safety net. Needs only a Level, so a client works out a ghost's cell from the spec the server sent.
     */
    public BlockPos exitNominalPos(Level level, boolean indoors, Vec3i offset) {
        return (indoors ? getIndoorSpawnPoint(level) : getDefaultOutdoorSpawnPoint()).offset(offset);
    }

    /**
     * Where produceUnit would put this building's primary unit with no rally set: the nominal cell and, when
     * {@code safe}, the spawn safety net's cell for the item's body against the world as it is now - produceUnit's own
     * fittingSpawnCell, then the roof - for a placed building (a ghost's blocks are not in the world yet).
     */
    public BlockPos exitArrowPos(ServerLevel serverLevel, ExitSpec spec, boolean safe) {
        BlockPos p = exitNominalPos(serverLevel, spec.indoors(), spec.offset());
        if (!safe)
            return p;
        BlockPos cell = spawnCell(serverLevel, p, spec.type(), spec.offset(), true);
        return cell != null ? cell : roofSpawnCell();
    }

    /** The safe exit cell, only for {@code senderName}'s own building (a network request is untrusted); else null. */
    @Nullable
    public BlockPos exitArrowCellFor(ServerLevel serverLevel, String senderName, ExitSpec spec) {
        return senderName != null && senderName.equals(ownerName) ? exitArrowPos(serverLevel, spec, true) : null;
    }

    // ------------------------------------------------------------------ the spawn safety net
    // lovish (Horde, Repeat on his Fortress): "the mammoth unit and other units bugged when being made - sometimes
    // they wouldn't spawn". Production completed and the cost was spent, but nothing the player could see appeared
    // and nothing was logged. Whatever the cause, it is never silent again: a unit that was not placed, or is gone
    // within a second of being placed for any reason but a fight, is refunded and reported.

    /** How long after spawning a unit must still be alive, or its cost is refunded. */
    public static final int SPAWN_WATCH_TICKS = 20;

    /** The item being completed right now (set around ProductionItem.tick), so a failed spawn knows what to refund. */
    @Nullable
    private ActiveProduction completing;

    private record SpawnWatch(Entity entity, EntityType<?> type, float metal, float energy, int emerald, int ticksLeft) { }
    private final List<SpawnWatch> spawnWatches = new ArrayList<>();

    private void watchSpawn(Entity entity) {
        if (completing == null)
            return;   // not a production (a wave portal, a scenario spawn): nothing was paid for it
        int emerald = completing.item.getCost(false, ownerName).emerald;
        spawnWatches.add(new SpawnWatch(entity, entity.getType(), completing.metalSpent, completing.energySpent, emerald, SPAWN_WATCH_TICKS));
    }

    private void tickSpawnWatches() {
        if (spawnWatches.isEmpty())
            return;
        for (int i = spawnWatches.size() - 1; i >= 0; i--) {
            SpawnWatch w = spawnWatches.get(i);
            if (w.ticksLeft() > 0) {
                spawnWatches.set(i, new SpawnWatch(w.entity(), w.type(), w.metal(), w.energy(), w.emerald(), w.ticksLeft() - 1));
                continue;
            }
            spawnWatches.remove(i);
            Entity e = w.entity();
            if (e.isAlive() && !e.isRemoved())
                continue;
            if (e instanceof LivingEntity le && le.getLastDamageSource() != null && le.getLastDamageSource().getEntity() != null)
                continue;   // killed in a fight at the door: that is the game, not a bug
            refundSpawn(w.type(), w.metal(), w.energy(), w.emerald(), e.isRemoved() ? "it was removed" : "it died");
        }
    }

    private void onSpawnFailed(EntityType<?> type, String why) {
        if (completing == null) {
            com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("{} could not be placed at {} {}: {}",
                    EntityType.getKey(type), getBuilding().name, originPos.toShortString(), why);
            return;
        }
        refundSpawn(type, completing.metalSpent, completing.energySpent, completing.item.getCost(false, ownerName).emerald, why);
    }

    private void refundSpawn(EntityType<?> type, float metal, float energy, int emerald, String why) {
        com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("{} could not be placed at {} {} ({}); refunding {}m {}e {}em to {}",
                EntityType.getKey(type), getBuilding().name, originPos.toShortString(), why,
                Math.round(metal), Math.round(energy), emerald, ownerName);
        EconomyServerEvents.refund(ownerName, metal, energy);
        if (emerald > 0)
            ResourcesServerEvents.addSubtractResources(Resources.emeralds(ownerName, emerald));
        com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToPlayer(ownerName,
                "server.reignofnether.unit_not_placed", false, type.getDescription().getString(), getBuilding().name);
    }

    /** For tests: spawns still being watched by this building. */
    public int spawnWatchCount() {
        return spawnWatches.size();
    }

    /**
     * Results-screen scores for one unit that just spawned for {@code ownerName}: every unit counts once, and it is a
     * worker or military by what it is (WorkerUnit), not by a hard-coded list of RoN worker items. Research never
     * passes through here, so it no longer inflates the unit counts.
     */
    public static void recordUnitProduced(String ownerName, Entity entity) {
        if (!(entity instanceof Unit) || ownerName == null || ownerName.isEmpty())
            return;
        com.solegendary.reignofnether.player.RTSPlayer rtsPlayer =
                com.solegendary.reignofnether.player.PlayerServerEvents.getRTSPlayer(ownerName);
        if (rtsPlayer == null)
            return;
        rtsPlayer.scores.addToScore(com.solegendary.reignofnether.player.RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED);
        rtsPlayer.scores.addToScore(entity instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit
                ? com.solegendary.reignofnether.player.RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED
                : com.solegendary.reignofnether.player.RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED);
    }

    /**
     * The default indoor spawn point is the footprint's centre, walked down only while the block below is air - so in a
     * building with something solid in the middle it lands INSIDE it: the Heartwood Hall's 3x3 oak trunk (every
     * Seedshaper it trained was entombed), and on top of the Circle of Elders' / Heart of the Wild's moonwell cauldron
     * (the unit drops into the bowl and can't step out). If the pathfinder can't stand a unit on the chosen cell, or
     * can but could never walk it out of there (the centre of a Celestial Observatory lands on the first roof, INSIDE
     * the glass of the tier above; a hollow between a rim and a pillar), take the nearest cell it can stand on and
     * leave from (UnitUnstick.isPocketed). Water (a harbour's ship spawns) is left alone.
     * <p>
     * The outdoor spawn point and the rally spawn point name a cell at the building's FLOOR level - the solid ground
     * block - and the ring search scored a cave or a ledge under the lab the same as the ground 1 above (dy -3 and
     * dy +1 both scored 3, and down was tried first): lovish's units "never spawned" because they were put under the
     * base. So the ground on top of a named floor block, and the ground under a cell named in the air (a slope's
     * downhill side), are taken first ({@link #surfaceCell}), and the rings are searched one level up or down before
     * the wider window.
     */
    public static BlockPos safeSpawnPoint(ServerLevel level, BlockPos spawnPoint) {
        byte kind = com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level, spawnPoint.getX(),
                spawnPoint.getY(), spawnPoint.getZ());
        if (com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.isWater(kind))
            return spawnPoint;
        if (kind != com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.KIND_BLOCKED
                && !com.solegendary.reignofnether.unit.UnitUnstick.isPocketed(level, spawnPoint))
            return spawnPoint;
        BlockPos surface = surfaceCell(level, spawnPoint, 8);
        if (surface != null && !com.solegendary.reignofnether.unit.UnitUnstick.isPocketed(level, surface))
            return surface;
        BlockPos safe = null;
        // a named floor block or a cell in the air told us the ground level: that level first, one up or down. A named
        // cell that is standable but pocketed (the Jade Pagoda's centre lands on a closed tier) gets the full window:
        // the ground 2-3 under a tier is the right answer there, not the tier beside it
        if (surface != null && !surface.equals(spawnPoint))
            safe = com.solegendary.reignofnether.unit.UnitUnstick.findEscapePos(level, surface, 10, 1, 1, 0f, 0f);
        if (safe == null)
            safe = com.solegendary.reignofnether.unit.UnitUnstick.findEscapePos(level, spawnPoint, 10, 8, 3, 0f, 0f);
        if (safe == null)   // a spawn point sunk into the ground (the Central Portal's is 5 under its floor): the surface
            safe = com.solegendary.reignofnether.unit.UnitUnstick.findEscapePos(level, spawnPoint, 10, 8, 8, 0f, 0f);
        if (safe == null)   // nowhere to leave from within reach: at least somewhere to stand
            safe = com.solegendary.reignofnether.unit.UnitUnstick.findStandPos(level, spawnPoint, 10, 8, 3, null);
        return safe != null ? safe : spawnPoint;
    }

    /**
     * The cell a unit stands in at this column's surface, when it is the obvious one: {@code cell} itself if standable;
     * the cell above a named floor block (a solid cell whose top is the ground); or, from a cell named in the air (a
     * spawn point on a slope's downhill side), the first standable cell up to {@code down} below it. Null otherwise
     * (a cell inside a wall or a plinth, a sheer drop): the ring search decides those.
     */
    @Nullable
    public static BlockPos surfaceCell(ServerLevel level, BlockPos cell, int down) {
        if (com.solegendary.reignofnether.unit.UnitUnstick.isStandable(level, cell))
            return cell;
        if (com.solegendary.reignofnether.unit.UnitUnstick.bodyBlocked(level, cell))
            return com.solegendary.reignofnether.unit.UnitUnstick.isStandable(level, cell.above()) ? cell.above() : null;
        for (int dy = 1; dy <= down; dy++) {
            BlockPos c = cell.below(dy);
            if (com.solegendary.reignofnether.unit.UnitUnstick.isStandable(level, c))
                return c;
            if (com.solegendary.reignofnether.unit.UnitUnstick.bodyBlocked(level, c))
                return null;   // landed on something that can't be stood on (a fence, a canopy)
        }
        return null;
    }

    /**
     * {@link #safeSpawnPoint(ServerLevel, BlockPos)} for a body of this type: a unit wider than a block (the Leviathan
     * and the World Tree Walker are 3.5 wide, the War Mammoth 3.3) also needs its whole hitbox clear where it lands -
     * one standable cell beside a lab's wall would still bury half of it - so if it doesn't fit, take the nearest
     * standable cell where it does, and NEVER a cell where it doesn't: null when nothing within 10 blocks fits (the
     * caller tries the building's other sides, then its roof). Narrow bodies keep the one-cell rule and never get null.
     */
    @Nullable
    public static BlockPos safeSpawnPoint(ServerLevel level, BlockPos spawnPoint, @javax.annotation.Nullable EntityType<?> type) {
        if (type == null || type.getWidth() <= 1.0f)
            return safeSpawnPoint(level, spawnPoint);
        float w = type.getWidth(), h = type.getHeight();
        // where it was asked for, if it fits there (a harbour's ship spawn in open water is one of these)
        if (com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level, spawnPoint.getX(),
                spawnPoint.getY(), spawnPoint.getZ()) != com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.KIND_BLOCKED
                && com.solegendary.reignofnether.unit.UnitUnstick.bodyFits(level, spawnPoint, w, h)
                && !com.solegendary.reignofnether.unit.UnitUnstick.isPocketed(level, spawnPoint))
            return spawnPoint;
        BlockPos surface = surfaceCell(level, spawnPoint, 8);
        if (surface != null && com.solegendary.reignofnether.unit.UnitUnstick.bodyFits(level, surface, w, h)
                && !com.solegendary.reignofnether.unit.UnitUnstick.isPocketed(level, surface))
            return surface;
        // the same level first; then down 8 but up only 1: a wide body must land on the floor beside the lab, never
        // perched on a wall top or a roof edge (its hitbox hangs over the air beside those and "fits"). Beside raised
        // ground (up to 3 higher than the named floor) the search is run again from the top of that ground.
        BlockPos from = surface != null ? surface : spawnPoint;
        BlockPos safe = null;
        if (surface != null && !surface.equals(spawnPoint))   // the ground level is known: that level first
            safe = com.solegendary.reignofnether.unit.UnitUnstick.findEscapePos(level, surface, 10, 1, 1, w, h);
        if (safe == null)
            safe = com.solegendary.reignofnether.unit.UnitUnstick.findEscapePos(level, from, 10, 8, 1, w, h);
        if (safe == null) {
            BlockPos top = groundAbove(level, spawnPoint, 3);
            if (top != null)
                safe = com.solegendary.reignofnether.unit.UnitUnstick.findEscapePos(level, top, 10, 1, 1, w, h);
        }
        if (safe == null)
            safe = com.solegendary.reignofnether.unit.UnitUnstick.findStandPos(level, from, 10, 8, 1, w, h);
        return safe;
    }

    /** The first standable cell above a cell inside the ground, up to {@code up} above it; null if there is none. */
    @Nullable
    private static BlockPos groundAbove(ServerLevel level, BlockPos cell, int up) {
        for (int dy = 1; dy <= up; dy++)
            if (com.solegendary.reignofnether.unit.UnitUnstick.isStandable(level, cell.above(dy)))
                return cell.above(dy);
        return null;
    }

    protected void setDelayedRally(Unit unit) {
        Entity entity = (Entity) unit;
        LivingEntity rallyEntity = getRallyPointEntity();
        BlockPos defaultRallyPoint = getDefaultOutdoorSpawnPoint();
        final List<BlockPos> fRallyPoints = this.rallyPoints.isEmpty() ? List.of(defaultRallyPoint) : this.rallyPoints;

        if (rallyEntity != null && rallyEntity.isAlive()) {
            if (isRallyEntityAttackable()) {
                TaskSchedulerServerEvents.schedule(10, () -> {
                    if (!fRallyPoints.isEmpty()) {
                        UnitServerEvents.addActionItem(
                                this.ownerName,
                                UnitAction.ATTACK,
                                rallyEntity.getId(),
                                new int[] { entity.getId() },
                                fRallyPoints.get(0),
                                new BlockPos(0,0,0)
                        );
                    }
                });
            } else {
                TaskSchedulerServerEvents.schedule(10, () -> {
                    if (!fRallyPoints.isEmpty()) {
                        UnitServerEvents.addActionItem(
                                this.ownerName,
                                UnitAction.FOLLOW,
                                rallyEntity.getId(),
                                new int[] { entity.getId() },
                                fRallyPoints.get(0),
                                new BlockPos(0,0,0)
                        );
                    }
                });
            }
        } else {
            for (int i = 0; i < fRallyPoints.size(); i++) {
                final int fi = i;
                TaskSchedulerServerEvents.schedule(10 * fi, () -> {
                    if (fRallyPoints.size() > fi)
                        UnitServerEvents.addActionItem(
                                this.ownerName,
                                attackRally ? UnitAction.ATTACK_MOVE : UnitAction.MOVE,
                                -1,
                                new int[] { entity.getId() },
                                fRallyPoints.get(fi),
                                new BlockPos(0,0,0),
                                fi > 0
                        );
                });
                TaskSchedulerServerEvents.schedule(15, () -> {
                    if (!attackRally && unit instanceof WorkerUnit workerUnit)
                        if (rallyResourceName != ResourceName.NONE)
                            workerUnit.getGatherResourceGoal().setTargetResourceName(rallyResourceName);
                });
            }
        }
    }

    /** Whether this owner already has {@code item} recorded as researched (heroes and other non-research items never are). */
    private boolean alreadyResearched(ProductionItem item) {
        var key = com.solegendary.reignofnether.api.ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item);
        return key != null && com.solegendary.reignofnether.research.ResearchServerEvents.recordedCount(ownerName, key) > 0;
    }

    // return true if successful
    public boolean startProductionItem(ProductionItem prodItem) {
        return startProductionItem(prodItem, -1);
    }

    // if ticksLeft <= 0, just use default cost ticks
    public boolean startProductionItem(ProductionItem prodItem, float ticksLeft) {
        boolean success = false;

        if (getBuilding() instanceof ProductionBuilding pb && !pb.productions.get().contains(prodItem)) {
            return false;
        }

        if (prodItem != null) {
            // only worry about checking affordability on serverside
            if (getLevel().isClientSide()) {
                ActiveProduction activeProduction = new ActiveProduction(prodItem, true, ownerName);
                if (ticksLeft > 0)
                    activeProduction.ticksLeft = ticksLeft;
                productionQueue.add(activeProduction);
                success = true;
            }
            else {
                boolean allow = switch (prodItem.dupeRule) {
                    // one-off, player-wide: refused while queued at ANY of the owner's buildings, and once it is done.
                    // The queue test alone let a second Tier 2 start after the first finished (the client only hides
                    // the button; a stale card, a double press, the bot or a raw packet got through): tester report.
                    case DISALLOW -> !prodItem.itemIsBeingProduced(false, ownerName) && !alreadyResearched(prodItem);
                    case DISALLOW_FOR_BUILDING -> !prodItem.itemIsBeingProducedAt(false, this);
                    case ALLOW -> true;
                };

                if (decommissioning) {
                    // BuildingReclaim.start refunds the queue, but the batch command card (and a stale client card)
                    // could queue again: the lab would drain metal into units it never finishes (CR-01)
                    return false;
                }
                else if (productionQueue.size() >= MAX_QUEUE) {
                    HudClientboundPacket.showTempMessageI18n(ownerName, "hud.reignofnether.queue_full");
                }
                else if (com.solegendary.reignofnether.resources.TierGate.productionError(this, prodItem) != null) {
                    // a T2 lab, or the extractor refit, without Tier 2 (resources/TierGate): the same on all seven factions
                    HudClientboundPacket.showTempMessageI18n(ownerName,
                        com.solegendary.reignofnether.resources.TierGate.productionError(this, prodItem));
                }
                else if (!prodItem.canProduce(this)) {
                    String errorMsg = prodItem.getProduceErrorMsg(this);
                    if (errorMsg != null) {
                        HudClientboundPacket.showTempMessageI18n(ownerName, errorMsg);
                    }
                }
                else if (allow && prodItem.canAfford(this)) {
                    // BAR flow economy: metal and energy are drained while the item is produced (see
                    // ProductionItem.tick), so queueing is allowed with 0 resources. Only emeralds are paid upfront.
                    ActiveProduction activeProduction = new ActiveProduction(prodItem, false, ownerName);
                    productionQueue.add(activeProduction);
                    int emeraldCost = prodItem.getCost(level.isClientSide(), ownerName).emerald;
                    if (emeraldCost > 0)
                        ResourcesServerEvents.addSubtractResources(Resources.emeralds(ownerName, -emeraldCost));
                    success = true;
                }
                else if (!ResourcesServerEvents.canAfford(ownerName, ResourceName.EMERALD, prodItem.getCost(level.isClientSide(), ownerName).emerald)) {
                    // only emeralds are still paid upfront; metal and energy never block queueing, and there is no
                    // population limit (lovish, Oct 29)
                    ResourcesClientboundPacket.warnInsufficientResources(ownerName, true, true, true, false);
                }
            }
        }
        // DISALLOW_FOR_BUILDING items are likely building upgrades, so switch focus to an unupgraded building without any upgrade in progress
        if (success && level.isClientSide() && prodItem.dupeRule == ProdDupeRule.DISALLOW_FOR_BUILDING) {
            for (BuildingPlacement bpl : BuildingClientEvents.getSelectedBuildings()) {
                if (bpl.getBuilding() == this.getBuilding() && bpl != this && bpl instanceof ProductionPlacement ppl &&
                    !ppl.productionQueue.stream().map(ap -> ap.item.dupeRule).toList().contains(ProdDupeRule.DISALLOW_FOR_BUILDING) &&
                    ppl.getUpgradeLevel() == 0) {
                    HudClientEvents.hudSelectedPlacement = bpl;
                    break;
                }
            }
        }
        return success;
    }

    public boolean cancelProductionItem(ProductionItem item, boolean frontItem) {
        boolean success = false;

        if (!productionQueue.isEmpty()) {
            if (frontItem) {
                ActiveProduction prodItem = productionQueue.get(0);
                productionQueue.remove(0);
                if (!getLevel().isClientSide())
                    refundProduction(prodItem);
                success = true;
            }
            else {
                // find first non-started item to remove
                for (int i = 0; i < productionQueue.size(); i++) {
                    ActiveProduction prodItem = productionQueue.get(i);
                    if (prodItem.item.equals(item) &&
                            prodItem.ticksLeft >= prodItem.item.getCost(level.isClientSide(), ownerName).ticks) {
                        productionQueue.remove(prodItem);
                        if (!getLevel().isClientSide())
                            refundProduction(prodItem);
                        success = true;
                        break;
                    }
                }
            }
        }
        return success;
    }

    // BAR flow economy: refund only what was actually spent on the item so far (+ any upfront emerald cost)
    private void refundProduction(ActiveProduction prodItem) {
        EconomyServerEvents.refund(ownerName, prodItem.metalSpent, prodItem.energySpent);
        int emeraldCost = prodItem.item.getCost(false, ownerName).emerald;
        if (emeraldCost > 0)
            ResourcesServerEvents.addSubtractResources(Resources.emeralds(ownerName, emeraldCost));
        prodItem.metalSpent = 0;
        prodItem.energySpent = 0;
    }

    @Override
    public void destroy(ServerLevel serverLevel) {
        super.destroy(serverLevel);
        while (!productionQueue.isEmpty()) {
            cancelProductionItem(productionQueue.get(0).item, true);
        }
    }

    public void tick(Level tickLevel) {
        super.tick(tickLevel);

        if (!tickLevel.isClientSide())
            tickSpawnWatches();
        if (!productionQueue.isEmpty()) {
            ActiveProduction nextItem = productionQueue.get(0);
            completing = nextItem;   // ProductionItem.tick completes it (onComplete -> produceUnit) from in here
            boolean done;
            try {
                done = nextItem.item.tick(this, nextItem);
            } finally {
                completing = null;
            }
            if (done) {
                if (!tickLevel.isClientSide()) {
                    productionQueue.remove(0);
                    BuildingProductionClientboundPacket.completeProduction(this.ownerName, this.originPos, nextItem.item.getItemName());
                    if (repeatQueue && nextItem.item.dupeRule == com.solegendary.reignofnether.building.production.ProdDupeRule.ALLOW
                            && startProductionItem(nextItem.item))
                        BuildingProductionClientboundPacket.startProduction(this.ownerName, this.originPos, nextItem.item);
                }
            }
        }
    }

    @Override
    public void updateButtons() {
        if (level.isClientSide()) {
            super.updateButtons();
            if (getBuilding() instanceof ProductionBuilding productionBuilding) {
                productionButtons = productionBuilding.productions.getButtons(this);
            }
        }
    }

    @Override
    protected boolean checkAndDoCapture(ServerLevel serverLevel) {
        String oldOwner = this.ownerName;
        boolean captured = super.checkAndDoCapture(serverLevel);
        String newOwner = this.ownerName;
        if (captured) {
            this.ownerName = oldOwner;
            for (ActiveProduction activeProd : new ArrayList<>(productionQueue)) {
                cancelProductionItem(activeProd.item, true);
            }
            BuildingProductionClientboundPacket.clearQueue(this.ownerName, this.originPos);
            this.ownerName = newOwner;
        }
        return captured;
    }
}
