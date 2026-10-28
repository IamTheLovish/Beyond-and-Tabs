package com.solegendary.reignofnether.hero.wager;

/**
 * Reads a Wager Book page (design "Reading a page"): the request is the last non-empty line, lower-cased and stripped
 * of punctuation, matched against the keyword table of {@link WagerIntents}. No language model anywhere: the keyword
 * table is the whole understanding, which is also what keeps Lovish fair and cheap.
 */
public final class WagerParser {
    private WagerParser() { }

    /** What a page asks for: the intent, the asked tier (1-4), a quantity hint (0 = none), the line it read. */
    public record Parsed(WagerIntents.Intent intent, int askedTier, int quantity, String line, int matches) { }

    /** The last non-empty line of the page ("" for an empty page): notes above it are the player's own. */
    public static String requestLine(String page) {
        return "";
    }

    /** Lower case, punctuation gone, single spaces. */
    public static String normalise(String line) {
        return "";
    }

    public static Parsed parse(String page) {
        return parse(page, WagerIntents.get());
    }

    public static Parsed parse(String page, WagerIntents table) {
        return new Parsed(table.nonsense(), 1, 0, "", 0);
    }
}
