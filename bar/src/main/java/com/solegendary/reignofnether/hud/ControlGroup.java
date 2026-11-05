package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.solegendary.reignofnether.building.BuildingClientEvents.getPlayerToBuildingRelationship;
import static com.solegendary.reignofnether.building.BuildingClientEvents.getSelectedBuildings;
import static com.solegendary.reignofnether.hud.HudClientEvents.controlGroups;
import static com.solegendary.reignofnether.hud.HudClientEvents.hudSelectedEntity;
import static com.solegendary.reignofnether.hud.HudClientEvents.hudSelectedPlacement;
import static com.solegendary.reignofnether.unit.UnitClientEvents.*;

/**
 * BAR control groups (rules and sources: ControlGroupRules). Groups hold own units and buildings; a unit or building is
 * in one group at most. The number keys are handled on PRESS from HudClientEvents.onKeyPress (onNumberKey), with the
 * key event's own modifier bits, so either Ctrl / Alt / Shift works and the group's HUD button need not be drawn (clean
 * view, F1). The HUD buttons are for the mouse only: their key release is skipped (HudClientEvents.onKeyRelease).
 */
public class ControlGroup {

    // double click to centre the camera on the group (mouse, on the HUD button)
    public long lastClickTime = 0;

    public final ArrayList<Integer> entityIds = new ArrayList<>();
    public final ArrayList<BlockPos> buildingBps = new ArrayList<>(); // origin pos
    private Keybinding keybinding = null;
    private ResourceLocation iconRl = null;

    public ControlGroup() { }

    public ControlGroup(Keybinding keybinding) {
        this.keybinding = keybinding;
    }

    public int getKey() {
        return keybinding != null ? keybinding.getKey() : -1;
    }

    public void clearAll() {
        this.entityIds.clear();
        this.buildingBps.clear();
    }

    public boolean isEmpty() {
        return entityIds.isEmpty() && buildingBps.isEmpty();
    }

    // removes any entities/buildings that are no longer being tracked (likely dead/left world)
    public void clean() {
        if (entityIds.isEmpty() && buildingBps.isEmpty())
            return;
        Set<Integer> ids = new HashSet<>();
        for (LivingEntity livingEntity : getAllUnits())
            ids.add(livingEntity.getId());
        this.entityIds.removeIf(e -> !ids.contains(e));
        Set<BlockPos> poses = new HashSet<>();
        for (BuildingPlacement b2 : BuildingClientEvents.getBuildings())
            poses.add(b2.originPos);
        this.buildingBps.removeIf(b -> !poses.contains(b));
    }

    /**
     * Ctrl+N (newGroup: "group set") or Ctrl+Shift+N ("group add"): the selected own units AND buildings go into this
     * group and leave any other. Also the command language's entry (IntentExecutor).
     */
    public void saveFromSelected(Keybinding keybinding, boolean newGroup) {
        if (keybinding != null)
            this.keybinding = keybinding;
        int n = controlGroups.indexOf(this);
        List<Integer> units = selectedOwnedUnitIds();
        List<BlockPos> buildings = selectedOwnedBuildingBps();
        if (n >= 0) {
            ControlGroupRules.assign(unitLists(), n, units, newGroup);
            ControlGroupRules.assign(buildingLists(), n, buildings, newGroup);
        } else {
            if (newGroup)
                clearAll();
            for (Integer id : units)
                if (!entityIds.contains(id))
                    entityIds.add(id);
            for (BlockPos bp : buildings)
                if (!buildingBps.contains(bp))
                    buildingBps.add(bp);
        }
        if (newGroup || iconRl == null)
            updateIcon();
    }

