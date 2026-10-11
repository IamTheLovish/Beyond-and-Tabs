package com.solegendary.reignofnether.building.buildings.placements;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

public class ProductionPlacement extends BuildingPlacement {
    protected ArrayList<BlockPos> rallyPoints = new ArrayList<>();
    protected LivingEntity rallyPointEntity;
    public List<Button> productionButtons;
    public final List<ActiveProduction> productionQueue = new ArrayList<>();
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
    public boolean attackRally = false;
    public ResourceName rallyResourceName = ResourceName.NONE; // used to set worker resource targets

    public ProductionPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName, ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
        if (building instanceof ProductionBuilding productionBuilding) {
            productionButtons = productionBuilding.productions.getButtons(this);
        }
        if (isCapitol)
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
    public BlockPos getIndoorSpawnPoint(ServerLevel level) {
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
        ProductionBuilding building = (ProductionBuilding) getBuilding();
        BlockPos spawnPoint;
        if (spawnIndoors) {
            spawnPoint = getIndoorSpawnPoint(level);
        }
        else if (!rallyPoints.isEmpty())
            spawnPoint = getClosestGroundPos(rallyPoints.get(0), (int) building.spawnRadiusOffset);
        else if (rallyPointEntity != null)
            spawnPoint = getClosestGroundPos(rallyPointEntity.getOnPos(), (int) building.spawnRadiusOffset);
        else
            spawnPoint = getDefaultOutdoorSpawnPoint();

        spawnPoint = spawnPoint.offset(spawnOffset);
        spawnPoint = safeSpawnPoint(level, spawnPoint, entityType);

        Entity entity = entityType.spawn(level, (CompoundTag) null,
                null,
                spawnPoint,
                MobSpawnType.SPAWNER,
                true,
                false
        );
        if (entity instanceof Unit unit) {
            unit.setOwnerName(ownerName);
            unit.setupEquipmentAndUpgradesServer();
            setDelayedRally(unit);
            recordUnitProduced(ownerName, entity);
        }
        return entity;
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
     * (the unit drops into the bowl and can't step out). If the pathfinder can't stand a unit on the chosen cell, take
     * the nearest cell it can. Water (a harbour's ship spawns) and anything already standable are left alone.
     */
    public static BlockPos safeSpawnPoint(ServerLevel level, BlockPos spawnPoint) {
        if (com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level, spawnPoint.getX(),
                spawnPoint.getY(), spawnPoint.getZ()) != com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.KIND_BLOCKED)
            return spawnPoint;
        BlockPos safe = com.solegendary.reignofnether.unit.UnitUnstick.findStandPos(level, spawnPoint, 10, 8, 3, null);
        return safe != null ? safe : spawnPoint;
    }

    /**
     * {@link #safeSpawnPoint(ServerLevel, BlockPos)} for a body of this type: a unit wider than a block (the Leviathan
     * and the World Tree Walker are 3.5 wide) also needs its whole hitbox clear where it lands - one standable cell
     * beside a lab's wall would still bury half of it - so if it doesn't fit, take the nearest standable cell where it
     * does. Narrow bodies keep the one-cell rule.
     */
    public static BlockPos safeSpawnPoint(ServerLevel level, BlockPos spawnPoint, @javax.annotation.Nullable EntityType<?> type) {
        if (type == null || type.getWidth() <= 1.0f)
            return safeSpawnPoint(level, spawnPoint);
        float w = type.getWidth(), h = type.getHeight();
        if (com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level, spawnPoint.getX(),
                spawnPoint.getY(), spawnPoint.getZ()) != com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.KIND_BLOCKED
                && com.solegendary.reignofnether.unit.UnitUnstick.bodyFits(level, spawnPoint, w, h))
            return spawnPoint;
        // up 1, not 3: a wide body must land on the floor beside the lab, never perched on a wall top or a roof edge
        BlockPos safe = com.solegendary.reignofnether.unit.UnitUnstick.findStandPos(level, spawnPoint, 10, 8, 1, w, h);
        return safe != null ? safe : safeSpawnPoint(level, spawnPoint);
    }

    protected void setDelayedRally(Unit unit) {
        Entity entity = (Entity) unit;
        LivingEntity rallyEntity = getRallyPointEntity();
        BlockPos defaultRallyPoint = getDefaultOutdoorSpawnPoint();
        final List<BlockPos> fRallyPoints = this.rallyPoints.isEmpty() ? List.of(defaultRallyPoint) : this.rallyPoints;

        if (rallyEntity != null && rallyEntity.isAlive()) {
            if (isRallyEntityAttackable()) {
                CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
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
                CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS).execute(() -> {
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
                CompletableFuture.delayedExecutor(500L * fi, TimeUnit.MILLISECONDS).execute(() -> {
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
                CompletableFuture.delayedExecutor(750, TimeUnit.MILLISECONDS).execute(() -> {
                    if (!attackRally && unit instanceof WorkerUnit workerUnit)
                        if (rallyResourceName != ResourceName.NONE)
                            workerUnit.getGatherResourceGoal().setTargetResourceName(rallyResourceName);
                });
            }
        }
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
                    case DISALLOW -> !prodItem.itemIsBeingProduced(false, ownerName);
                    case DISALLOW_FOR_BUILDING -> !prodItem.itemIsBeingProducedAt(false, this);
                    case ALLOW -> true;
                };

                if (productionQueue.size() >= MAX_QUEUE) {
                    HudClientboundPacket.showTempMessageI18n(ownerName, "hud.reignofnether.queue_full");
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
                else {
                    if (!prodItem.isBelowMaxPopulation(this))
                        ResourcesClientboundPacket.warnMaxPopulation(ownerName);
                    else if (!prodItem.canAffordPopulation(this)) {
                        if (prodItem instanceof GraveyardUnitProductionItem && this instanceof GraveyardPlacement gy && gy.getUpgradeLevel() > 0) {
                            ResourcesClientboundPacket.warnFullGraveyard(ownerName);
                        } else {
                            ResourcesClientboundPacket.warnInsufficientPopulation(ownerName);
                        }
                    }
                    else if (!ResourcesServerEvents.canAfford(ownerName, ResourceName.EMERALD, prodItem.getCost(level.isClientSide(), ownerName).emerald))
                        // only emeralds are still paid upfront; metal and energy never block queueing
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

        if (!productionQueue.isEmpty()) {
            ActiveProduction nextItem = productionQueue.get(0);
            if (nextItem.item.tick(this, nextItem)) {
                if (!tickLevel.isClientSide()) {
                    productionQueue.remove(0);
                    BuildingProductionClientboundPacket.completeProduction(this.ownerName, this.originPos, nextItem.item.getItemName());
                    if (repeatQueue && nextItem.item.dupeRule == com.solegendary.reignofnether.building.production.ProdDupeRule.ALLOW
                            && startProductionItem(nextItem.item))
                        BuildingProductionClientboundPacket.startProduction(this.ownerName, this.originPos, nextItem.item.getItemName());
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
