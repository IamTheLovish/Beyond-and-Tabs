package com.solegendary.reignofnether.hero.wager;

import java.util.Locale;

/**
 * Reads a Wager Book page (design "Reading a page"): the request is the last non-empty line, lower-cased and stripped
 * of punctuation, matched against the keyword table of {@link WagerIntents}. No language model anywhere: the keyword
 * table is the whole understanding, which is also what keeps Lovish fair and cheap.
 * <p>
 * Scoring: a single keyword counts once per word it matches (a keyword of four or more letters also takes longer words
 * that start with it: "rain" reads "raining"); a phrase counts once per word in it, so "run away" outweighs "run". The
 * intent with the most points wins; a tie goes to the smaller ask (the intent's size), then to the table's order.
 */
public final class WagerParser {
    private WagerParser() { }

    /** What a page asks for: the intent, the asked tier (1-4), a quantity hint (0 = none), the line it read. */
    public record Parsed(WagerIntents.Intent intent, int askedTier, int quantity, String line, int matches) { }

    /** The last non-empty line of the page ("" for an empty page): notes above it are the player's own. */
    public static String requestLine(String page) {
        if (page == null)
            return "";
        String[] lines = page.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String l = lines[i].trim();
            if (!l.isEmpty())
                return l;
        }
        return "";
    }

    /** Lower case, punctuation gone, single spaces. */
    public static String normalise(String line) {
        if (line == null)
            return "";
        String s = line.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        return s.trim().replaceAll(" +", " ");
    }

    public static Parsed parse(String page) {
        return parse(page, WagerIntents.get());
    }

    public static Parsed parse(String page, WagerIntents table) {
        String line = normalise(requestLine(page));
        String[] words = line.isEmpty() ? new String[0] : line.split(" ");
        String padded = " " + line + " ";

        WagerIntents.Intent best = table.nonsense();
        int bestScore = 0;
        for (WagerIntents.Intent intent : table.intents) {
            int score = 0;
            for (String k : intent.keywords()) {
                if (k.contains(" ")) {
                    if (padded.contains(" " + k + " "))
                        score += k.split(" ").length;
                    continue;
                }
                for (String w : words)
                    if (w.equals(k) || (k.length() >= 4 && w.startsWith(k)))
                        score++;
            }
            if (score > bestScore || (score == bestScore && score > 0 && intent.size() < best.size())) {
                best = intent;
                bestScore = score;
            }
        }

        int up = 0, down = 0, quantity = 0;
        for (String w : words) {
            if (table.sizeUp.contains(w))
                up++;
            else if (table.sizeDown.contains(w))
                down++;
            else if (w.length() <= 6 && w.chars().allMatch(Character::isDigit))
                quantity = Math.max(quantity, Integer.parseInt(w));
        }
        int tier = table.defaultTier + Math.min(2, up) - Math.min(2, down) + (quantity >= 10 ? 1 : 0);
        return new Parsed(best, Math.max(1, Math.min(4, tier)), quantity, line, bestScore);
    }
}
