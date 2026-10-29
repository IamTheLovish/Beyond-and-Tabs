package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.google.common.collect.Sets;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.EnchantAbility;
import com.solegendary.reignofnether.ability.BuildingAbilityClientboundPacket;
import com.solegendary.reignofnether.ability.EquipAbility;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.addon.ItemShopAddon;
import com.solegendary.reignofnether.building.addon.NetherConvertingAddon;
import com.solegendary.reignofnether.building.addon.NightSourceAddon;
import com.solegendary.reignofnether.building.buildings.monsters.Dungeon;
import com.solegendary.reignofnether.building.buildings.neutral.NeutralTransportPortal;
import com.solegendary.reignofnether.building.buildings.piglins.FlameSanctuary;
import com.solegendary.reignofnether.building.buildings.placements.*;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.buildings.villagers.Blacksmith;
import com.solegendary.reignofnether.building.buildings.villagers.IronGolemBuilding;
import com.solegendary.reignofnether.building.buildings.villagers.Library;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.building.data.DataType;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.commands.rtsapi.ResourceObjectiveCriteria;
import com.solegendary.reignofnether.entities.AdjustablePrimedTnt;
import com.solegendary.reignofnether.fogofwar.FrozenChunkClientboundPacket;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.perf.Sys;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.monsters.CreeperUnit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;
import com.solegendary.reignofnether.unit.units.villagers.PillagerUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.*;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;

public class BuildingServerEvents {
    
