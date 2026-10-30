package com.auctionsniper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Wird als config/auctionsniper.json gespeichert. */
public final class SniperConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SniperConfig instance = new SniperConfig();

    public String itemFilter = "";
    public String maxPriceText = "";
    public boolean alarmEnabled = true;
    /** true = nur Angebote MIT Verzauberungen, false = nur OHNE. */
    public boolean withEnchants = false;
    public boolean autoScan = false;
    /** true: "Price" in der Lore ist der Gesamtpreis des Stacks. false: Preis pro Stueck. */
    public boolean priceIsTotal = true;
    /** Beim Auto-Scan alle Seiten durchblaettern (Papier-Button in der letzten Reihe). */
    public boolean scanAllPages = true;
    public int maxPages = 40;
    /** Slot (ab 0) des Weiter-Buttons; -1 = automatisch das Papier in der letzten Reihe suchen. */
    public int nextPageSlot = -1;
    public int cooldownSeconds = 20;
    /** Teilstrings des Fenstertitels, die das Auktionshaus kennzeichnen (kurze wie "ah" nur als ganzes Wort). */
    public List<String> ahTitleKeywords = new ArrayList<>(List.of("auction", "auktion", "ah"));
    /** Vom Auto-Scan gelernte exakte Titel. */
    public List<String> learnedTitles = new ArrayList<>();
    /** Zusaetzliche Custom-Enchant-Namen des Servers (klein oder gross egal). */
    public List<String> extraEnchantNames = new ArrayList<>();

    private transient boolean dirty;

    public static SniperConfig get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("auctionsniper.json");
    }

    public static void load() {
        Path p = path();
        try {
            if (Files.exists(p)) {
                SniperConfig c = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), SniperConfig.class);
                if (c != null) {
                    if (c.ahTitleKeywords == null) c.ahTitleKeywords = new ArrayList<>();
                    if (c.learnedTitles == null) c.learnedTitles = new ArrayList<>();
                    if (c.extraEnchantNames == null) c.extraEnchantNames = new ArrayList<>();
                    if (c.itemFilter == null) c.itemFilter = "";
                    if (c.maxPriceText == null) c.maxPriceText = "";
                    instance = c;
                }
            } else {
                instance.save();
            }
        } catch (Exception e) {
            System.err.println("[AuctionSniper] Config konnte nicht geladen werden: " + e);
        }
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException e) {
            System.err.println("[AuctionSniper] Config konnte nicht gespeichert werden: " + e);
        }
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    /** Maximaler Stueckpreis, 0 wenn nicht gesetzt/ungueltig. */
    public double maxPrice() {
        double v = PriceParser.parse(maxPriceText);
        return v > 0 ? v : 0;
    }

    /** Erfuellt das Angebot Filter, Preislimit und Enchant-Variante? */
    public boolean matches(Offer o) {
        if (!alarmEnabled) return false;
        double max = maxPrice();
        if (max <= 0 || o.unitPrice > max) return false;
        if (o.hasEnchants != withEnchants) return false;
        return o.matchesFilter(itemFilter.toLowerCase(Locale.ROOT));
    }
}
