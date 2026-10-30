package com.auctionsniper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rollierende Statistik ueber die letzten 10 Minuten. */
public final class Stats {
    public static final long WINDOW_MS = 10 * 60 * 1000L;

    public record GroupMin(String label, double minUnitPrice, int listings) {}

    private record Entry(String groupKey, String groupLabel, double unitPrice, long lastSeen) {}

    private final Map<String, Entry> entries = new HashMap<>();

    public void record(Offer o, long now) {
        entries.put(o.signature, new Entry(o.groupKey, o.groupLabel, o.unitPrice, now));
    }

    public void prune(long now) {
        entries.values().removeIf(e -> now - e.lastSeen() > WINDOW_MS);
    }

    public void clear() {
        entries.clear();
    }

    public int listingCount() {
        return entries.size();
    }

    public List<GroupMin> groupMins() {
        Map<String, double[]> min = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        for (Entry e : entries.values()) {
            double[] v = min.computeIfAbsent(e.groupKey(), k -> new double[]{Double.MAX_VALUE, 0});
            v[0] = Math.min(v[0], e.unitPrice());
            v[1]++;
            labels.put(e.groupKey(), e.groupLabel());
        }
        List<GroupMin> out = new ArrayList<>();
        for (Map.Entry<String, double[]> me : min.entrySet()) {
            out.add(new GroupMin(labels.get(me.getKey()), me.getValue()[0], (int) me.getValue()[1]));
        }
        out.sort(Comparator.comparing(g -> g.label().toLowerCase()));
        return out;
    }

    public int groupCount() {
        return groupMins().size();
    }
}
