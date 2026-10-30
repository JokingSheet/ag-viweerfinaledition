package com.auctionsniper;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Liest Preise wie 700K, 1.5M, 2B, $500K oder 1,500,000. Gibt -1 zurueck wenn ungueltig. */
public final class PriceParser {
    private static final Pattern LINE = Pattern.compile(
            "(?i)(?:price|preis|cost|kosten|buy\\s*now)\\s*[:=]?\\s*\\$?\\s*([0-9][0-9.,]*)\\s*([kmbt])?(?![a-z])");
    private static final Pattern PLAIN = Pattern.compile(
            "^\\s*\\$?\\s*([0-9][0-9.,]*)\\s*([kmbt])?\\s*$", Pattern.CASE_INSENSITIVE);

    private PriceParser() {}

    /** Fuer eigene Eingaben (Preisfeld). */
    public static double parse(String text) {
        if (text == null) return -1;
        Matcher m = PLAIN.matcher(text);
        if (!m.matches()) return -1;
        return number(m.group(1), m.group(2));
    }

    /** Sucht in einer Lore-Zeile nach "Price: $500K" o.ae. */
    public static double fromLine(String line) {
        if (line == null) return -1;
        Matcher m = LINE.matcher(line);
        if (!m.find()) return -1;
        return number(m.group(1), m.group(2));
    }

    private static double number(String raw, String suffix) {
        String d = raw.replaceAll("[.,]+$", "");
        if (d.isEmpty()) return -1;
        int seps = 0;
        for (char c : d.toCharArray()) if (c == '.' || c == ',') seps++;
        try {
            if (suffix != null && !suffix.isEmpty()) {
                if (seps > 1) {
                    int last = Math.max(d.lastIndexOf('.'), d.lastIndexOf(','));
                    d = d.substring(0, last).replaceAll("[.,]", "") + "." + d.substring(last + 1);
                } else {
                    d = d.replace(',', '.');
                }
                return Double.parseDouble(d) * multiplier(suffix.charAt(0));
            }
            if (seps > 0) {
                int last = Math.max(d.lastIndexOf('.'), d.lastIndexOf(','));
                int after = d.length() - last - 1;
                if (seps > 1 || after == 3) d = d.replaceAll("[.,]", "");
                else d = d.replace(',', '.');
            }
            return Double.parseDouble(d);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double multiplier(char c) {
        switch (Character.toLowerCase(c)) {
            case 'k': return 1_000d;
            case 'm': return 1_000_000d;
            case 'b': return 1_000_000_000d;
            case 't': return 1_000_000_000_000d;
            default: return 1d;
        }
    }

    public static String format(double v) {
        String[] suffix = {"", "K", "M", "B", "T"};
        double a = v;
        int i = 0;
        while (Math.abs(a) >= 1000 && i < suffix.length - 1) {
            a /= 1000;
            i++;
        }
        String n;
        if (a == Math.rint(a)) n = String.valueOf((long) a);
        else n = String.format(Locale.ROOT, "%.2f", a).replaceAll("0+$", "").replaceAll("\\.$", "");
        return "$" + n + suffix[i];
    }
}
