package com.solegendary.reignofnether.building.merge;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerboundPacket;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.time.ClientTicks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client side of merged buildings: runs the client registry ({@link MergedBuildings#CLIENT}) once a tick over
 * the buildings this client knows, tells the player what changed in their own base (the HUD line - "Farm formed",
 * "Quarter lost a section: now a Row + a Pair"), names the shapes, and puts the merge switch on an own mergeable
 * building's command card. Client only: nothing here is referenced on the server.
 */
public final class MergedBuildingsClient {
    private MergedBuildingsClient() { }

    private static final Minecraft MC = Minecraft.getInstance();

    /** When this client first saw a built member at each origin: a "formed" line is for a merge that just happened,
     *  not for the complexes that arrive whole when the player joins or when the match loads. By origin, so a building
     *  the server re-sends (a new object at the same place) is not "new"; a mill rebuilt where one fell is, because
     *  the fallen one's entry went when it left the list. */
    private static final Map<BlockPos, Long> firstBuiltSeen = new HashMap<>();
    static final int FRESH_TICKS = 60;
    /** The buildings of a match arrive in the first seconds after joining: while they do, a complex whose members
     *  all arrived in the same tick is one that already stood. */
    static final int JOIN_SETTLE_TICKS = 200;
    private static long levelSince = Long.MIN_VALUE;
    private static int lastListSize = -1;

    /** Once a client tick, after the buildings ticked (BuildingClientEvents.onClientTick). */
    public static void tick() {
        if (MC.level == null) {
            MergedBuildings.CLIENT.clear();
            firstBuiltSeen.clear();
            levelSince = Long.MIN_VALUE;
            lastListSize = -1;
            return;
        }
        long now = ClientTicks.now();
        if (levelSince == Long.MIN_VALUE)
            levelSince = now;
        List<BuildingPlacement> all = BuildingClientEvents.getBuildings();
        for (BuildingPlacement p : all)
            if (MergedBuildings.isCandidate(p) && !firstBuiltSeen.containsKey(p.originPos))
                firstBuiltSeen.put(p.originPos, now);
        if (all.size() != lastListSize) {
            lastListSize = all.size();
            Set<BlockPos> origins = new HashSet<>();
            for (BuildingPlacement p : all)
                origins.add(p.originPos);
            firstBuiltSeen.keySet().removeIf(o -> !origins.contains(o));
        }
        List<MergedBuildings.Change> changes = MergedBuildings.CLIENT.update(all, now);
        if (!changes.isEmpty())
            report(changes, now);
        MergedBuildings.CLIENT_PLANNED.update(all, now);   // what the grids under construction will become (the card)
    }

    /** How many of a planned complex's members already stand. */
    public static int built(MergedComplex planned) {
        int n = 0;
        for (BuildingPlacement m : planned.members)
            if (m.isBuilt)
                n++;
        return n;
    }

    /**
     * Rebuilding where a section fell (claude/merged-buildings-animation.md 2: "the ruin can be rebuilt"): when the
     * ghost of the same building is over the footprint of a section an own complex lost, the cursor snaps to that
     * footprint's middle, so the mill drops exactly into the 3x3 hole between its neighbours instead of being
     * wiggled until the overlap check lets it through. Null when the cursor is not over a lost section.
     */
    public static BlockPos snapToLostSection(com.solegendary.reignofnether.building.Building toPlace, BlockPos cursor) {
        if (toPlace == null || cursor == null || MC.player == null)
            return null;
        String me = MC.player.getName().getString();
        for (MergedComplex c : MergedBuildings.CLIENT.complexes()) {
            if (c.building != toPlace || !me.equals(c.ownerName) || c.lostSections.isEmpty())
                continue;
            BuildingPlacement sample = c.members.get(0);
            int w = sample.maxCorner.getX() - sample.minCorner.getX() + 1;
            int d = sample.maxCorner.getZ() - sample.minCorner.getZ() + 1;
            for (BlockPos lost : c.lostSections)
                if (cursor.getX() >= lost.getX() && cursor.getX() < lost.getX() + w
                        && cursor.getZ() >= lost.getZ() && cursor.getZ() < lost.getZ() + d)
                    return new BlockPos(lost.getX() + w / 2, cursor.getY(), lost.getZ() + d / 2);
        }
        return null;
    }

    private static boolean own(MergedComplex c) {
        return c != null && MC.player != null && MC.player.getName().getString().equals(c.ownerName);
    }

    /**
     * A merge that just happened: a member finished in the last {@link #FRESH_TICKS}, and - while the match's buildings
     * are still arriving after a join - not every member in the same tick.
     */
    private static boolean fresh(MergedComplex c, long now) {
        long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        for (BuildingPlacement m : c.members) {
            Long t = firstBuiltSeen.get(m.originPos);
            if (t == null)
                continue;
            min = Math.min(min, t);
            max = Math.max(max, t);
        }
        if (max == Long.MIN_VALUE || now - max > FRESH_TICKS)
            return false;
        return min < max || now - levelSince > JOIN_SETTLE_TICKS;
    }

    private static void report(List<MergedBuildings.Change> changes, long now) {
        // losses first, grouped by the complex that lost: one line names every piece it broke into
        Map<MergedComplex, List<MergedComplex>> broke = new LinkedHashMap<>();
        for (MergedBuildings.Change ch : changes) {
            if (ch.destroyed() <= 0 || !own(ch.previous()))
                continue;
            if (ch.kind() == MergedBuildings.ChangeKind.SHRUNK || ch.kind() == MergedBuildings.ChangeKind.DISSOLVED)
                broke.computeIfAbsent(ch.previous(), k -> new ArrayList<>());
            if (ch.kind() == MergedBuildings.ChangeKind.SHRUNK)
                broke.get(ch.previous()).add(ch.complex());
        }
        for (Map.Entry<MergedComplex, List<MergedComplex>> e : broke.entrySet()) {
            MergedComplex was = e.getKey();
            if (e.getValue().isEmpty()) {
                HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.merged.broken", shapeName(was.shape)));
            } else {
                List<MergedShape> shapes = new ArrayList<>();
                for (MergedComplex c : e.getValue())
                    shapes.add(c.shape);
                HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.merged.shrunk", shapeName(was.shape), shapeList(shapes, 0)));
            }
        }
        for (MergedBuildings.Change ch : changes) {
            if (ch.kind() != MergedBuildings.ChangeKind.FORMED && ch.kind() != MergedBuildings.ChangeKind.GREW)
                continue;
            MergedComplex c = ch.complex();
            if (!own(c) || !fresh(c, now))
                continue;
            HudClientEvents.showTemporaryMessage(I18n.get("hud.reignofnether.merged.formed", shapeName(c.shape),
                c.members.size(), c.shape.bonusPercent()));
        }
    }

    /** "Pair", "Farm", "Row", "Quarter" ... */
    public static String shapeName(MergedShape shape) {
        return I18n.get(shape.nameKey());
    }

    /** "Quarter (+12%) + Pair (+4%) + 1 standing alone" for a grid cut, or "" when nothing merges. */
    public static String summary(List<MergeGrid.Match> matches, int singles) {
        if (matches.isEmpty())
            return "";
        List<MergedShape> shapes = new ArrayList<>();
        for (MergeGrid.Match m : matches)
            shapes.add(m.shape());
        return shapeList(shapes, singles);
    }

    /** "Quarter (+12%) + 2 x Pair (+4%) + 1 standing alone": biggest first, repeats counted, singles last. */
    static String shapeList(List<MergedShape> shapes, int singles) {
        StringBuilder sb = new StringBuilder();
        MergedShape[] ladder = MergedShape.values();
        for (int i = ladder.length - 1; i >= 0; i--) {
            int n = 0;
            for (MergedShape s : shapes)
                if (s == ladder[i])
                    n++;
            if (n == 0)
                continue;
            if (sb.length() > 0)
                sb.append(" + ");
            String part = I18n.get("hud.reignofnether.merged.part", shapeName(ladder[i]), ladder[i].bonusPercent());
            sb.append(n > 1 ? I18n.get("hud.reignofnether.merged.parts", n, part) : part);
        }
        if (singles > 0) {
            if (sb.length() > 0)
                sb.append(" + ");
            sb.append(I18n.get("hud.reignofnether.merged.singles", singles));
        }
        return sb.toString();
    }

    // ---- the merge switch on the command card ----

    private static final ResourceLocation ICON_ON = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/lightbulb_on.png");
    private static final ResourceLocation ICON_OFF = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/lightbulb_off.png");

    /**
     * The "Merge with neighbours" switch of an own mergeable building (BuildingPlacement.updateButtons): lit when on.
     * Clicking sets every selected building of the same type to the new value - a complex is selected as one, so
     * one click splits or re-forms the whole thing (BuildingBatchServerboundPacket's SET_REPEAT convention).
     */
    public static AbilityButton toggleButton(BuildingPlacement bp) {
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(FormattedCharSequence.forward(I18n.get("hud.reignofnether.merged.toggle"), Style.EMPTY.withBold(true)));
        tooltip.add(FormattedCharSequence.forward(I18n.get(bp.mergeEnabled ? "hud.reignofnether.merged.toggle.on"
            : "hud.reignofnether.merged.toggle.off"), Style.EMPTY));
        tooltip.add(FormattedCharSequence.forward(I18n.get("hud.reignofnether.merged.toggle.how",
            MergedShape.PAIR.bonusPercent(), MergedShape.FARM.bonusPercent(), MergedShape.ROW.bonusPercent(),
            MergedShape.QUARTER.bonusPercent()), Style.EMPTY));
        tooltip.add(FormattedCharSequence.forward(I18n.get("hud.reignofnether.merged.toggle.click"), Style.EMPTY));
        return new AbilityButton(
            "Merge",
            bp.mergeEnabled ? ICON_ON : ICON_OFF,
            null,
            () -> bp.mergeEnabled,
            () -> !bp.getBuilding().mergeable
                || BuildingClientEvents.getPlayerToBuildingRelationship(bp) != Relationship.OWNED,
            () -> true,
            () -> click(bp),
            null,
            tooltip,
            null,
            bp
        );
    }

    static void click(BuildingPlacement bp) {
        if (MC.player == null)
            return;
        boolean enable = !bp.mergeEnabled;
        List<BuildingPlacement> targets = new ArrayList<>();
        for (BuildingPlacement b : BuildingClientEvents.getSelectedBuildings())
            if (b.getBuilding() == bp.getBuilding() && BuildingClientEvents.getPlayerToBuildingRelationship(b) == Relationship.OWNED)
                targets.add(b);
        if (!targets.contains(bp))
            targets.add(bp);
        for (BuildingPlacement b : targets) {
            b.mergeEnabled = enable;   // at once, for the card; the server's answer rides the next building sync
            b.updateButtons();
            BuildingServerboundPacket.toggleMerge(b.originPos, enable);
        }
        HudClientEvents.showTemporaryMessage(I18n.get(enable ? "hud.reignofnether.merged.toggle.now_on"
            : "hud.reignofnether.merged.toggle.now_off"));
    }
}
