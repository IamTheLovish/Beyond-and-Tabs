package com.solegendary.reignofnether.building.production;

/**
 * Skeleton for the red run of PopulationRulesTest (tests first): every rule throws until it is implemented.
 */
public final class PopulationRules {
    private PopulationRules() { }

    public enum Verdict {
        WALKS_OUT, BANKS_AS_SKULL, ARMY_FULL, BANK_FULL;

        public boolean canQueue() {
            throw new UnsupportedOperationException("not implemented");
        }
    }

    public static Verdict verdict(int current, int supply, int cost, int skullsCommitted, int maxSkulls) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static boolean hasRoom(int current, int supply, int cost) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static boolean banksOnCompletion(int current, int supply, int cost, int maxSkulls) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static boolean keepsProducing(int current, int supply, int cost, int skullsCommitted, int maxSkulls) {
        throw new UnsupportedOperationException("not implemented");
    }

    public static boolean withinMax(int current, int cost, int maxPopulation) {
        throw new UnsupportedOperationException("not implemented");
    }
}
