package com.solegendary.reignofnether.unit.support;

import org.junit.jupiter.api.Test;

import com.solegendary.reignofnether.unit.support.SupportMath.Undead;

import static com.solegendary.reignofnether.unit.support.SupportField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The healer AI's rules (claude/healer-ai-oct21.md, section C) on hand-built fields, no game: the GameTests in
 * gametest/SupportAiGameTests prove the same rules end to end with real units. Casts here use the Field Medic's
 * potion shapes (cloud radius 3, splash 4, instant heal and harm INVERTED on the undead) unless a case says otherwise.
 */
class SupportMathTest {

    private static final int ME = F_FRIEND | F_SELF | F_SUPPORT;
    private static final int FRIEND = F_FRIEND | F_ATTACKER | F_MELEE;
    private static final int FOE = F_ENEMY | F_ATTACKER | F_MELEE;

    private static final double CLOUD = 3.0, SPLASH = 4.0;
    private static final Undead INV = Undead.INVERTED;

    private static SupportField field() {
        return new SupportField(64);
    }

    private static int add(SupportField f, double x, double z, int flags) {
        return f.add(null, x, z, 0, 0, 20f, 20f, 1f, flags);
    }

    private static int addHurt(SupportField f, double x, double z, float health, int flags) {
        return f.add(null, x, z, 0, 0, health, 20f, 1f, flags);
    }

    @Test
    void harmingSkipsAnEnemyInMeleeWithAFriend() {
        SupportField f = field();
        add(f, 0, 0, ME);
        add(f, 6, 0, FOE);
        add(f, 7, 0, FRIEND);   // a friend fighting it, one block away
        SupportMath.Aim aim = new SupportMath.Aim();
        assertFalse(SupportMath.harmAim(f, 0, 0, 8, CLOUD, 0, 0, INV, aim), "a cloud on that enemy covers the friend");
    }

    @Test
    void harmingTakesALoneEnemyAndAimsBeyondIt() {
        SupportField f = field();
        add(f, 0, 0, ME);
        int foe = add(f, 6, 0, FOE);
        add(f, -1, 0, FRIEND);   // our side is behind the healer
        SupportMath.Aim aim = new SupportMath.Aim();
        assertTrue(SupportMath.harmAim(f, 0, 0, 8, CLOUD, -1, 0, INV, aim));
        assertEquals(foe, aim.index);
        assertTrue(aim.x > 6.5, "the cloud goes a block beyond the enemy, away from our side: " + aim.x);
        assertTrue(SupportMath.harmSafeAt(f, aim.x, aim.z, CLOUD, INV));
    }

    @Test
    void harmingCountsAFriendWalkingIntoTheCloud() {
        SupportField f = field();
        add(f, 0, 0, ME);
        add(f, 7, 0, FOE);
        // a friend 6 blocks off the cloud now, walking at it: 0.25 blocks a tick puts it 1 block from the aim in a second
        f.add(null, 7, 6, 0, -0.25, 20f, 20f, 1f, FRIEND);
        SupportMath.Aim aim = new SupportMath.Aim();
        assertFalse(SupportMath.harmAim(f, 0, 0, 8, CLOUD, 0, 0, INV, aim), "the predicted spot is inside the safety ring");
    }

    @Test
    void harmingIgnoresUndeadEnemiesAndUndeadFriends() {
        SupportField f = field();
        add(f, 0, 0, ME);
        add(f, 5, 0, FOE | F_UNDEAD);   // harming heals it
        SupportMath.Aim aim = new SupportMath.Aim();
        assertFalse(SupportMath.harmAim(f, 0, 0, 8, CLOUD, 0, 0, INV, aim), "no living enemy, no throw");
        SupportField g = field();
        add(g, 0, 0, ME);
        add(g, 6, 0, FOE);
        add(g, 7, 0, FRIEND | F_UNDEAD);   // harming heals an undead friend, so it does not block the throw
        assertTrue(SupportMath.harmAim(g, 0, 0, 8, CLOUD, 0, 0, INV, aim));
    }