    private void updateIcon() {
        if (hudSelectedEntity != null) {
            String unitName = MiscUtil.getEntityIconName(hudSelectedEntity);
            iconRl = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/" + unitName + ".png");
        } else if (hudSelectedPlacement != null) {
            iconRl = hudSelectedPlacement.getBuilding().icon;
        } else {
            List<LivingEntity> live = liveUnits();
            if (!live.isEmpty())
                iconRl = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID,
                    "textures/mobheads/" + MiscUtil.getEntityIconName(live.get(0)) + ".png");
        }
    }

    /** The group's units that are still tracked, in group order. */
    public List<LivingEntity> liveUnits() {
        List<LivingEntity> out = new ArrayList<>();
        if (entityIds.isEmpty())
            return out;
        Map<Integer, LivingEntity> byId = new HashMap<>();
        for (LivingEntity e : getAllUnits())
            if (e instanceof Unit)
                byId.put(e.getId(), e);
        for (Integer id : entityIds) {
            LivingEntity e = byId.get(id);
            if (e != null)
                out.add(e);
        }
        return out;
    }

    private List<BuildingPlacement> liveBuildings() {
        List<BuildingPlacement> out = new ArrayList<>();
        if (buildingBps.isEmpty())
            return out;
        Set<BlockPos> want = new HashSet<>(buildingBps);
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (want.contains(b.originPos))
                out.add(b);
        return out;
    }

    /** "group select": the selection becomes the group; units win when it has both (units and buildings never mix here). */
    public boolean select() {
        List<LivingEntity> units = liveUnits();
        if (!units.isEmpty()) {
            BuildingClientEvents.clearSelectedBuildings();
            UnitClientEvents.clearSelectedUnits();
            for (LivingEntity entity : units)
                UnitClientEvents.addSelectedUnitNoSort(entity);
            UnitClientEvents.sortedSelectedUnits();
            HudClientEvents.setLowestCdHudEntity();
            return true;
        }
        List<BuildingPlacement> buildings = liveBuildings();
        if (!buildings.isEmpty()) {
            UnitClientEvents.clearSelectedUnits();
            BuildingClientEvents.clearSelectedBuildings();
            for (BuildingPlacement b : buildings)
                BuildingClientEvents.addSelectedBuilding(b);
            return true;
        }
        return false;
    }

    /** "group selectadd": the group's members join the selection. */
    public void selectAdd() {
        List<LivingEntity> units = liveUnits();
        if (!units.isEmpty()) {
            Set<Integer> have = new HashSet<>();
            for (LivingEntity e : UnitClientEvents.getSelectedUnits())
                have.add(e.getId());
            for (LivingEntity e : units)
                if (have.add(e.getId()))
                    UnitClientEvents.addSelectedUnitNoSort(e);
            UnitClientEvents.sortedSelectedUnits();
            HudClientEvents.setLowestCdHudEntity();
            return;
        }
        if (!UnitClientEvents.getSelectedUnits().isEmpty())
            return;   // units and buildings are never selected together here
        Set<BlockPos> have = new HashSet<>();
        for (BuildingPlacement b : getSelectedBuildings())
            have.add(b.originPos);
        for (BuildingPlacement b : liveBuildings())
            if (have.add(b.originPos))
                BuildingClientEvents.addSelectedBuilding(b);
    }

    /** "group focus": the camera goes to the middle of the group (CGroup::CalculateCenter). */
    public void focus() {
        List<LivingEntity> units = liveUnits();
        if (!units.isEmpty()) {
            double x = 0, y = 0, z = 0;
            for (LivingEntity e : units) {
                x += e.getX();
                y += e.getY();
                z += e.getZ();
            }
            OrthoviewClientEvents.centreCameraOnPos(new Vec3(x / units.size(), y / units.size(), z / units.size()));
            return;
        }
        List<BuildingPlacement> buildings = liveBuildings();
        if (!buildings.isEmpty())
            OrthoviewClientEvents.centreCameraOnPos(buildings.get(0).centrePos);
    }

    // the HUD button's left click: select, a double click also centres the camera
    public void loadToSelected() {
        Minecraft MC = Minecraft.getInstance();
        if (MC.level == null)
            return;
        boolean doubleClicked = (System.currentTimeMillis() - lastClickTime)
            < com.solegendary.reignofnether.config.ReignOfNetherClientConfigs.DOUBLE_CLICK_MS.get();
        if (select() && doubleClicked)
            focus();
        lastClickTime = System.currentTimeMillis();
    }

    public Button getButton() {
        Button b = new Button(
            "Control Group " + getKey(),
            HudLayout.SLIM_ICON,   // the slim 18 px tile (SlimTiles), the groups' icons are only a reminder of the unit type
            iconRl == null ? ResourceLocation.parse("") : iconRl,
            this.keybinding,
            () -> false,
            () -> false,
            () -> true,
            () -> {
                if (!Keybindings.shiftMod.isDown())
                    loadToSelected();
            },
            () -> {
                if (Keybindings.shiftMod.isDown())
                    clearAll();
            },
            List.of(FormattedCharSequence.forward(
                I18n.get("hud.control_group.reignofnether.control_group", keybinding != null ? keybinding.buttonLabel : "?"),
                Style.EMPTY
            ))
        );
        b.imageSize = HudLayout.SLIM_IMAGE;
        return b;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Keys (BAR's key set, see ControlGroupRules)
    // ------------------------------------------------------------------------------------------------------------

    private static final ControlGroupRules.TapTracker TAPS = new ControlGroupRules.TapTracker();
    private static ControlGroupRules.AutoGroups autoGroups = null;
    private static final Set<Integer> seenUnits = new HashSet<>();
    private static final Set<BlockPos> seenBuildings = new HashSet<>();
    private static Object seenLevel = null;
    private static final Set<Integer> heldNumberKeys = new HashSet<>();

    /** Ten groups, 0-9, each on its number key. */
    public static void ensureGroups() {
        if (controlGroups.size() < Keybindings.nums.length) {
            controlGroups.clear();
            for (Keybinding keybinding : Keybindings.nums)
                controlGroups.add(new ControlGroup(keybinding));
            TAPS.reset();
        }
    }

    /** The group a key selects (its binding's number), or -1. */
    public static int groupForKey(int key) {
        for (Keybinding kb : Keybindings.nums)
            if (kb.matches(key))
                return Integer.parseInt(kb.buttonLabel);
        return -1;
    }

    /** A number key went down. True when it was a control-group key (an auto-repeat of it does nothing). */
    public static boolean onNumberKey(int key, int glfwMods) {
        int n = groupForKey(key);
        if (n < 0)
            return false;
        if (!heldNumberKeys.add(key))
            return true;   // auto-repeat of a held key: BAR acts once per press
        ensureGroups();
        ControlGroup g = controlGroups.get(n);
        long window = com.solegendary.reignofnether.config.ReignOfNetherClientConfigs.DOUBLE_CLICK_MS.get();
        switch (ControlGroupRules.actionFor(glfwMods)) {
            case SET -> {
                g.saveFromSelected(null, true);
                TAPS.reset();
            }
            case ADD -> {
                g.saveFromSelected(null, false);
                TAPS.reset();
            }
            case SELECT_ADD -> {
                g.selectAdd();
                TAPS.reset();
            }
            case SELECT -> {
                boolean dbl = TAPS.press(n, System.currentTimeMillis(), window);
                if (g.select() && dbl)
                    g.focus();
            }
            case AUTOGROUP -> {
                addSelectionToAutogroup(n);
                TAPS.reset();
            }
            case NONE -> { }
        }
        return true;
    }

    public static void onKeyReleased(int key) {
        heldNumberKeys.remove(key);
    }

    /** The deselect key (`) with Ctrl ("group unset") or Alt ("remove_from_autogroup"); false for a plain deselect. */
    public static boolean onDeselectKey(int glfwMods) {
        ensureGroups();
        switch (ControlGroupRules.unsetActionFor(glfwMods)) {
            case UNSET -> {
                ControlGroupRules.unassign(unitLists(), selectedOwnedUnitIds());
                ControlGroupRules.unassign(buildingLists(), selectedOwnedBuildingBps());
                return true;
            }
            case REMOVE_AUTOGROUP -> {
                removeSelectionFromAutogroup();
                return true;
            }
            case NONE -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** BAR's Alt+N: the selected types are bound to N; every own unit / building of those types joins N and the selection. */
    public static void addSelectionToAutogroup(int n) {
        ensureGroups();
        Set<String> unitTypes = selectedUnitTypes();
        Set<String> buildingTypes = selectedBuildingTypes();
        if (unitTypes.isEmpty() && buildingTypes.isEmpty())
            return;
        autoGroups().bind(unitTypes, n);
        autoGroups().bind(buildingTypes, n);
        saveAutoGroups();

        List<LivingEntity> joining = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : getAllUnits())
            if (isOwnUnit(e) && unitTypes.contains(unitType(e))) {
                joining.add(e);
                ids.add(e.getId());
            }
        ControlGroupRules.assign(unitLists(), n, ids, false);
        List<BuildingPlacement> joiningBuildings = new ArrayList<>();
        List<BlockPos> bps = new ArrayList<>();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (getPlayerToBuildingRelationship(b) == Relationship.OWNED && buildingTypes.contains(buildingType(b))) {
                joiningBuildings.add(b);
                bps.add(b.originPos);
            }
        ControlGroupRules.assign(buildingLists(), n, bps, false);

        // SelectUnitArray({unitID}, true): they join the selection
        if (!unitTypes.isEmpty()) {
            Set<Integer> have = new HashSet<>();
            for (LivingEntity e : getSelectedUnits())
                have.add(e.getId());
            for (LivingEntity e : joining)
                if (have.add(e.getId()))
                    UnitClientEvents.addSelectedUnitNoSort(e);
            UnitClientEvents.sortedSelectedUnits();
        } else {
            Set<BlockPos> have = new HashSet<>();
            for (BuildingPlacement b : getSelectedBuildings())
                have.add(b.originPos);
            for (BuildingPlacement b : joiningBuildings)
                if (have.add(b.originPos))
                    BuildingClientEvents.addSelectedBuilding(b);
        }
        ControlGroup g = controlGroups.get(n);
        if (g.iconRl == null)
            g.updateIcon();
    }

    /** BAR's Alt+`: the selected types lose their autogroup and every own unit / building of them leaves its group. */
    public static void removeSelectionFromAutogroup() {
        Set<String> unitTypes = selectedUnitTypes();
        Set<String> buildingTypes = selectedBuildingTypes();
        if (unitTypes.isEmpty() && buildingTypes.isEmpty())
            return;
        autoGroups().unbind(unitTypes);
        autoGroups().unbind(buildingTypes);
        saveAutoGroups();
        List<Integer> ids = new ArrayList<>();
        for (LivingEntity e : getAllUnits())
            if (isOwnUnit(e) && unitTypes.contains(unitType(e)))
                ids.add(e.getId());
        ControlGroupRules.unassign(unitLists(), ids);
        List<BlockPos> bps = new ArrayList<>();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (getPlayerToBuildingRelationship(b) == Relationship.OWNED && buildingTypes.contains(buildingType(b)))
                bps.add(b.originPos);
        ControlGroupRules.unassign(buildingLists(), bps);
    }

    /** Once a client tick: new own units and buildings of an autogrouped type join its group (BAR's UnitCreated). */
    public static void tick() {
        Minecraft MC = Minecraft.getInstance();
        // a key let go while another screen had focus never sent its release here
        if (!heldNumberKeys.isEmpty())
            heldNumberKeys.removeIf(k -> !com.mojang.blaze3d.platform.InputConstants.isKeyDown(MC.getWindow().getWindow(), k));
        if (MC.level == null || MC.player == null) {
            seenLevel = null;
            return;
        }
        if (seenLevel != MC.level) {
            // entity ids start over in a new world: what was seen there means nothing here
            seenLevel = MC.level;
            seenUnits.clear();
            seenBuildings.clear();
        }
        ensureGroups();
        ControlGroupRules.AutoGroups auto = autoGroups();
        Map<Integer, String> units = new LinkedHashMap<>();
        for (LivingEntity e : getAllUnits()) {
            if (seenUnits.contains(e.getId()))
                continue;
            if (!auto.view().isEmpty() && isOwnUnit(e))
                units.put(e.getId(), unitType(e));
            else
                seenUnits.add(e.getId());   // binding a type later must not pull in units already there
        }
        if (!units.isEmpty())
            ControlGroupRules.joinNew(unitLists(), units, seenUnits, auto);
        Map<BlockPos, String> buildings = new LinkedHashMap<>();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings()) {
            if (seenBuildings.contains(b.originPos))
                continue;
            if (!auto.view().isEmpty() && getPlayerToBuildingRelationship(b) == Relationship.OWNED)
                buildings.put(b.originPos, buildingType(b));
            else
                seenBuildings.add(b.originPos);
        }
        if (!buildings.isEmpty())
            ControlGroupRules.joinNew(buildingLists(), buildings, seenBuildings, auto);
    }

    /** The group number of every grouped unit id (the numbers drawn on units, ControlGroupNumbers). */
    public static Map<Integer, Integer> groupByUnitId() {
        Map<Integer, Integer> out = new HashMap<>();
        for (int i = 0; i < controlGroups.size(); i++) {
            ControlGroup g = controlGroups.get(i);
            int label = g.keybinding != null ? Integer.parseInt(g.keybinding.buttonLabel) : i;
            for (Integer id : g.entityIds)
                out.put(id, label);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------------------

    private static List<ArrayList<Integer>> unitLists() {
        List<ArrayList<Integer>> out = new ArrayList<>(controlGroups.size());
        for (ControlGroup g : controlGroups)
            out.add(g.entityIds);
        return out;
    }

    private static List<ArrayList<BlockPos>> buildingLists() {
        List<ArrayList<BlockPos>> out = new ArrayList<>(controlGroups.size());
        for (ControlGroup g : controlGroups)
            out.add(g.buildingBps);
        return out;
    }

    private static boolean isOwnUnit(LivingEntity e) {
        return e instanceof Unit && (getPlayerToEntityRelationship(e) == Relationship.OWNED || AlliancesClient.canControlAlly(e));
    }

    private static List<Integer> selectedOwnedUnitIds() {
        List<Integer> out = new ArrayList<>();
        for (LivingEntity e : getSelectedUnits())
            if (getPlayerToEntityRelationship(e) == Relationship.OWNED || AlliancesClient.canControlAlly(e))
                out.add(e.getId());
        return out;
    }

    private static List<BlockPos> selectedOwnedBuildingBps() {
        List<BlockPos> out = new ArrayList<>();
        for (BuildingPlacement b : getSelectedBuildings())
            if (getPlayerToBuildingRelationship(b) == Relationship.OWNED)
                out.add(b.originPos);
        return out;
    }

    private static Set<String> selectedUnitTypes() {
        Set<String> out = new LinkedHashSet<>();
        for (LivingEntity e : getSelectedUnits())
            if (isOwnUnit(e))
                out.add(unitType(e));
        return out;
    }

    private static Set<String> selectedBuildingTypes() {
        Set<String> out = new LinkedHashSet<>();
        for (BuildingPlacement b : getSelectedBuildings())
            if (getPlayerToBuildingRelationship(b) == Relationship.OWNED)
                out.add(buildingType(b));
        return out;
    }

    private static String unitType(LivingEntity e) {
        return "unit:" + BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
    }

    private static String buildingType(BuildingPlacement b) {
        ResourceLocation rl = com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING.getKey(b.getBuilding());
        return "building:" + (rl != null ? rl.toString() : b.getBuilding().getClass().getName());
    }

    // ------------------------------------------------------------------------------------------------------------
    // autogroups are kept across matches, as BAR's widget keeps them in its config data
    // ------------------------------------------------------------------------------------------------------------

    private static Path autoGroupsFile() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("reignofnether-autogroups.txt");
    }

    static ControlGroupRules.AutoGroups autoGroups() {
        if (autoGroups == null) {
            autoGroups = new ControlGroupRules.AutoGroups();
            Path f = autoGroupsFile();
            if (Files.exists(f)) {
                try {
                    autoGroups = ControlGroupRules.AutoGroups.parse(Files.readString(f, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    ReignOfNether.LOGGER.error("[ControlGroup] could not read autogroups from {}: {}", f, e.toString());
                }
            }
        }
        return autoGroups;
    }

    private static void saveAutoGroups() {
        Path f = autoGroupsFile();
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, autoGroups().serialize(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[ControlGroup] could not save autogroups to {}: {}", f, e.toString());
        }
    }
}
