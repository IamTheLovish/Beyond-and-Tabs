package com.solegendary.reignofnether.building.production;

/**
 * Who may queue a unit and what becomes of it at the army cap - ONE answer for the server's queue, the client's card
 * and every graveyard (before this the same arithmetic lived in ProductionItem, again in GraveyardUnitProductionItem,
 * once per side). Plain ints, no Minecraft: PopulationRulesTest pins every row.
 * <ul>
 *   <li>A lab queues a unit while current + cost fits the supply (BAR: the supply IS the cap, ResourceCosts); a free
 *       unit (cost 0) is never blocked.</li>
 *   <li>An overflowing graveyard (skull slots, the Overflowing Graveyard research or Level 2 / 3) works like any lab
 *       while the army has room: the finished unit walks out. Only AT the cap does it bank the unit as a skull, while
 *       its slots can take what is queued (playtest Oct 22 19:20, PR #167: "the death buildings werent making any
 *       units and said graveyard is full" - a bank that took every unit filled with skulls that never walked out).</li>
 * </ul>
 */
public final class PopulationRules {
    private PopulationRules() { }

    /** What a queue request for one unit comes to. */
    public enum Verdict {
        /** The army has room (or the unit is free): queued, and it walks out of the building when done. */
        WALKS_OUT,
        /** The army is at the cap but the graveyard has slots: queued, and the finished unit becomes a skull. */
        BANKS_AS_SKULL,
        /** At the cap and the building has no skull slots: refused (the card says "not enough population"). */
        ARMY_FULL,
        /** At the cap and every skull slot is taken or promised to a queued unit: refused ("graveyard is full"). */
        BANK_FULL;

        /** Whether the unit may enter the queue. */
        public boolean canQueue() {
            return this == WALKS_OUT || this == BANKS_AS_SKULL;
        }
    }

    /**
     * The queue decision for one unit.
     *
     * @param current          the owner's population: units alive plus units queued in every building
     * @param supply           the owner's population cap
     * @param cost             this unit's population (0 = free)
     * @param skullsCommitted  skulls on the building's slots plus graveyard units queued (each would become one)
     * @param maxSkulls        the building's skull slots; 0 or less for a building that cannot bank
     */
    public static Verdict verdict(int current, int supply, int cost, int skullsCommitted, int maxSkulls) {
        if (hasRoom(current, supply, cost))
            return Verdict.WALKS_OUT;
        if (maxSkulls <= 0)
            return Verdict.ARMY_FULL;
        return skullsCommitted + cost <= maxSkulls ? Verdict.BANKS_AS_SKULL : Verdict.BANK_FULL;
    }

    /** Whether the army has room for one more of this unit (a free unit always does). */
    public static boolean hasRoom(int current, int supply, int cost) {
        return cost == 0 || current + cost <= supply;
    }

    /**
     * Called when a unit finishes: true when it goes into the building as a skull instead of walking out - only a
     * building with skull slots, and only while the army has no room.
     */
    public static boolean banksOnCompletion(int current, int supply, int cost, int maxSkulls) {
        return maxSkulls > 0 && !hasRoom(current, supply, cost);
    }

    /**
     * Whether production already under way may keep ticking (checked every step, so a supply that drops under a
     * running queue pauses it). An ordinary lab runs while the army is not over the supply; an overflowing graveyard
     * also runs over it, while its slots can take everything queued.
     */
    public static boolean keepsProducing(int current, int supply, int cost, int skullsCommitted, int maxSkulls) {
        if (cost == 0 || current <= supply)
            return true;
        return maxSkulls > 0 && skullsCommitted <= maxSkulls;
    }

    /** The host's hard cap (gamerule maxPopulation) - asked apart from the supply; a free unit is never over it. */
    public static boolean withinMax(int current, int cost, int maxPopulation) {
        return cost == 0 || current + cost <= maxPopulation;
    }
}
