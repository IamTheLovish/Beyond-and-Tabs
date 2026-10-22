package com.solegendary.reignofnether.hud.buttons;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.neutral.Beacon;
import com.solegendary.reignofnether.building.buildings.placements.BeaconPlacement;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.hud.HudClientEvents.hudSelectedPlacement;
import static com.solegendary.reignofnether.unit.UnitClientEvents.getPlayerToEntityRelationship;
import static com.solegendary.reignofnether.unit.UnitClientEvents.idleWorkerIds;
import static com.solegendary.reignofnether.util.MiscUtil.fcs;

public class HelperButtons {

    private static final Minecraft MC = Minecraft.getInstance();

    public static final int ICON_SIZE = 14;

    private static int idleWorkerIndex = 0;
    public static Button idleWorkerButton;
    public static Button idleFactoryButton;
    public static Button chatButton;
    public static Button buildingCancelButton;
    public static Button armyButton;
    public static Button menuButton;

    /**
     * Select the next idle worker and centre the camera on it (BAR's idle-constructor cycle), or with {@code all}
     * every idle worker at once. Safe to call with none idle: it does nothing.
     */
    public static void cycleIdleWorker(boolean all) {
        if (MC.level == null || idleWorkerIds.isEmpty())
            return;
        if (all) {
            UnitClientEvents.clearSelectedUnits();
            for (int id : idleWorkerIds) {
                Entity entity = MC.level.getEntity(id);
                if (entity instanceof WorkerUnit)
                    UnitClientEvents.addSelectedUnit((LivingEntity) entity);
            }
            return;
        }
        if (idleWorkerIndex >= idleWorkerIds.size())
            idleWorkerIndex = 0; // Reset to zero if out of bounds

        Entity entity = MC.level.getEntity(idleWorkerIds.get(idleWorkerIndex));
        if (entity instanceof WorkerUnit) {
            OrthoviewClientEvents.centreCameraOnPos(entity.position());
            UnitClientEvents.clearSelectedUnits();
            UnitClientEvents.addSelectedUnit((LivingEntity) entity);
        }
        idleWorkerIndex += 1;

        // Reset idleWorkerIndex if it exceeds the size after increment
        if (idleWorkerIndex >= idleWorkerIds.size())
            idleWorkerIndex = 0;
    }

    public static void updateButtons() {
        chatButton = new Button(
                "Chat",
                ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/speech_bubble.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                () -> true,
                () -> MC.setScreen(new ChatScreen("")),
                null,
                List.of(FormattedCharSequence.forward(I18n.get("hud.helperbuttons.reignofnether.chat"), Style.EMPTY))
        );
        // BAR's idle-constructor cycler: click (or "." / F10) centres on the next idle worker and selects it,
        // CTRL-click selects them all; the count rides on the tile as a badge (synced by UnitIdleWorkerClientBoundPacket)
        idleWorkerButton = new Button(
                "Idle workers (CTRL-click to select all)",
                ICON_SIZE,
                getIdleWorkerIcon(),
                Keybindings.idleWorker,
                () -> false,
                idleWorkerIds::isEmpty,
                () -> true,
                () -> cycleIdleWorker(Keybindings.ctrlMod.isDown()),
                null,
                List.of(
                        FormattedCharSequence.forward(I18n.get("hud.helperbuttons.reignofnether.idle_workers"), Style.EMPTY),
                        FormattedCharSequence.forward(I18n.get("hud.helperbuttons.reignofnether.idle_workers_shift"), Style.EMPTY)
                )
        );
        idleWorkerButton.bottomLeftText = () -> String.valueOf(idleWorkerIds.size());
        // BAR's idle-factory counter beside the idle-worker one: click cycles the labs with an empty queue
        // (IdleFactories keeps the list), CTRL-click selects them all
        idleFactoryButton = new Button(
                "Idle factories",
                ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/idle_factory.png"),
                (Keybinding) null,
                () -> false,
                () -> com.solegendary.reignofnether.hud.IdleFactories.count() == 0,
                () -> true,
                () -> {
                    if (MC.level == null)
                        return;
                    if (Keybindings.ctrlMod.isDown()) {
                        UnitClientEvents.clearSelectedUnits();
                        BuildingClientEvents.clearSelectedBuildings();
                        for (BuildingPlacement b : com.solegendary.reignofnether.hud.IdleFactories.get())
                            BuildingClientEvents.addSelectedBuilding(b);
                    } else {
                        com.solegendary.reignofnether.hud.IdleFactories.cycle();
                    }
                },
                null,
                List.of(
                        FormattedCharSequence.forward(I18n.get("hud.helperbuttons.reignofnether.idle_factories"), Style.EMPTY),
                        FormattedCharSequence.forward(I18n.get("hud.helperbuttons.reignofnether.idle_workers_shift"), Style.EMPTY)
                )
        );
        idleFactoryButton.bottomLeftText = () -> String.valueOf(com.solegendary.reignofnether.hud.IdleFactories.count());
        buildingCancelButton = new Button(
                "Cancel",
                ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/barrier.png"),
                Keybindings.cancelBuild,
                () -> false,
                () -> {
                    if (hudSelectedPlacement == null)
                        return false;
                    boolean isSandboxPlayer = MC.player != null && SandboxClientEvents.isSandboxPlayer(MC.player.getName().getString());
                    return BuildingUtils.getTotalCompletedBuildingsOwned(true, hudSelectedPlacement.ownerName) == 0 &&
                            !isSandboxPlayer;
                },
                () -> true,
                () -> {
                    if (MC.player != null)
                        BuildingServerboundPacket.cancelBuilding(hudSelectedPlacement.minCorner, MC.player.getName().getString());
                    hudSelectedPlacement = null;
                },
                null,
                List.of(FormattedCharSequence.forward(I18n.get("hud.helperbuttons.reignofnether.cancel"), Style.EMPTY))
        );
        // BAR's top-bar Menu button (lovish, Oct 21): Esc no longer opens the match menu in the RTS view (it cancels and
        // deselects), so the menu has its own button; Shift+Esc opens it from the keyboard
        menuButton = new Button(
                "Menu",
                ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/menu.png"),
                (Keybinding) null,
                () -> false,
                () -> false,
                () -> true,
                com.solegendary.reignofnether.guiscreen.TopdownGuiClientEvents::openMatchMenu,
                null,
                List.of(FormattedCharSequence.forward("Menu (Shift+Esc)", Style.EMPTY))
        );
        armyButton = new Button(
                "Select all military units",
                ICON_SIZE,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/sword_and_bow.png"),
                Keybindings.selectArmy,
                () -> false,
                () -> {
                    var flag = false;
                    for (LivingEntity u : UnitClientEvents.getAllUnits()) {
                        if (!(u instanceof WorkerUnit) &&
                            GarrisonableBuildingAddon.getGarrison((Unit) u) == null &&
                            getPlayerToEntityRelationship(u) == Relationship.OWNED) {
                            flag = true;
                            break;
                        }
                    }
                    return !flag;
                },
                () -> true,
                () -> {
                    List<LivingEntity> militaryUnits = new ArrayList<>();

                    if (Keybindings.shiftMod.isDown()) {
                        militaryUnits.addAll(UnitClientEvents.getMilitaryUnitsOnScreen());
                    } else {
                        for (LivingEntity u : UnitClientEvents.getAllUnits()) {
                            if (u instanceof Unit unit &&
                                !(u instanceof WorkerUnit) &&
                                !(unit.isScout()) &&
                                GarrisonableBuildingAddon.getGarrison(unit) == null &&
                                getPlayerToEntityRelationship(u) == Relationship.OWNED) {
                                militaryUnits.add(u);
                            }
                        }
                    }
                    UnitClientEvents.clearSelectedUnits();
                    for (LivingEntity militaryUnit : militaryUnits)
                        UnitClientEvents.addSelectedUnit(militaryUnit);
                    HudClientEvents.setLowestCdHudEntity();
                },
                null,
                List.of(
                        fcs(I18n.get("hud.helperbuttons.reignofnether.select_all_military_units"), Style.EMPTY),
                        fcs(I18n.get("hud.helperbuttons.reignofnether.select_all_military_units_shift"), Style.EMPTY)
                )
        );
    }