    @Test
    void harmingPrefersTheBiggerCluster() {
        SupportField f = field();
        add(f, 0, 0, ME);
        add(f, 0, 7, FOE);
        int a = add(f, 7, 0, FOE);
        add(f, 7.5, 1, FOE);
        add(f, 7.5, -1, FOE);
        SupportMath.Aim aim = new SupportMath.Aim();
        assertTrue(SupportMath.harmAim(f, 0, 0, 9, CLOUD, 0, 0, INV, aim));
        assertTrue(aim.x > 6, "the cloud goes on the group of three: " + aim.x + "," + aim.z);
        assertTrue(Math.abs(aim.z) < 1.5);
        assertTrue(aim.score >= 3, "three enemies under it");
        assertNotEquals(-1, a);
    }

    @Test
    void healSkipsAFriendNextToAnUndeadFriend() {
        SupportField f = field();
        add(f, 0, 0, ME);
        int nearUndead = addHurt(f, 5, 0, 4f, FRIEND);   // the most hurt
        add(f, 6, 0, FRIEND | F_UNDEAD);                // healing splash would hurt it
        int alone = addHurt(f, -5, 0, 10f, FRIEND);
        int t = SupportMath.healTarget(f, 0, 0, 8, SPLASH, 3f, INV);
        assertEquals(alone, t, "the splash must not land next to the undead friend");
        assertNotEquals(nearUndead, t);
    }

    @Test
    void healTakesTheMostHurtAndTheHealerOnlyWhenLow() {
        SupportField f = field();
        int me = f.add(null, 0, 0, 0, 0, 12f, 20f, 1f, ME);   // 60%: not low enough
        int a = addHurt(f, 3, 0, 15f, FRIEND);
        int b = addHurt(f, -3, 0, 8f, FRIEND);
        assertEquals(b, SupportMath.healTarget(f, 0, 0, 8, SPLASH, 3f, INV));
        f.health[me] = 6f;   // 30%: 14 missing x 1.5 beats b's 12
        assertEquals(me, SupportMath.healTarget(f, 0, 0, 8, SPLASH, 3f, INV));
        assertNotEquals(-1, a);
    }

    @Test
    void healIgnoresScratchesAndFriendsOutOfReach() {
        SupportField f = field();
        add(f, 0, 0, ME);
        addHurt(f, 2, 0, 19f, FRIEND);   // 1 HP missing
        addHurt(f, 20, 0, 2f, FRIEND);   // far away
        assertEquals(-1, SupportMath.healTarget(f, 0, 0, 12, SPLASH, 3f, INV));
    }

    @Test
    void healHalvesATargetWithAnEnemyNextToIt() {
        SupportField f = field();
        add(f, 0, 0, ME);
        int brawling = addHurt(f, 5, 0, 8f, FRIEND);
        add(f, 6, 0, FOE);
        int safe = addHurt(f, -5, 0, 10f, FRIEND);
        assertEquals(safe, SupportMath.healTarget(f, 0, 0, 8, SPLASH, 3f, INV), "12 missing at half weight loses to 10 missing");
        assertNotEquals(-1, brawling);
    }

    @Test
    void areaHealNeedsTwoHurtFriendsAndNoOutnumberingEnemies() {
        SupportField f = field();
        add(f, 0, 0, ME);
        addHurt(f, 5, 0, 10f, FRIEND);
        SupportMath.Aim aim = new SupportMath.Aim();
        assertFalse(SupportMath.areaHealAim(f, 0, 0, 8, CLOUD, 3f, Undead.IMMUNE, aim), "one hurt friend is the single heal's job");
        addHurt(f, 6, 1, 10f, FRIEND);
        assertTrue(SupportMath.areaHealAim(f, 0, 0, 8, CLOUD, 3f, Undead.IMMUNE, aim));
        assertTrue(aim.x > 4.5 && aim.x < 6.5, "on their centre: " + aim.x);
        add(f, 6, -1, FOE);
        add(f, 5.5, 1.5, FOE);
        add(f, 6.5, 0.5, FOE);
        assertFalse(SupportMath.areaHealAim(f, 0, 0, 8, CLOUD, 3f, Undead.IMMUNE, aim), "three enemies under the cloud outnumber the two friends");
    }

