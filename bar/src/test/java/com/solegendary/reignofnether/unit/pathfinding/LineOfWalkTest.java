package com.solegendary.reignofnether.unit.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Walking a path in straight lines (LineOfWalk, PathNavigationMixin): on open floor a staircase path is walked
 * straight, and never where the straight line would take the body - its full width - over a cell the node path went
 * round (a hole or ledge, water, a wall's corner) or onto another height. A grid of cells, all standable but `blocked`.
 */
class LineOfWalkTest {

    private static final int Y = 64;

    private static LineOfWalk.Cells floor(Set<Long> blocked) {
        return (x, y, z) -> y == Y && !blocked.contains(BlockPos.asLong(x, 0, z));
    }

    private static long cell(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    /** A path through the given (x, z) cells at height Y, unless a third value gives the height. */
    private static Path path(int[]... cells) {
        List<Node> nodes = new ArrayList<>();
        for (int[] c : cells)
            nodes.add(new Node(c[0], c.length > 2 ? c[2] : Y, c[1]));
        Node end = nodes.get(nodes.size() - 1);
        return new Path(nodes, new BlockPos(end.x, end.y, end.z), true);
    }

    // a staircase 12 east, 2 south: east, east, south-east, east, east, south-east, east...
    private static Path staircase() {
        return path(new int[] { 0, 0 }, new int[] { 1, 0 }, new int[] { 2, 0 }, new int[] { 3, 1 }, new int[] { 4, 1 },
            new int[] { 5, 1 }, new int[] { 6, 2 }, new int[] { 7, 2 }, new int[] { 8, 2 });
    }

    @Test
    void aStaircaseOnOpenFloorIsWalkedStraight() {
        Path p = staircase();
        p.setNextNodeIndex(1);
        int to = LineOfWalk.farthest(0.5, 0.5, Y, 0.3, p, floor(Set.of()));
        assertEquals(p.getNodeCount() - 1, to, "aims at the far end of the staircase on open floor");
    }

    @Test
    void aHoleTheNodesWentRoundIsNotCrossed() {
        // the straight line from (0.5, 0.5) to node 7 (7.5, 2.5) crosses (4, 1)'s neighbour (4, 2) - a hole the nodes
        // stepped round; so it may not aim past the hole
        Set<Long> holes = new HashSet<>(Set.of(cell(3, 2), cell(4, 2)));
        Path p = staircase();
        p.setNextNodeIndex(1);
        int to = LineOfWalk.farthest(0.5, 0.5, Y, 0.3, p, floor(holes));
        Node n = p.getNode(to);
        assertTrue(LineOfWalk.clearLine(0.5, 0.5, n.x + 0.5, n.z + 0.5, Y, 0.4, floor(holes)),
            "the node it aims at is straight-walkable");
        assertTrue(to < 7, "never aims across the hole: " + to);
    }

    @Test
    void aWideBodyDoesNotShaveAWallCorner() {
        // a wall block at (3, -1), beside the route on its outer side: a 0.6-wide body's line passes well clear, a
        // 3-wide body's would graze it, so it keeps the nodes
        Set<Long> wall = Set.of(cell(3, -1));
        Path p = staircase();
        p.setNextNodeIndex(1);
        int narrow = LineOfWalk.farthest(0.5, 0.5, Y, 0.3, p, floor(wall));
        int wide = LineOfWalk.farthest(0.5, 0.5, Y, 1.4, p, floor(wall));
        assertEquals(p.getNodeCount() - 1, narrow, "the narrow body walks straight past the wall");
        assertEquals(1, wide, "the wide body keeps the nodes");
        assertTrue(!LineOfWalk.clearLine(0.5, 0.5, 7.5, 2.5, Y, 1.5, floor(wall)), "its straight line would graze the wall");
    }

    @Test
    void aWideBodyIsCheckedAlongTheLineItReallyWalks() {
        // a 1.4-wide body (an Iron Golem): vanilla walks it to each node's cell + 1.0 on x and z (Path.getEntityPosAtNode:
        // the 2x2 box its evaluator cleared), not the cell's centre. A wall at (5, 3) sits off every node's box, clear of
        // every line to a cell centre, but the real line to the far end grazes it
        Set<Long> wall = Set.of(cell(5, 3));
        Path p = staircase();
        p.setNextNodeIndex(1);
        double hw = 0.7, off = 1.0;
        int to = LineOfWalk.farthest(off, off, Y, hw, p, floor(wall), j -> new Vec3(p.getNode(j).x + off, Y, p.getNode(j).z + off));
        Node n = p.getNode(to);
        assertTrue(LineOfWalk.clearLine(off, off, n.x + off, n.z + off, Y, hw + 0.1, floor(wall)),
            "the line it really walks to node " + to + " (" + (n.x + off) + ", " + (n.z + off) + ") grazes the wall");
        assertTrue(to > 1, "it still walks straight as far as the wall allows: " + to);
    }

    @Test
    void itNeverAimsOntoAnotherHeight() {
        // the path steps up a block at node 4: the line stops at the last node on its own floor
        Path p = path(new int[] { 0, 0 }, new int[] { 1, 0 }, new int[] { 2, 1 }, new int[] { 3, 1 },
            new int[] { 4, 2, Y + 1 }, new int[] { 5, 2, Y + 1 }, new int[] { 6, 3, Y + 1 });
        p.setNextNodeIndex(1);
        assertEquals(3, LineOfWalk.farthest(0.5, 0.5, Y, 0.3, p, floor(Set.of())));
    }

    @Test
    void aStraightRunIsLeftToVanilla() {
        Path p = path(new int[] { 0, 0 }, new int[] { 1, 0 }, new int[] { 2, 0 }, new int[] { 3, 0 }, new int[] { 4, 0 });
        p.setNextNodeIndex(1);
        assertEquals(1, LineOfWalk.farthest(0.5, 0.5, Y, 0.3, p, floor(Set.of())), "nothing to straighten");
    }

    @Test
    void aBodyThatCannotStandOnTheLineKeepsTheNodes() {
        // water (or lava's edge, a door, a fence: anything not plain walkable floor) on every cell off the node chain
        Path p = staircase();
        Set<Long> chain = new HashSet<>();
        for (int i = 0; i < p.getNodeCount(); i++)
            chain.add(cell(p.getNode(i).x, p.getNode(i).z));
        LineOfWalk.Cells onlyTheChain = (x, y, z) -> y == Y && chain.contains(cell(x, z));
        p.setNextNodeIndex(1);
        int to = LineOfWalk.farthest(0.5, 0.5, Y, 0.3, p, onlyTheChain);
        assertTrue(to <= 2, "a line off the chain crosses water, so it walks the nodes: " + to);
    }
}
