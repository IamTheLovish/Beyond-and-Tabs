package com.solegendary.reignofnether.unit.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a wide unit's failed full-range search answers again (WidePathMemo), and when it must not. */
class WidePathMemoTest {

    static final BlockPos FROM = new BlockPos(10, 64, 10);
    static final Set<BlockPos> TARGETS = Set.of(new BlockPos(20, 74, 10));

    @Test
    void aFailedSearchAnswersForTheSameTargetsFromTheSameSpotUntilItExpires() {
        WidePathMemo m = new WidePathMemo();
        assertFalse(m.failedLately(FROM, TARGETS, 0), "nothing searched yet");
        m.fullSearched(FROM, TARGETS, 100, false);
        assertEquals(1, m.fullSearches);
        assertTrue(m.failedLately(FROM, TARGETS, 100));
        assertTrue(m.failedLately(FROM, new HashSet<>(TARGETS), 100 + WidePathMemo.TICKS - 1), "any set with the same targets");
        assertFalse(m.failedLately(FROM, TARGETS, 100 + WidePathMemo.TICKS), "expired");
    }

    @Test
    void aSidestepOrAnotherRingSlotIsTheSameSearchAWalkIsNot() {
        WidePathMemo m = new WidePathMemo();
        m.fullSearched(FROM, TARGETS, 0, false);
        assertTrue(m.failedLately(FROM.offset(1, 0, -1), TARGETS, 1));
        assertTrue(m.failedLately(FROM.offset(WidePathMemo.NEAR, 0, 0), TARGETS, 1));
        assertFalse(m.failedLately(FROM.offset(WidePathMemo.NEAR + 1, 0, 0), TARGETS, 1));
        assertFalse(m.failedLately(FROM.offset(0, 0, -WidePathMemo.NEAR - 1), TARGETS, 1));
        assertTrue(m.failedLately(FROM, Set.of(new BlockPos(22, 74, 8)), 1), "another slot round the same target");
        assertFalse(m.failedLately(FROM, Set.of(new BlockPos(20 + WidePathMemo.NEAR + 1, 74, 10)), 1), "another target");
        assertFalse(m.failedLately(FROM, Set.of(new BlockPos(20, 74, 10), new BlockPos(30, 74, 10)), 1), "more targets");
    }

    @Test
    void otherTargetsOrAFoundWaySearchAgain() {
        WidePathMemo m = new WidePathMemo();
        m.fullSearched(FROM, TARGETS, 0, false);
        assertFalse(m.failedLately(FROM, Set.of(new BlockPos(40, 74, 10)), 1), "a target elsewhere");
        m.fullSearched(FROM, TARGETS, 2, true);
        assertFalse(m.failedLately(FROM, TARGETS, 3), "a complete path forgets the failure");
        assertEquals(2, m.fullSearches);
    }

    @Test
    void aMutablePositionIsCopied() {
        WidePathMemo m = new WidePathMemo();
        BlockPos.MutableBlockPos from = FROM.mutable();
        BlockPos.MutableBlockPos target = new BlockPos(20, 74, 10).mutable();
        m.fullSearched(from, Set.of(target), 0, false);
        from.move(10, 0, 0);
        target.move(0, 0, 5);
        assertTrue(m.failedLately(FROM, TARGETS, 1), "the memo keeps where it was searched, not the caller's cursor");
    }

    // Oct 31 (lovish's game 03:14, headhunters): any unit's failed search answers again for REPEAT_TICKS

    static Path partial() {
        return new Path(List.of(), new BlockPos(20, 74, 10), false);
    }

    @Test
    void aFailedSearchOfAnyRangeIsHandedBackForASecond() {
        WidePathMemo m = new WidePathMemo();
        Path p = partial();
        assertNull(m.repeatOf(FROM, TARGETS, 0), "nothing searched yet");
        m.searched(FROM, TARGETS, 100, p);
        assertEquals(1, m.searches);
        assertSame(p, m.repeatOf(FROM, TARGETS, 100));
        assertSame(p, m.repeatOf(FROM.offset(1, 0, 0), Set.of(new BlockPos(21, 74, 11)), 100 + WidePathMemo.REPEAT_TICKS - 1),
            "about the same spot and targets");
        assertNull(m.repeatOf(FROM, TARGETS, 100 + WidePathMemo.REPEAT_TICKS), "a second later it searches again");
        assertNull(m.repeatOf(FROM.offset(WidePathMemo.NEAR + 1, 0, 0), TARGETS, 101), "walked off");
        assertNull(m.repeatOf(FROM, Set.of(new BlockPos(40, 74, 10)), 101), "another target");
    }

    @Test
    void aCompletePathOrNoPathIsNeverHandedBack() {
        WidePathMemo m = new WidePathMemo();
        m.searched(FROM, TARGETS, 0, partial());
        m.searched(FROM, TARGETS, 1, new Path(List.of(), new BlockPos(20, 74, 10), true));
        assertNull(m.repeatOf(FROM, TARGETS, 2), "a way was found: search as usual");
        m.searched(FROM, TARGETS, 3, null);
        assertNull(m.repeatOf(FROM, TARGETS, 4), "vanilla's no-path answer is not held");
        assertEquals(3, m.searches);
    }
}