    @Test
    void areaHealLandsWhereAWalkingGroupWillBe() {
        SupportField f = field();
        add(f, 0, 0, ME);
        // two hurt friends walking east at 0.1 blocks a tick: in a second they are 2 blocks on
        f.add(null, 3, 0, 0.1, 0, 10f, 20f, 1f, FRIEND);
        f.add(null, 3, 1, 0.1, 0, 10f, 20f, 1f, FRIEND);
        SupportMath.Aim aim = new SupportMath.Aim();
        assertTrue(SupportMath.areaHealAim(f, 0, 0, 8, CLOUD, 3f, Undead.IMMUNE, aim));
        assertEquals(5.0, aim.x, 1e-9, "the cloud leads the group by its second of walking");
    }

    @Test
    void undeadRulesDecideWhoAHealOrHarmTouches() {
        // a plain heal (NORMAL) mends an undead friend and does not care who stands next to it
        SupportField f = field();
        add(f, 0, 0, ME);
        int undead = addHurt(f, 5, 0, 4f, FRIEND | F_UNDEAD);
        assertEquals(undead, SupportMath.healTarget(f, 0, 0, 8, SPLASH, 3f, Undead.NORMAL));
        assertEquals(-1, SupportMath.healTarget(f, 0, 0, 8, SPLASH, 3f, INV), "a potion heal never goes to the undead");
        assertEquals(-1, SupportMath.healTarget(f, 0, 0, 8, SPLASH, 3f, Undead.IMMUNE), "nor a heal they are immune to");
        // a plain harm (NORMAL) is blocked by an undead friend in reach of it; INVERTED and IMMUNE ones are not
        SupportField g = field();
        add(g, 0, 0, ME);
        add(g, 6, 0, FOE);
        add(g, 7, 0, FRIEND | F_UNDEAD);
        SupportMath.Aim aim = new SupportMath.Aim();
        assertFalse(SupportMath.harmAim(g, 0, 0, 8, CLOUD, 0, 0, Undead.NORMAL, aim));
        assertTrue(SupportMath.harmAim(g, 0, 0, 8, CLOUD, 0, 0, Undead.IMMUNE, aim));
    }

    @Test
    void theCastsRadiusSetsTheSafetyRing() {
        SupportField f = field();
        add(f, 0, 0, ME);
        add(f, 9, 0, FOE);
        add(f, 9, 5, FRIEND);   // 5 off the enemy: clear of a 3 radius + 1.5, not of a 4.5 radius + 1.5
        SupportMath.Aim aim = new SupportMath.Aim();
        assertTrue(SupportMath.harmAim(f, 0, 0, 10, 3.0, 0, 0, INV, aim));
        assertFalse(SupportMath.harmAim(f, 0, 0, 10, 4.5, 0, 0, INV, aim), "a bigger area keeps a bigger ring clear");
    }

    @Test
    void cleanseTakesTheAfflictedFriendLowestOnHealth() {
        SupportField f = field();
        add(f, 0, 0, ME);
        addHurt(f, 3, 0, 18f, FRIEND | F_BURNING | F_AFFLICTED);
        int poisoned = addHurt(f, -3, 0, 6f, FRIEND | F_AFFLICTED);
        add(f, 2, 2, FOE | F_BURNING | F_AFFLICTED);   // never an enemy
        assertEquals(poisoned, SupportMath.cleanseTarget(f, 0, 0, 8, F_AFFLICTED));
        assertNotEquals(poisoned, SupportMath.cleanseTarget(f, 0, 0, 8, F_BURNING), "water only puts out fire");
        assertEquals(-1, SupportMath.cleanseTarget(f, 0, 0, 2, F_AFFLICTED), "out of reach");
    }

    @Test
    void frontIsTheFriendWithTheMostEnemiesNear() {
        SupportField f = field();
        add(f, 0, 0, ME);
        add(f, 2, 0, F_FRIEND | F_SUPPORT);   // another healer never counts as the front
        add(f, 3, 3, FRIEND);
        int busy = add(f, 5, 0, FRIEND);
        add(f, 9, 0, FOE);
        add(f, 9, 1, FOE);
        add(f, 12, 3, FOE);
        assertEquals(busy, SupportMath.front(f, 0, 0, 16));
        SupportField calm = field();
        add(calm, 0, 0, ME);
        add(calm, 3, 0, FRIEND);
        assertEquals(-1, SupportMath.front(calm, 0, 0, 16), "nobody engaged: no front");
    }

