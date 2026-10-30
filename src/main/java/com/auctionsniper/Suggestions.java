package com.auctionsniper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Autovervollstaendigung: gescannte Namen, generische Begriffe und Vanilla-Items. */
public final class Suggestions {
    private static final String[] GENERIC = {
            "Mace", "Armor", "Armour", "Diamond", "Netherite", "Iron", "Gold", "Spawner",
            "Elytra", "Totem of Undying", "Shulker Box", "Gilded Blackstone"
    };
    private static List<String> vanilla;

    private record Candidate(String text, int score, boolean scanned) {}

    private Suggestions() {}

    public static List<String> suggest(String input) {
        String q = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) return List.of();

        Map<String, Candidate> best = new LinkedHashMap<>();
        for (String n : AuctionSniperMod.KNOWN_NAMES) consider(best, n, true, q);
        for (String n : GENERIC) consider(best, n, false, q);
        for (String n : vanilla()) consider(best, n, false, q);

        List<Candidate> list = new ArrayList<>(best.values());
        list.sort(Comparator.comparingInt(Candidate::score)
                .thenComparing(c -> !c.scanned())
                .thenComparingInt(c -> c.text().length())
                .thenComparing(c -> c.text().toLowerCase(Locale.ROOT)));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(5, list.size()); i++) out.add(list.get(i).text());
        return out;
    }

    private static void consider(Map<String, Candidate> best, String text, boolean scanned, String q) {
        String lower = text.toLowerCase(Locale.ROOT);
        int score;
        if (lower.startsWith(q)) score = 0;
        else if (wordStarts(lower, q)) score = 1;
        else if (lower.contains(q)) score = 2;
        else return;
        Candidate old = best.get(lower);
        Candidate c = new Candidate(text, score, scanned);
        if (old == null || c.score() < old.score() || (c.score() == old.score() && scanned && !old.scanned())) {
            best.put(lower, c);
        }
    }

    private static boolean wordStarts(String lower, String q) {
        for (String w : lower.split("[ _\\-]")) if (w.startsWith(q)) return true;
        return false;
    }

    private static List<String> vanilla() {
        if (vanilla == null) {
            List<String> names = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                var key = BuiltInRegistries.ITEM.getKey(item);
                names.add(titleCase(key.getPath()));
            }
            vanilla = names;
        }
        return vanilla;
    }

    private static String titleCase(String path) {
        StringBuilder sb = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
