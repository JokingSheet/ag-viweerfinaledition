package com.auctionsniper;

import java.util.List;

public final class Offer {
    public final String name;
    public final String itemId;
    public final String category;
    public final String spawnerType;
    public final List<String> enchants;
    public final boolean hasEnchants;
    public final double totalPrice;
    public final double unitPrice;
    public final int count;
    public final int slot;
    public final String groupKey;
    public final String groupLabel;
    public final String signature;
    public boolean alarmMatch;
    private final String searchText;

    public Offer(String name, String itemId, String category, String spawnerType, List<String> enchants,
                 boolean hasEnchants, double totalPrice, double unitPrice, int count, int slot) {
        this.name = name;
        this.itemId = itemId;
        this.category = category;
        this.spawnerType = spawnerType;
        this.enchants = enchants;
        this.hasEnchants = hasEnchants;
        this.totalPrice = totalPrice;
        this.unitPrice = unitPrice;
        this.count = count;
        this.slot = slot;
        String ench = String.join(",", enchants).toLowerCase();
        String base = name.toLowerCase() + "|" + itemId + "|" + ench + "|" + spawnerType;
        this.groupKey = base + "|" + hasEnchants;
        this.signature = base + "|" + (long) totalPrice + "|" + count;
        this.groupLabel = name + (spawnerType.isEmpty() ? "" : " (" + spawnerType + ")") + (hasEnchants ? " [E]" : "");
        this.searchText = (name + " " + itemId.replace('_', ' ') + " " + category + " " + spawnerType).toLowerCase();
    }

    public String displayName() {
        return groupLabel + (count > 1 ? " x" + count : "");
    }

    /** Alle Woerter des Filters muessen vorkommen. Leerer Filter = alles. */
    public boolean matchesFilter(String lowerFilter) {
        String f = lowerFilter.trim();
        if (f.isEmpty()) return true;
        for (String token : f.split("\\s+")) {
            if (!searchText.contains(token)) return false;
        }
        return true;
    }
}