    @Test
    void fleeLeadsAwayFromTheThreatAndTowardOurArmy() {
        SupportField f = field();
        add(f, 0, 0, ME);
        add(f, 2, 0, FOE);       // melee at 2 blocks, east
        add(f, -3, 4, FRIEND);   // our army north-west
        double[] dir = new double[2];
        assertTrue(SupportMath.fleeDirection(f, 0, 0, 4, 10, dir));
        assertTrue(dir[0] < -0.5, "west, away from the threat: " + dir[0]);
        assertTrue(dir[1] > 0.1, "bent toward the army: " + dir[1]);
        assertEquals(1.0, Math.hypot(dir[0], dir[1]), 1e-9);
    }

    @Test
    void fleeIgnoresFarMeleeButNotAnArcherAimingAtMe() {
        SupportField f = field();
        add(f, 0, 0, ME);
        add(f, 6, 0, FOE);   // melee, but 6 blocks off
        double[] dir = new double[2];
        assertFalse(SupportMath.fleeDirection(f, 0, 0, 4, 10, dir));
        add(f, 0, 10, F_ENEMY | F_ATTACKER | F_TARGETS_ME);
        assertTrue(SupportMath.fleeDirection(f, 0, 0, 4, 10, dir));
        assertTrue(dir[1] < -0.9, "away from the archer: " + dir[1]);
    }

    @Test
    void behindAndKeepClear() {
        double[] p = new double[2];
        SupportMath.behind(10, 0, 14, 0, 6.5, 0, 0, p);
        assertEquals(3.5, p[0], 1e-9);
        assertEquals(0, p[1], 1e-9);
        SupportField f = field();
        add(f, 4, 0, FOE);
        SupportMath.keepClear(f, 5.5, p);
        assertTrue(Math.hypot(p[0] - 4, p[1]) >= 5.5 - 1e-9, "pushed out of the melee unit's reach: " + p[0]);
    }

    @Test
    void rearRankStandsBehindTheClickAndSpreadsAcross() {
        double[] a = new double[2], b = new double[2];
        // a group at the origin fights toward (20, 0): two healers 6.5 short of the click, 2 apart across the move
        SupportMath.rearRank(0, 0, 20, 0, 6.5, 0, 2, 2.0, a);
        SupportMath.rearRank(0, 0, 20, 0, 6.5, 1, 2, 2.0, b);
        assertEquals(13.5, a[0], 1e-9);
        assertEquals(13.5, b[0], 1e-9);
        assertEquals(2.0, Math.abs(a[1] - b[1]), 1e-9);
        assertTrue(SupportMath.across(0, 0, 20, 0, a[0], a[1]) < SupportMath.across(0, 0, 20, 0, b[0], b[1]));
        // a click 3 blocks off: never behind where the group stands
        SupportMath.rearRank(0, 0, 3, 0, 6.5, 0, 1, 2.0, a);
        assertEquals(0, a[0], 1e-9);
    }

    @Test
    void candidatesAreCappedToTheNearest() {
        SupportField f = field();
        add(f, 0, 0, ME);
        for (int i = 0; i < 30; i++)
            add(f, 1 + i * 0.25, 7, FOE);
        int k = SupportMath.nearest(f, F_ENEMY, 0, 0, 0, 100, 0f);
        assertEquals(SupportMath.MAX_CANDIDATES, k);
        for (int c = 1; c < k; c++)
            assertTrue(f.candD[c - 1] <= f.candD[c], "nearest first");
        assertEquals(1, f.cand[0], "the first enemy added is the nearest");
    }

    @Test
    void lookaheadIsCapped() {
        SupportField f = field();
        int i = f.add(null, 0, 0, 5, 0, 20f, 20f, 1f, FRIEND);   // knocked back at 5 blocks a tick
        assertEquals(SupportField.MAX_LOOKAHEAD, f.px[i], 1e-9);
    }
}