    public static final DataType<Set<String>> BUILDING_TAGS = DataType.createRegistered(
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "building_tags"),
        (tag, server) -> {
            Set<String> tags = Sets.newHashSet();
            if (tag.contains("tags", Tag.TAG_LIST)) {
                ListTag tagList = tag.getList("tags", Tag.TAG_STRING);
                for (int i = 0; i < tagList.size(); i++) {
                    tags.add(tagList.getString(i));
                }
            }
            return tags;
        },
        tags -> {
            CompoundTag nbt = new CompoundTag();
            ListTag tagList = new ListTag();
            for (String tagName : tags) {
                tagList.add(StringTag.valueOf(tagName));
            }
            nbt.put("tags", tagList);
            return nbt;
        },
        Sets::newHashSet
    );
    public static final DataType<ArrayList<BuildingCommand>> BUILDING_COMMANDS = DataType.createRegistered(
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "building_commands"),
        (tag, server) -> {
            ArrayList<BuildingCommand> commands = new ArrayList<>();
            if (tag.contains("commands", Tag.TAG_LIST)) {
                ListTag commandList = tag.getList("commands", Tag.TAG_COMPOUND);
                for (int i = 0; i < commandList.size(); i++) {
                    CompoundTag commandTag = commandList.getCompound(i);
                    commands.add(BuildingCommand.getFromNbt(commandTag));
                }
            }
            return commands;
        },
        commands -> {
            CompoundTag nbt = new CompoundTag();
            ListTag commandList = new ListTag();
            for (BuildingCommand command : commands) {
                CompoundTag commandTag = new CompoundTag();
                commandTag.putInt("tickCooldown", command.tickCooldown);
                commandTag.putInt("tickCooldownMax", command.tickCooldownMax);
                commandTag.putString("commandStr", command.commandStr);
                commandTag.putString("condition", command.condition.name());
                commandList.add(commandTag);
            }
            nbt.put("commands", commandList);
            return nbt;
        },
        ArrayList::new
    );
    private static final int BUILDING_SYNC_TICKS_MAX = 20; // how often we send out unit syncing packets
    private static int buildingSyncTicks = BUILDING_SYNC_TICKS_MAX;

    private static final int TNT_BUILDING_BASE_DAMAGE = 20;
    private static final int MAX_SCAFFOLD_DEPTH = 5;

    private static @Nullable ServerLevel serverLevel = null;

    public static @Nullable ServerLevel getServerLevel() { return serverLevel; }

    // buildings that currently exist serverside (a BuildingList, so the BuildingGrid over it sees every change)
    private static final BuildingList buildings = new BuildingList();
    private static final BuildingGrid buildingGrid = new BuildingGrid(buildings);
    
    public static Object2IntArrayMap<String> populations = new Object2IntArrayMap<>();
    public static final ArrayList<NetherZone> netherZones = new ArrayList<>();

    public static ArrayList<BuildingPlacement> getBuildings() {
        return buildings;
    }

    /** The spatial index of {@link #getBuildings()} (point, box and nearest queries without walking the list). */
    public static BuildingGrid getBuildingGrid() {
        return buildingGrid;
    }

    /** {@code getBuildings().contains(b)}, from the grid's identity index instead of a walk of the list. */
    public static boolean isRegistered(BuildingPlacement b) {
        return BuildingGrid.legacyScans ? buildings.contains(b) : buildingGrid.contains(b);
    }

    public static List<BuildingPlacement> getGarrisonableBuildings() {
        ArrayList<BuildingPlacement> garrs = new ArrayList<>();
        for (BuildingPlacement bpl : buildings)
            if (bpl.getBuilding().hasActiveAddon(GarrisonableBuildingAddon.class))
                garrs.add(bpl);
        return garrs;
    }

    public static final Random random = new Random();

    // buildings and nether zones reach the disk with Minecraft's world save (persistence/RtsSaves, plan 014): the
    // 30 s timer here wrote every saved-data file on the server thread mid-fight

    public static void saveBuildings(ServerLevel level) {
        if (level == null)
            return;
        BuildingSaveData buildingData = BuildingSaveData.getInstance(level);
        buildingData.buildings.clear();

        getBuildings().forEach(b -> {
            b.getDataStorage().setData(BUILDING_TAGS, b.tags);
            b.getDataStorage().setData(BUILDING_COMMANDS, b.commands);
            BuildingReclaim.writeSaveData(b);   // a decommission resumes after a reload (BuildingReclaim)
            b.writeSaveState();                 // subclass state: game ender charge, ward stockpile (R2-01)
            // merged buildings: the per-building switch and the section's complex anchor are saved; the complexes
            // are re-derived after a load, seeded from the anchors so the cut the player built up survives
            if (b.getBuilding().mergeable) {
                b.getDataStorage().setData(com.solegendary.reignofnether.building.merge.MergedBuildings.MERGE_OFF, !b.mergeEnabled);
                b.getDataStorage().setData(com.solegendary.reignofnether.building.merge.MergedBuildings.MERGE_ANCHOR,
                    com.solegendary.reignofnether.building.merge.MergedBuildings.SERVER.anchorOf(b));
            }

            ItemShopAddon itemShopAddon = b.getBuilding().getActiveAddon(ItemShopAddon.class);
            if (itemShopAddon != null) {
                b.getDataStorage().setData(ItemShopAddon.STOCKED_ITEMS, b.getDataStorage().getData(ItemShopAddon.STOCKED_ITEMS));
            }
            
            PortalPlacement.PortalType portalType = null;
            if (b instanceof PortalPlacement portal) {
                portalType = portal.getPortalType();
            }
            if (b instanceof CustomBuildingPlacement cb) {
                cb.packCommandsNbt();
            }
            buildingData.buildings.add(new BuildingSave(b.originPos,
                    level,
                    b.getBuilding(),
                    b.ownerName,
                    b.rotation,
                    b instanceof ProductionPlacement pb ? pb.getFinalRallyPoint() : b.originPos,
                    b.isDiagonalBridge,
                    b.isBuilt,
                    b.getUpgradeLevel(),
                    portalType,
                    b instanceof PortalPlacement portal && portal.hasDestination() ? portal.destination : new BlockPos(0,0,0),
                    b.scenarioRoleIndex,
                    b.getDataStorage(),
                    b.partialBlocksDestroyed,
                    b instanceof CustomBuildingPlacement cb ? cb.commandsNbt : new ListTag()
            ));
            //ReignOfNether.LOGGER.info("saved buildings/nether in serverevents: " + b.originPos);
        });

        // deduplicate saved buildings by removing any additional buildings with the same originPos
        Set<BlockPos> seenOriginPoses = new HashSet<>();
        buildingData.buildings.removeIf(b -> !seenOriginPoses.add(b.originPos));

        buildingData.save();   // dirty only: the world save writes it (persistence/RtsSaves)
    }

    public static void saveNetherZones(ServerLevel level) {
        NetherZoneSaveData netherData = NetherZoneSaveData.getInstance(level);
        netherData.netherZones.clear();
        netherData.netherZones.addAll(netherZones);
        netherData.save();   // dirty only: the world save writes it (persistence/RtsSaves)
    }

    @SubscribeEvent
    public static void loadBuildingsAndNetherZones(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);

        if (level != null) {
            CustomBuildingServerEvents.loadCustomBuildings(level);
            // DataTypes register in their owners' static initialisers, and the save below is decoded before any
            // placement exists: touch them first, or a freshly launched game drops those entries as "Unknown
            // DataType" (the factory Repeat toggle came back off after a restart; design/code_review.md CR-03)
            ProductionPlacement.REPEAT_QUEUE.getClass();
            ProductionPlacement.FACTORY_GUARD.getClass();
            BuildingReclaim.DECOMMISSION.getClass();
            com.solegendary.reignofnether.building.gameender.GameEnderPlacement.CHARGE.getClass();
            com.solegendary.reignofnether.building.gameender.WardSpirePlacement.CHARGE.getClass();
            com.solegendary.reignofnether.building.merge.MergedBuildings.MERGE_OFF.getClass();
            com.solegendary.reignofnether.building.merge.MergedBuildings.MERGE_ANCHOR.getClass();
            BuildingSaveData buildingData = BuildingSaveData.getInstance(level);
            NetherZoneSaveData netherData = NetherZoneSaveData.getInstance(level);
            ArrayList<BlockPos> placedNZs = new ArrayList<>();
            BuildingServerEvents.getBuildings().clear();
            com.solegendary.reignofnether.building.merge.MergedBuildings.SERVER.clear();   // re-derived below, from the loaded buildings
            Map<BlockPos, List<BuildingPlacement>> mergeGroups = new HashMap<>();   // loaded sections by their saved complex anchor
            buildingData.buildings.forEach(b -> {
                BuildingPlacement building = BuildingUtils.getNewBuildingPlacement(b.building,
                    level,
                    b.originPos,
                    b.rotation,
                    b.ownerName,
                    b.isDiagonalBridge
                );

                if (building != null) {
                    building.partialBlocksDestroyed = b.partialBlocksDestroyed;
                    building.dataStorage = b.dataStorage;
                    
                    building.tags = b.dataStorage.getData(BUILDING_TAGS);
                    building.commands = b.dataStorage.getData(BUILDING_COMMANDS);
                    
                    building.scenarioRoleIndex = b.scenarioRoleIndex;
                    building.isBuilt = b.isBuilt;
                    building.mergeEnabled = !Boolean.TRUE.equals(b.dataStorage.getData(com.solegendary.reignofnether.building.merge.MergedBuildings.MERGE_OFF));
                    BlockPos mergeAnchor = b.dataStorage.getData(com.solegendary.reignofnether.building.merge.MergedBuildings.MERGE_ANCHOR);
                    if (mergeAnchor != null)
                        mergeGroups.computeIfAbsent(mergeAnchor, k -> new ArrayList<>()).add(building);
                    BuildingServerEvents.getBuildings().add(building);
                    if (building instanceof ProductionPlacement pb) {
                        pb.setRallyPoint(b.rallyPoint);
                        pb.repeatQueue = Boolean.TRUE.equals(b.dataStorage.getData(ProductionPlacement.REPEAT_QUEUE));
                        pb.stewardPaused = Boolean.TRUE.equals(b.dataStorage.getData(ProductionPlacement.STEWARD_PAUSED));
                        pb.restoreFactoryGuard();
                    }

                    if (b.upgradeLevel > 0) {
                        if (building instanceof PortalPlacement portal) {
                            if (!(building.getBuilding() instanceof NeutralTransportPortal)) {
                                portal.changePortalStructure(b.portalType);
                            }
                            if (b.portalDestination != null && !b.portalDestination.equals(new BlockPos(0, 0, 0))) {
                                portal.destination = b.portalDestination;
                            }
                        } else if (building instanceof BeaconPlacement beacon) {
                            beacon.changeBeaconStructure(b.upgradeLevel);
                        } else {
                            String upgradedStructureName = building.getBuilding().getUpgradedStructureName(b.upgradeLevel);
                            if (!upgradedStructureName.equals(building.getBuilding().structureName)) {
                                building.changeStructure(upgradedStructureName);
                            }
                        }
                    }
                    // a levelled lab back into its level's structure (design/building_upgrades.md)
                    BuildingLevels.restore(building, b.upgradeLevel);
                    // setNetherZone can only be run once - this supercedes where it normally happens in tick() ->
                    // onBuilt()
                    NetherConvertingAddon ncb;
                    if ((ncb = building.getBuilding().getActiveAddon(NetherConvertingAddon.class)) != null && ncb.getMaxNetherRange(building) > 0) {
                        for (NetherZone nz : netherData.netherZones)
                            if (building.isPosInsideBuilding(nz.getOrigin())) {
                                ncb.setNetherZone(building, nz, false);
                                placedNZs.add(nz.getOrigin());
                                ReignOfNether.LOGGER.info("loaded netherzone for: " + b.building.name + "|" + b.originPos);
                                break;
                            }
                    }
                    if (building instanceof CustomBuildingPlacement cb) {
                        cb.setAndUnpackCommandsNbt(b.commandsNbt);
                    }
                    BuildingReclaim.restoreFromSave(building);
                    building.readSaveState();

                    ReignOfNether.LOGGER.info("loaded building in serverevents: " + b.building.name + "|" + b.originPos);
                }
            });
            // merged buildings: the complexes stand again the moment their members do, here and silently - left to
            // the first tick, every loaded complex would "form" with its beams and chime (MergedBuildingsFx) - seeded
            // from the saved anchors so the cut the player built up is the one that comes back
            com.solegendary.reignofnether.building.merge.MergedBuildings.SERVER.seed(mergeGroups.values(), level.getGameTime());
            com.solegendary.reignofnether.building.merge.MergedBuildings.SERVER.update(buildings, level.getGameTime());
            netherData.netherZones.forEach(nz -> {
                if (!placedNZs.contains(nz.getOrigin())) {
                    BuildingServerEvents.netherZones.add(nz);
                    nz.startRestoring();
                    ReignOfNether.LOGGER.info("loaded orphaned netherzone: " + nz.getOrigin());
                }
            });
            saveNetherZones(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
        if (level != null) {
            for (BuildingPlacement bp : getBuildings()) {
                if (bp instanceof ProductionPlacement pp)
                    for (ActiveProduction activeProd : new ArrayList<>(pp.productionQueue))
                        pp.cancelProductionItem(activeProd.item, true);
            }
            saveNetherZones(level);
            saveBuildings(level);
            netherZones.clear();
            buildings.clear();
            Blueprints.clear();
            com.solegendary.reignofnether.building.merge.MergedBuildings.SERVER.clear();
        }
    }

    private static boolean isOverlappingAnyOtherBuilding(BuildingPlacement buildingToPlace) {
        BlockPos minPos = buildingToPlace.minCorner;
        BlockPos maxPos = buildingToPlace.maxCorner;

        for (BuildingPlacement building : buildings) {
            for (BuildingBlock block : building.blocks) {
                if (buildingToPlace.getBuilding() instanceof AbstractBridge &&
                        building.getBuilding() instanceof AbstractBridge) {
                    continue;
                }
                BlockPos bp = block.getBlockPos();
                if (bp.getX() >= minPos.getX() && bp.getX() <= maxPos.getX() && bp.getY() >= minPos.getY()
                        && bp.getY() <= maxPos.getY() && bp.getZ() >= minPos.getZ() && bp.getZ() <= maxPos.getZ()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    public static BuildingPlacement placeBuilding(
        Building building,
        BlockPos originPos,
        Rotation rotation,
        String ownerName,
        int[] builderUnitIds,
        boolean queue, // shift-queue the building for assigned workers
        boolean isDiagonalBridge,
        boolean fromCommand, // ignore resources, terrain or any other restrictions and self-build
        boolean ignoreFog
    ) {
        if (serverLevel == null)
            return null;

        BuildingPlacement newBuilding = BuildingUtils.getNewBuildingPlacement(building,
            serverLevel,
            originPos,
            rotation,
            ownerName,
            isDiagonalBridge
        );
        return placeBuilding(newBuilding, originPos, rotation, ownerName, builderUnitIds, queue, isDiagonalBridge, fromCommand, ignoreFog);
    }

    public static BuildingPlacement placeBuilding(
        BuildingPlacement newBuilding,
        BlockPos originPos,
        Rotation rotation,
        String ownerName,
        int[] builderUnitIds,
        boolean queue, // shift-queue the building for assigned workers
        boolean isDiagonalBridge,
        boolean fromCommand, // ignore resources, terrain or any other restrictions and self-build
        boolean ignoreFog
    ) {
        boolean buildingExists = false;
        for (BuildingPlacement pending : Blueprints.all())   // a pending site on this spot counts too
            if (pending.originPos.equals(originPos))
                buildingExists = true;
        for (BuildingPlacement placement : buildings) {
            if (placement.originPos.equals(originPos)) {
                buildingExists = true;
                break;
            }
        }
        if (newBuilding != null && !buildingExists && serverLevel != null) {

            // shift-queue depth: a site nobody can take goes nowhere (lovish's clip: "I can't queue more than that or
            // it bugs"); builders whose queue is full are simply left out of this one
            if (queue && builderUnitIds != null && builderUnitIds.length > 0) {
                int[] withRoom = buildersWithQueueRoom(builderUnitIds);
                if (withRoom.length == 0) {
                    HudClientboundPacket.showTempMessageI18n(ownerName, "hud.reignofnether.queue_full");
                    FogBuildingClientboundPacket.removeFogQueuedBuilding(ownerName, originPos);
                    return null;
                }
                builderUnitIds = withRoom;
            }

            // BAR flow economy: no upfront payment and no affordability check. The building is paid for while it
            // is constructed (BuildingPlacement.tickFlowConstruction) and simply stalls when resources run out.
            newBuilding.freeBuild = fromCommand;

            boolean isSandbox = SandboxServer.isSandboxOwner(ownerName);   // the placing owner, not "somebody is in sandbox"
            // only a T2 constructor may raise a T3 lab (the build button checks the selection; this stops a crafted
            // packet). A fog-queued site re-placed by its worker (ignoreFog, no builder ids) was checked when queued.
            boolean fogRequeue = ignoreFog && (builderUnitIds == null || builderUnitIds.length == 0);
            if (!fromCommand && !isSandbox && !fogRequeue
                    && !ResearchServerEvents.playerHasCheat(ownerName, "modifythephasevariance")
                    && !com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(serverLevel, newBuilding.getBuilding(), builderUnitIds)) {
                HudClientboundPacket.showTempMessageI18n(ownerName, "building.reignofnether.needs_t2_worker");
                FogBuildingClientboundPacket.removeFogQueuedBuilding(ownerName, originPos);
                return null;
            }
            // a T2 lab needs the Tier 2 research and its faction's finished T1 lab (resources/TierGate): the build button
            // greys it out, but a stale client, a crafted packet or an old fog-queued site must not skip that. A command
            // placement (scenarios, spawners), the sandbox and the "modifythephasevariance" cheat are exempt as before
            if (!fromCommand && !isSandbox
                    && !ResearchServerEvents.playerHasCheat(ownerName, "modifythephasevariance")) {
                String tierError = com.solegendary.reignofnether.resources.TierGate.placementError(ownerName, newBuilding.getBuilding());
                if (tierError != null) {
                    HudClientboundPacket.showTempMessageI18n(ownerName, tierError);
                    FogBuildingClientboundPacket.removeFogQueuedBuilding(ownerName, originPos);
                    return null;
                }
            }
            // combat engineers start field defences only; with a worker among the builders they assist (design/combat_engineers.md)
            if (!fromCommand && !isSandbox && !fogRequeue
                    && !com.solegendary.reignofnether.unit.CombatEngineers.buildersMayPlace(serverLevel, newBuilding.getBuilding(), builderUnitIds)) {
                HudClientboundPacket.showTempMessageI18n(ownerName, com.solegendary.reignofnether.unit.CombatEngineers.MSG_FIELD_DEFENCES_ONLY);
                FogBuildingClientboundPacket.removeFogQueuedBuilding(ownerName, originPos);
                return null;
            }
            if (!fromCommand && !isSandbox && !BuildingValidators.isInBrightChunk(serverLevel, newBuilding.centrePos, ownerName) && !ignoreFog) {
                for (int id : builderUnitIds) {
                    Entity entity = serverLevel.getEntity(id);
                    if (entity instanceof WorkerUnit workerUnit) {
                        if (!queue) {
                            Unit.fullResetBehaviours((Unit) entity);
                        }
                        workerUnit.getExploreBuildLocationGoal().getFogQueuedBuildings().add(newBuilding);
                    }
                }
                return null;
            } else if (!fromCommand) {
                String errorMsgKey = BuildingValidators.getPlacementValidityError(serverLevel, newBuilding.getBuilding(), originPos, ownerName, rotation, isDiagonalBridge, isSandbox, true);
                if (errorMsgKey != null) {
                    HudClientboundPacket.showTempMessageI18n(ownerName, errorMsgKey);
                    FogBuildingClientboundPacket.removeFogQueuedBuilding(ownerName, originPos);
                    return null;
                }
            }

            // BLUEPRINTS (lovish, Oct 22: "Blueprints shouldn't drop physical prints until the worker gets there, and you
            // shouldn't be able to see enemy blueprints"). A worker-built site is now only an ORDER until a worker
            // reaches it: nothing below touches the world, the registry or the network. The placement waits in
            // Blueprints (owner + allies + spectators see its ghost, nobody else), the worker walks to it, and
            // Blueprints.arrive re-checks the site and calls beginConstruction - the code that used to run right here.
            // Not for command-placed sites (match start, tests, spawners, the tutorial's script: fromCommand) or sandbox
            // editing, which raise at once as before. A player's order with no worker behind it has nobody to ever
            // reach the site, and a physical stub would be exactly the print this rule forbids: it is refused.
            boolean workerOrder = !fromCommand && !isSandbox;
            if (workerOrder && !fogRequeue && (builderUnitIds == null || builderUnitIds.length == 0)) {
                HudClientboundPacket.showTempMessageI18n(ownerName, "building.reignofnether.needs_worker");
                return null;
            }
            if (workerOrder) {
                Blueprints.add(newBuilding);
                assignBuilderUnits(builderUnitIds, queue, newBuilding);
                return newBuilding;
            }

            raiseSite(newBuilding, originPos, rotation, ownerName, fromCommand, isDiagonalBridge);
            assignBuilderUnits(builderUnitIds, queue, newBuilding);
            settleSite(newBuilding, ownerName, builderUnitIds);

            if (isSandbox && builderUnitIds.length == 0) {
                newBuilding.getBuilding().shouldDestroyOnReset = false;
                saveBuildings(serverLevel);
            }
            return newBuilding;
        }
        return null;
    }

    /**
     * Physical construction starts: everything a placement used to do the moment it was ordered - fell the trees on
     * the site, level or clear the footprint, register the building (pathing, overlap tests, fog, the economy all see
     * it from here), lay scaffolding under the floating bottom layer, queue the foundation layer and tell the clients.
     * Called when the order is for a site that needs no walk (fromCommand, no workers) and, for a blueprint, by
     * {@link Blueprints#arrive} when the first assigned worker reaches it.
     */
    private static void raiseSite(BuildingPlacement newBuilding, BlockPos originPos, Rotation rotation, String ownerName,
                                  boolean fromCommand, boolean isDiagonalBridge) {
        // trees in the footprint and a 2-block ring are felled as reclaim for the owner (TreeClearing) - before
        // the site clears/levels its footprint, so the logs are credited rather than erased
        if (!(newBuilding.getBuilding() instanceof AbstractBridge))
            com.solegendary.reignofnether.resources.TreeClearing.clearForBuilding(serverLevel, newBuilding);

        if (fromCommand ||
                (serverLevel.getGameRules().getRule(GameRuleRegistrar.SLANTED_BUILDING).get() &&
                        !(newBuilding.getBuilding() instanceof AbstractBridge))) {
            BuildingUtils.clearBuildingArea(newBuilding);
        } else if (!(newBuilding.getBuilding() instanceof AbstractBridge)) {
            levelFootprint(newBuilding);
        }
        buildings.add(newBuilding);
        newBuilding.forceChunk(true);
        int minY = BuildingUtils.getMinCorner(newBuilding.blocks).getY();

        if (!(newBuilding.getBuilding() instanceof AbstractBridge))
            for (BuildingBlock block : newBuilding.blocks)
                if (block.getBlockPos().getY() == minY && !block.getBlockState().isAir())
                    placeScaffoldingUnder(block, newBuilding);

        boolean hasFastBuildCheat = ResearchServerEvents.playerHasCheat(ownerName, "warpten");
        boolean isSandbox = SandboxServer.isSandboxOwner(ownerName);   // the placing owner, not "somebody is in sandbox" (27c90f43)
        if (isSandbox && hasFastBuildCheat) {
            newBuilding.maxBlocksPerTick = 4;
            newBuilding.queueAllBlocks(serverLevel);
        } else {
            // speed up first capitol
            if (newBuilding.isCapitol && BuildingUtils.getTotalCompletedBuildingsOwned(false, ownerName) == 0) {
                newBuilding.blocksPerBuild = 2;
                newBuilding.maxBlocksPerTick = 2;
                newBuilding.buildSpeedMult = 2f;
            }
            // a building that names no starting block types (our generated extractor and wind generator) lays its
            // whole foundation layer instead - with an empty queue and nothing placed, RoN counts a fresh site as
            // destroyed on its first tick (the windmill that blew up the moment it was placed)
            // (a starting-type block the biome palette changed still counts: BiomePalette.joinsStartQueue)
            boolean anyStartType = !newBuilding.getBuilding().startingBlockTypes.isEmpty();
            for (BuildingBlock block : newBuilding.blocks) {
                if (block.getBlockPos().getY() <= minY + (newBuilding.getBuilding().foundationYLayers - 1)
                        && !block.getBlockState().isAir()
                        && (!anyStartType || BiomePalette.joinsStartQueue(newBuilding.getBuilding(), block, newBuilding.biomePaletteStarts))) {
                    newBuilding.addToBlockPlaceQueue(block);
                }
            }
        }

        BuildingClientboundPacket.placeBuilding(originPos,
                newBuilding.getBuilding(),
                rotation,
                newBuilding.getBuilding() instanceof AbstractBridge ? "" : ownerName,
                -1,
                newBuilding.blockPlaceQueue.size(),
                isDiagonalBridge,
                0,
                false,
                PortalPlacement.PortalType.BASIC,
                originPos
        );

        if (SandboxServer.isNeutralSandboxOwner(ownerName))
            newBuilding.selfBuilding = true;
    }

    /**
     * What happens round a site that just rose: own units standing on the footprint are walked off it (the builders
     * stay), huntable animals are herded away, and a bridge becomes neutral (anyone's workers may finish it).
     */
    private static void settleSite(BuildingPlacement newBuilding, String ownerName, int[] builderUnitIds) {
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (entity instanceof Unit unit && unit.getOwnerName().equals(ownerName) &&
                    newBuilding.isPosInsideBuilding(entity.getOnPos().above().above()) &&
                    (unit.getMoveGoal().getMoveTarget() == null ||
                            newBuilding.isPosInsideBuilding(unit.getMoveGoal().getMoveTarget()))) {
                moveNonBuildersAwayFromBuildingFoundations(entity, builderUnitIds, newBuilding);
            }
        }
        moveAnimalsAwayFromBuildingFoundations(newBuilding);

        if (newBuilding.getBuilding() instanceof AbstractBridge)
            newBuilding.ownerName = "";
    }

    /**
     * A blueprint's first worker is on the site: raise it (raiseSite + settleSite). Called only by
     * {@link Blueprints#arrive}, after it re-validated the site and took the placement off the pending list.
     */
    static void beginConstruction(BuildingPlacement bp, int[] builderUnitIds) {
        raiseSite(bp, bp.originPos, bp.rotation, bp.ownerName, false, bp.isDiagonalBridge);
        settleSite(bp, bp.ownerName, builderUnitIds);
    }

    /**
     * BuildingValidators accepts gentle slopes (soft blocks in the way, 1 block of terrain poking into the bottom
     * layer, 1-block gaps under it), so level the site here: clear soft blocks inside the footprint and terrain
     * left inside the bottom layer where the building itself has no block (so it isn't buried), and fill 1-block
     * gaps under the foundation with the surrounding ground (so it isn't floating on scaffolding over a tiny step).
     * Deeper gaps keep RoN's scaffolding. Bottom-layer terrain under a building block is simply overwritten when
     * that block is built.
     */
    public static void levelFootprint(BuildingPlacement building) {
        Level level = building.getLevel();
        int minY = BuildingUtils.getMinCorner(building.blocks).getY();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BuildingBlock block : building.blocks) {
            BlockPos bp = block.getBlockPos();
            BlockState bsWorld = level.getBlockState(bp);
            if (!bsWorld.isAir() && block.getBlockState().isAir() && bsWorld.getFluidState().isEmpty()
                    && (BuildingValidators.isSoftBlock(bsWorld) || bp.getY() == minY))
                level.setBlockAndUpdate(bp, air);

            if (bp.getY() != minY || block.getBlockState().isAir())
                continue;
            // soft blocks occupying a building block's spot go too (otherwise they'd pop off as items when built)
            if (!bsWorld.isAir() && BuildingValidators.isSoftBlock(bsWorld))
                level.setBlockAndUpdate(bp, air);
            BlockPos gapPos = bp.below();
            // the gap is below our footprint, so make sure it isn't a neighbouring building's space
            if (BuildingValidators.getGapDepth(level, bp) == 1
                    && !BuildingUtils.isPosInsideAnyBuilding(level.isClientSide(), gapPos)) {
                BlockState ground = level.getBlockState(gapPos.below());
                BlockState fill = ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.MYCELIUM) || ground.is(Blocks.PODZOL)
                        || !ground.isCollisionShapeFullBlock(level, gapPos.below()) || ground.hasBlockEntity()
                        ? Blocks.DIRT.defaultBlockState() : ground;
                level.setBlockAndUpdate(gapPos, fill);
            }
        }
    }

    private static void placeScaffoldingUnder(BuildingBlock block, BuildingPlacement newBuilding) {
        BlockPos basePos = block.getBlockPos();
        int yBelow = 0;
        BlockState bsBelow;

        // Search downward for a solid block up to -5 levels below
        while (yBelow > -MAX_SCAFFOLD_DEPTH) {
            yBelow--;
            BlockPos bpBelow = basePos.offset(0, yBelow, 0);
            if (MiscUtil.isSolidBlocking(newBuilding.level, bpBelow)) {
                break; // Found a solid block, exit loop
            }
        }
        if (yBelow <= -MAX_SCAFFOLD_DEPTH) {
            return;
        }

        // Place scaffolding from the lowest point back to the original block's level
        for (int y = yBelow + 1; y < 0; y++) {
            BlockPos scaffoldPos = basePos.offset(0, y, 0);
            BuildingBlock scaffold = new BuildingBlock(scaffoldPos, Blocks.SCAFFOLDING.defaultBlockState());
            newBuilding.getScaffoldBlocks().add(scaffold);
            newBuilding.addToBlockPlaceQueue(scaffold);
        }
    }


    /** The builders among {@code builderUnitIds} whose shift-queue still has room (BuildRepairGoal.MAX_QUEUE). */
    static int[] buildersWithQueueRoom(int[] builderUnitIds) {
        if (serverLevel == null)
            return builderUnitIds;
        List<Integer> out = new ArrayList<>(builderUnitIds.length);
        for (int id : builderUnitIds) {
            Entity entity = serverLevel.getEntity(id);
            if (!(entity instanceof WorkerUnit workerUnit)
                    || workerUnit.getBuildRepairGoal().queuedBuildings.size()
                        + workerUnit.getExploreBuildLocationGoal().getFogQueuedBuildings().size()
                        < com.solegendary.reignofnether.unit.goals.BuildRepairGoal.MAX_QUEUE)
                out.add(id);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void assignBuilderUnits(int[] builderUnitIds, boolean queue, BuildingPlacement newBuilding) {
        if (serverLevel == null)
            return;

        for (int id : builderUnitIds) {
            Entity entity = serverLevel.getEntity(id);
            if (entity instanceof WorkerUnit workerUnit) {
                if (queue) {
                    var brg = workerUnit.getBuildRepairGoal();
                    BuildingPlacement current = brg.getBuildingTarget();
                    if (brg.queuedBuildings.isEmpty()) {
                        if (current != null) {
                            // a plain (unqueued) build order leaves the queue empty while the worker is on the site,
                            // and the reset below would have dropped that site on the spot - the "shift-queueing
                            // cancels my build" bug. Keep it as the head of the queue instead.
                            brg.queuedBuildings.add(current);
                        } else {
                            ((Unit) entity).resetBehaviours();
                            WorkerUnit.resetBehavioursExceptExploreBuild(workerUnit);
                        }
                    }
                    brg.queuedBuildings.add(newBuilding);
                    if (brg.getBuildingTarget() == null) {
                        brg.startNextQueuedBuilding();
                    }
                } else {
                    ((Unit) entity).resetBehaviours();
                    WorkerUnit.resetBehavioursExceptExploreBuild(workerUnit);
                    workerUnit.getBuildRepairGoal().setBuildingTarget(newBuilding);
                }
            }
        }
    }

    private static void warnInsufficientResources(BuildingPlacement newBuilding) {
        ResourcesClientboundPacket.warnInsufficientResources(newBuilding.ownerName,
            ResourcesServerEvents.canAfford(newBuilding.ownerName, ResourceName.FOOD, newBuilding.getBuilding().cost.food),
            ResourcesServerEvents.canAfford(newBuilding.ownerName, ResourceName.WOOD, newBuilding.getBuilding().cost.wood),
            ResourcesServerEvents.canAfford(newBuilding.ownerName, ResourceName.ORE, newBuilding.getBuilding().cost.ore),
            ResourcesServerEvents.canAfford(newBuilding.ownerName, ResourceName.EMERALD, newBuilding.getBuilding().cost.emerald)
        );
    }

    private static void moveNonBuildersAwayFromBuildingFoundations(
        LivingEntity entity, int[] builderUnitIds, BuildingPlacement newBuilding
    ) {
        boolean b = true;
        for (int id : builderUnitIds) {
            if (id == entity.getId()) {
                b = false;
                break;
            }
        }
        if (b && com.solegendary.reignofnether.unit.GuardServerEvents.isGuarding(entity.getId())) {
            // a guard escorting its ward stands next to it - often on the ward's new footprint. A MOVE order here
            // ended the guard (UnitActionItem clears it on any order), so the assisting builder went idle while the
            // ward built alone. Step it off without an order: the guard pass then gives a guarding builder this site.
            Unit unit = (Unit) entity;
            unit.setFollowTarget(null);
            unit.setMoveTarget(newBuilding.getClosestGroundPos(entity.getOnPos(), 3, true));
        } else if (b) {
            UnitServerEvents.addActionItem(((Unit) entity).getOwnerName(),
                UnitAction.MOVE,
                -1,
                new int[] { entity.getId() },
                newBuilding.getClosestGroundPos(entity.getOnPos(), 3, true),
                new BlockPos(0, 0, 0)
            );
        }
    }

    private static void moveAnimalsAwayFromBuildingFoundations(BuildingPlacement newBuilding) {
        List<Mob> mobs = MiscUtil.getEntitiesWithinRange(newBuilding.centrePos.getCenter(), 10, Mob.class, newBuilding.level);
        for (Mob mob : mobs) {
            if (ResourceSources.isHuntableAnimal(mob)) {
                BlockPos bp = newBuilding.getClosestGroundPos(mob.getOnPos(), 3, true);
                Path path = mob.getNavigation().createPath(bp.getX(), bp.getY(), bp.getZ(), 0);
                mob.getNavigation().moveTo(path, 1);
            }
        }
    }

    public static void cancelBuilding(BuildingPlacement building, String playerName) {
        if (building == null)
            return;
        // a blueprint nobody has started: nothing was built, paid or reserved, so cancelling it costs and refunds nothing
        if (Blueprints.isPending(building)) {
            Blueprints.cancel(building, null);
            return;
        }
        if (building.isBuilt && !SandboxServer.isSandboxPlayer(playerName) &&
            BuildingUtils.getTotalCompletedBuildingsOwned(false, building.ownerName) == 1) {
            HudClientboundPacket.showTempMessageI18n(playerName,"hud.helperbuttons.reignofnether.cancel.error");
            return;
        }
        if (building.getBuilding().capturable && !SandboxServer.isSandboxPlayer(playerName)) {
            HudClientboundPacket.showTempMessageI18n(playerName,"hud.helperbuttons.reignofnether.cancel.error");
            return;
        }
        NightSourceAddon nsa = building.getBuilding().getActiveAddon(NightSourceAddon.class);
        if (nsa != null && nsa.getNightRange(building) > 0 && building.isBuilt && !SandboxServer.isSandboxPlayer(playerName)) {
            HudClientboundPacket.showTempMessageI18n(playerName,"hud.helperbuttons.reignofnether.cancel.error");
            return;
        }

        // remove from tracked buildings, all of its leftover queued blocks and then blow it up
        buildings.remove(building);
        NetherConvertingAddon ncb;
        if ((ncb = building.getBuilding().getActiveAddon(NetherConvertingAddon.class)) != null && ncb.getMaxNetherRange(building) > 0 && ncb.getNetherZone(building) != null) {
            NetherZone nz = ncb.getNetherZone(building);
            if (nz != null)
                nz.startRestoring();
            saveNetherZones(serverLevel);
        }
        FrozenChunkClientboundPacket.setBuildingDestroyedServerside(building.originPos);

        // BAR flow economy refund: an unbuilt building returns exactly what was spent on it so far
        // (in survival, a completed building still refunds 50% of its cost scaled by its remaining blocks)
        if (!building.isBuilt || SurvivalServerEvents.isEnabled()) {

            float buildPercent = building.getBlocksPlacedPercent();
            int food = 0;
            int wood = Math.round(building.energySpent);
            int ore = Math.round(building.metalSpent);
            String refundName = building.payerName != null ? building.payerName : building.ownerName;

            if (building.isBuilt && SurvivalServerEvents.isEnabled()) {
                food = Math.round(building.getBuilding().cost.food * 0.5f * buildPercent);
                wood = Math.round(building.getBuilding().cost.wood * 0.5f * buildPercent);
                ore = Math.round(building.getBuilding().cost.ore * 0.5f * buildPercent);
                refundName = building.ownerName;
            }
            building.metalSpent = 0;
            building.energySpent = 0;
            if ((food > 0 || wood > 0 || ore > 0) && refundName != null && !refundName.isEmpty()) {
                Resources res = new Resources(refundName, food, wood, ore);
                ResourcesServerEvents.addSubtractResources(res);
                ResourcesClientboundPacket.showFloatingText(res, building.centrePos);
            }
        }
        building.destroy((ServerLevel) building.getLevel());
    }

    // similar to BuildingClientEvents getPlayerToBuildingRelationship: given a Unit and Building, what is the
    // relationship between them
    public static Relationship getUnitToBuildingRelationship(Unit unit, BuildingPlacement building) {
        if (unit.getOwnerName().equals(building.ownerName)) {
            return Relationship.OWNED;
        } else {
            return Relationship.HOSTILE;
        }
    }

    private static void placeBuildingsClientside() {
        for (BuildingPlacement building : buildings) {
            BuildingClientboundPacket.placeBuilding(building.originPos,
                    building.getBuilding(),
                    building.rotation,
                    building.ownerName,
                    building.scenarioRoleIndex,
                    building.blockPlaceQueue.size(),
                    building.getBuilding() instanceof AbstractBridge && building.isDiagonalBridge,
                    building.getUpgradeLevel(),
                    building.isBuilt,
                    building instanceof PortalPlacement p ? p.getPortalType() : PortalPlacement.PortalType.BASIC,
                    building instanceof PortalPlacement p && p.hasDestination() ? p.destination : new BlockPos(0, 0, 0)
            );

            if (building instanceof ProductionPlacement pp && pp.repeatQueue) {
                BuildingProductionClientboundPacket.toggleRepeat(building.ownerName, building.originPos, true);
            }
            if (building instanceof ProductionPlacement pp && pp.factoryGuard) {
                BuildingProductionClientboundPacket.toggleFactoryGuard(building.ownerName, building.originPos, true);
            }

            if (building.getBuilding() instanceof Library) {
                EnchantAbility ability = building.getDataStorage().getData(Library.AUTO_CAST_ENCHANT);
                if (ability != null) {
                    BuildingAbilityClientboundPacket.doAbility(ability.action, building.originPos);
                }
            }else if (building.getBuilding() instanceof Blacksmith) {
                EquipAbility ability = building.getDataStorage().getData(Blacksmith.AUTO_CAST_EQUIP);
                if (ability != null) {
                    BuildingAbilityClientboundPacket.doAbility(ability.action, building.originPos);
                }
            }
        }
    }

    public static void placeBuildingClientside(BlockPos pos) {
        for (BuildingPlacement building : buildings) {
            if (building.originPos.equals(pos)) {
                BuildingClientboundPacket.placeBuilding(building.originPos,
                        building.getBuilding(),
                        building.rotation,
                        building.ownerName,
                        building.scenarioRoleIndex,
                        building.blockPlaceQueue.size(),
                        building.getBuilding() instanceof AbstractBridge && building.isDiagonalBridge,
                        building.getUpgradeLevel(),
                        building.isBuilt,
                        building instanceof PortalPlacement p ? p.getPortalType() : PortalPlacement.PortalType.BASIC,
                        building instanceof PortalPlacement p && p.hasDestination() ? p.destination : new BlockPos(0, 0, 0)
                );
            }
            break;
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!PlayerServerEvents.rtsSyncingEnabled) {
            return;
        }
        MinecraftServer server = evt.getEntity().level().getServer();
        if (server == null || !server.isDedicatedServer()) {
            TaskSchedulerServerEvents.schedule(20, BuildingServerEvents::placeBuildingsClientside);
        } else {
            placeBuildingsClientside();
        }
        // a late joiner gets the ghosts of their own side too (Blueprints.syncTo checks who may see each)
        if (evt.getEntity() instanceof ServerPlayer joined) {
            if (server == null || !server.isDedicatedServer())
                TaskSchedulerServerEvents.schedule(20, () -> Blueprints.syncTo(joined));   // as the buildings above: the client is not ready yet
            else
                Blueprints.syncTo(joined);
        }
        //ReignOfNether.LOGGER.info("Synced " + buildings.size() + " buildings with player logged in");
    }

    // if blocks are destroyed manually by a player then help it along by causing periodic explosions
    @SubscribeEvent
    public static void onPlayerBlockBreak(BlockEvent.BreakEvent evt) {
        if (!evt.getLevel().isClientSide()) {
            for (BuildingPlacement building : buildings)
                if (building.isPosPartOfBuilding(evt.getPos(), true)) {
                    building.onBlockBreak((ServerLevel) evt.getLevel(), evt.getPos(), true);
                }
        }
    }

    // prevent dungeons spawners from actually spawning
    @SubscribeEvent
    public static void onLivingSpawn(MobSpawnEvent.FinalizeSpawn evt) {
        if (evt.getSpawnType() == MobSpawnType.SPAWNER) {
            if (evt.getSpawner() != null && evt.getSpawner().getSpawnerBlockEntity() != null) {
                BlockPos bp = evt.getSpawner().getSpawnerBlockEntity().getBlockPos();
                BuildingPlacement bpl = BuildingUtils.findBuilding(false, bp);
                if (bpl != null &&
                   (bpl.getBuilding() instanceof Dungeon ||
                    bpl.getBuilding() instanceof FlameSanctuary)) {
                    evt.getEntity().discard();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.level.isClientSide() || evt.level.dimension() != Level.OVERWORLD) {
            return;
        }

        serverLevel = (ServerLevel) evt.level;

        buildingSyncTicks -= 1;
        if (buildingSyncTicks <= 0) {
            buildingSyncTicks = BUILDING_SYNC_TICKS_MAX;
            for (BuildingPlacement building : buildings)
                BuildingClientboundPacket.syncBuilding(building.originPos, building.getBlocksPlaced(),
                        building.partialBlocksDestroyed, building.ownerName, building.scenarioRoleIndex,
                        building.mergeEnabled, com.solegendary.reignofnether.building.merge.MergedBuildings.SERVER.anchorOf(building));
        }
        // need to remove from the list first as destroy() will read it to check defeats
        List<BuildingPlacement> buildingsToDestroy = new ArrayList<>();
        for (BuildingPlacement buildingPlacement : buildings) {
            if (buildingPlacement.shouldBeDestroyed()) {
                buildingsToDestroy.add(buildingPlacement);
            }
        }
        buildings.removeIf(b -> {
            if (b.shouldBeDestroyed()) {
                NetherConvertingAddon ncb;
                if ((ncb = b.getBuilding().getActiveAddon(NetherConvertingAddon.class)) != null && ncb.getMaxNetherRange(b) > 0 && ncb.getNetherZone(b) != null) {
                    NetherZone nz = ncb.getNetherZone(b);
                    if (nz != null)
                        nz.startRestoring();
                    saveNetherZones(serverLevel);
                }
                FrozenChunkClientboundPacket.setBuildingDestroyedServerside(b.originPos);
                return true;
            }
            return false;
        });

        for (BuildingPlacement building : buildingsToDestroy) {
            building.getBuilding().onFellToDamage(serverLevel, building);   // death blasts (EnergyConverter's chain)
            building.destroy(serverLevel);
        }

        Blueprints.tick(serverLevel);

        ArrayList<BuildingPlacement> bpls = new ArrayList<>(buildings);
        int sysBuildings = Sys.begin(Sys.BUILDINGS);
        try {
            for (BuildingPlacement building : bpls)
                building.tick(serverLevel);
        } finally {
            Sys.end(sysBuildings);
        }

        // merged buildings: after the ticks, so a complex forms the tick its last member finished (a cheap
        // signature check; it re-derives only when a member was built, lost, sold or switched); what changed gets
        // its moment (the placeholder beams and chime until the animations land)
        com.solegendary.reignofnether.building.merge.MergedBuildingsFx.onChanges(serverLevel,
            com.solegendary.reignofnether.building.merge.MergedBuildings.SERVER.update(buildings, serverLevel.getGameTime()));

        for (NetherZone netherConversionZone : netherZones)
            netherConversionZone.tick(serverLevel);

        int nzSizeBefore = netherZones.size();
        netherZones.removeIf(NetherZone::isDone);
        int nzSizeAfter = netherZones.size();
        if (nzSizeBefore != nzSizeAfter) {
            saveNetherZones(serverLevel);
        }

        String playerName;
        if (serverLevel.getServer().getTickCount() % 10 == 0) {
            for (Objective objective : serverLevel.getScoreboard().getObjectives()) {
                if (objective.getCriteria().equals(ResourceObjectiveCriteria.POPULATION))
                    for (ServerPlayer player : serverLevel.players()) {
                        playerName = player.getName().getString();
                        int currentPopulation = UnitServerEvents.getCurrentPopulation(playerName);
                        if (serverLevel != null && currentPopulation != populations.getInt(playerName)) {
                            populations.put(playerName, currentPopulation);
	                        serverLevel.getScoreboard().forAllObjectives(ResourceObjectiveCriteria.POPULATION, playerName, (p_9178_) -> p_9178_.setScore(currentPopulation));
                        }
                    }
            }
            
            for (BuildingPlacement bpl : getBuildings()) {
                // no population cap: a banked skull (only an old save has one) walks out unless the toggle holds it
                if (bpl instanceof GraveyardPlacement gy && gy.getUpgradeLevel() > 0 && gy.autoRelease)
                    gy.releaseNextUnit();
            }
        }
    }

    // cancel all explosion damage to non-building blocks
    // cancel damage to entities and non-building blocks if it came from a non-entity source such as:
    // - building block breaks
    // - beds (vanilla)
    // - respawn anchors (vanilla)
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate evt) {
        Explosion exp = evt.getExplosion();

        GhastUnit ghastUnit = null;
        CreeperUnit creeperUnit = null;
        PillagerUnit pillagerUnit = null;

        if (evt.getExplosion().getExploder() instanceof CreeperUnit cUnit) {
            creeperUnit = cUnit;
        }
        else if (evt.getExplosion().getExploder() instanceof PillagerUnit pUnit) {
            pillagerUnit = pUnit;
        } else if (evt.getExplosion().getExploder() instanceof LargeFireball fireball && fireball.getOwner() instanceof GhastUnit gUnit) {
            ghastUnit = gUnit;
        }

        if (exp.getExploder() == null && ghastUnit == null) {
            evt.getAffectedEntities().clear();
        }

        // apply creeper, ghast and mounted pillager attack damage as bonus damage to buildings
        // this is dealt in addition to the actual blocks destroyed by the explosion itself
        if (creeperUnit != null || ghastUnit != null || pillagerUnit != null || exp.getExploder() instanceof PrimedTnt) {
            Set<BuildingPlacement> affectedBuildings = new HashSet<>();

            if (pillagerUnit != null) {
                Vec3 pos = evt.getExplosion().getPosition();
                evt.getAffectedBlocks().add(new BlockPos((int) pos.x, (int) pos.y - 1, (int) pos.z));
            }

            for (BlockPos bp : evt.getAffectedBlocks()) {
                BuildingPlacement building = BuildingUtils.findBuilding(false, bp);

                if (building != null) {
                    // prevent enemy ghasts friendly firing their own buildings
                    if (!(SurvivalServerEvents.isEnabled() && ghastUnit != null &&
                            SurvivalServerEvents.ENEMY_OWNER_NAME.equals(ghastUnit.getOwnerName()) &&
                            SurvivalServerEvents.ENEMY_OWNER_NAME.equals(building.ownerName)))
                        affectedBuildings.add(building);
                }
            }
            for (BuildingPlacement building : affectedBuildings) {
                float atkDmg = 0;
                boolean byUnit = ghastUnit != null || creeperUnit != null || pillagerUnit != null;
                if (ghastUnit != null) {
                    atkDmg = ghastUnit.getUnitAttackDamage();
                    building.lastAttacker = ghastUnit;
                } else if (creeperUnit != null) {
                    atkDmg = creeperUnit.getUnitAttackDamage();
                    if (creeperUnit.isPowered()) {
                        atkDmg *= CreeperUnit.CHARGED_DAMAGE_MULT;
                    }
                    building.lastAttacker = creeperUnit;
                } else if (pillagerUnit != null) {
                    atkDmg = pillagerUnit.getUnitAttackDamage() / 2;
                    building.lastAttacker = pillagerUnit;
                } else if (exp.getExploder() instanceof AdjustablePrimedTnt aTNT) {
                    atkDmg = aTNT.getExplosionPower() *  AdjustablePrimedTnt.DAMAGE_PER_POWER;
                } else if (exp.getExploder() instanceof PrimedTnt) {
                    atkDmg = TNT_BUILDING_BASE_DAMAGE;
                }

                if (atkDmg > 0) {
                    // all explosion damage will directly hit all occupants at an average of 1/4 rate
                    GarrisonableBuildingAddon garr;
                    if ((garr = building.getBuilding().getActiveAddon(GarrisonableBuildingAddon.class)) != null) {
                        for (LivingEntity le : garr.getOccupants(building))
                            le.hurt(exp.getDamageSource(), (random.nextFloat(atkDmg + 1)) / 2f);
                    }

                    // a unit's explosion brings the building down faster (BuildingDamage); the occupants above take the old hit
                    building.destroyRandomBlocks(byUnit ? BuildingDamage.unit(atkDmg) : atkDmg, building.lastAttacker);
                }

            }
        }
        // don't do any block damage apart from the scripted building damage above or damage to leaves/tnt
        if (serverLevel == null || !serverLevel.getGameRules().getRule(GameRuleRegistrar.DO_UNIT_GRIEFING).get()) {
            evt.getAffectedBlocks().removeIf(bp -> {
                BlockState bs = evt.getLevel().getBlockState(bp);
                return !(bs.getBlock() instanceof LeavesBlock) && !(bs.getBlock() instanceof TntBlock);
            });
        }

    }

    @SubscribeEvent
    public static void onEntityTravelToDimension(EntityTravelToDimensionEvent evt) {
        BuildingPlacement building = BuildingUtils.findBuilding(evt.getEntity().level().isClientSide(), evt.getEntity().getOnPos());
        if (building != null) {
            evt.setCanceled(true);

            if (evt.getEntity() instanceof ServerPlayer player &&
                !player.isSpectator() &&
                (AlliancesServerEvents.isAllied(player.getName().getString(), building.ownerName) ||
                building.getBuilding() instanceof NeutralTransportPortal ||
                player.getName().getString().equals(building.ownerName)) &&
                building instanceof PortalPlacement portal &&
                portal.hasDestination()) {

                player.teleportTo(portal.destination.getX(), portal.destination.getY(), portal.destination.getZ());
                building.level.playSound(null, building.centrePos, SoundEvents.ENDERMAN_TELEPORT,
                        player.getSoundSource(), 1.0F, 1.0F);
                building.level.playSound(null, portal.destination, SoundEvents.ENDERMAN_TELEPORT,
                        player.getSoundSource(), 1.0F, 1.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onCropTrample(BlockEvent.FarmlandTrampleEvent evt) {
        if (BuildingUtils.isPosInsideAnyBuilding(evt.getEntity().level().isClientSide(), evt.getPos())) {
            evt.setCanceled(true);
        }
    }

    // prevent crops from becoming items when a farm is damaged
    @SubscribeEvent
    public static void onItemDrop(EntityJoinLevelEvent evt) {
        if (evt.getEntity() instanceof ItemEntity ie && BuildingUtils.isPosInsideFarm(evt.getLevel().isClientSide(), ie.getOnPos())) {
            Item item = ie.getItem().getItem();
            if (List.of(Items.NETHER_WART, Items.WHEAT, Items.CARROT, Items.BEETROOT, Items.POTATO).contains(item)) {
                evt.setCanceled(true);
            }
        }
    }

    public static void replaceClientBuilding(BlockPos buildingPos) {
        if (!PlayerServerEvents.rtsSyncingEnabled) {
            return;
        }
        for (BuildingPlacement building : buildings) {
            if (building.originPos.equals(buildingPos)) {
                BuildingClientboundPacket.placeBuilding(
                        building.originPos,
                        building.getBuilding(),
                        building.rotation,
                        building.ownerName,
                        building.scenarioRoleIndex,
                        building.blockPlaceQueue.size(),
                        building.getBuilding() instanceof AbstractBridge && building.isDiagonalBridge,
                        building.getUpgradeLevel(),
                        building.isBuilt,
                        building instanceof PortalPlacement p ? p.getPortalType() : PortalPlacement.PortalType.BASIC,
                        building instanceof PortalPlacement p && p.getPortalType() == PortalPlacement.PortalType.TRANSPORT ? p.destination : new BlockPos(0,0,0)
                );
                return;
            }
        }
    }
}