    private static ResourceLocation getIdleWorkerIcon() {
        return PlayerClientEvents.getFaction().idleWorkerIcon;
    }

    private static List<FormattedCharSequence> getBeaconButtonTooltip(String ownerName) {
        ArrayList<FormattedCharSequence> fcsList = new ArrayList<>();
        BeaconPlacement beacon = BuildingUtils.getBeacon(true);
        if (beacon == null)
            return fcsList;

        fcsList.add(fcs(I18n.get("hud.helperbuttons.reignofnether.beacon.beacon_level_title",
                beacon.getUpgradeLevel(), Beacon.MAX_UPGRADE_LEVEL)));

        if (beacon.getUpgradeLevel() < Beacon.MAX_UPGRADE_LEVEL) {
            fcsList.add(fcs(I18n.get("hud.helperbuttons.reignofnether.beacon.player_controls", ownerName), true));
        } else {
            boolean noController = true;
            for (RTSPlayer rtsPlayer : PlayerClientEvents.rtsPlayers) {
                if (rtsPlayer.beaconOwnerTicks > 0) {
                    noController = false;
                    break;
                }
            }
            if (noController) {
                fcsList.add(fcs(I18n.get("hud.helperbuttons.reignofnether.beacon.no_controller")));
            } else {
                for (RTSPlayer rtsPlayer : PlayerClientEvents.rtsPlayers) {
                    long ticksToWin = Math.max(0, Beacon.getTicksToWin(beacon.getLevel()) - rtsPlayer.beaconOwnerTicks);
                    String timeToWin = TimeUtils.getTimeStrFromTicks(ticksToWin);
                    fcsList.add(fcs(I18n.get("hud.helperbuttons.reignofnether.beacon.player_wins_in",
                            rtsPlayer.name, timeToWin), ownerName.equals(rtsPlayer.name)));
                }
            }
        }

        fcsList.add(fcs(I18n.get("hud.helperbuttons.reignofnether.beacon.click_to_centre")));
        return fcsList;
    }

    // button that tracks all beacons in the game, including how long each player has owned a beacon for
    // clicking the button should make
    public static Button getBeaconButton(String ownerName) {
        return new Button(
                "Beacon",
                14,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/nether_star.png"),
                (Keybinding) null,
                () -> false,
                () -> BuildingUtils.getBeacon(true) == null,
                () -> true,
                () -> {
                    List<BuildingPlacement> beacons = new ArrayList<>();
                    for (BuildingPlacement b : BuildingClientEvents.getBuildings()) {
                        if (b instanceof BeaconPlacement) {
                            beacons.add(b);
                        }
                    }
                    if (!beacons.isEmpty()) {
                        BlockPos bp = beacons.get(0).centrePos;
                        OrthoviewClientEvents.centreCameraOnPos(bp);
                    }
                },
                null,
                getBeaconButtonTooltip(ownerName)
        );
    }

    static {
        updateButtons();
    }
}
