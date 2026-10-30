package com.auctionsniper;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AuctionSniperMod implements ClientModInitializer {
    public static final String MOD_ID = "auctionsniper";

    public static final Stats STATS = new Stats();
    public static final AlertManager ALERTS = new AlertManager();
    public static final AutoScan AUTO = new AutoScan();
    /** Bereits gescannte Itemnamen fuer die Autovervollstaendigung. */
    public static final Set<String> KNOWN_NAMES = new LinkedHashSet<>();
    /** Guenstigstes Angebot je Gruppe aus dem letzten Scan. */
    public static List<Offer> cheapest = List.of();
    private static final Map<String, Offer> SNAPSHOT = new LinkedHashMap<>();

    private static KeyMapping openKey;
    private int tickCounter;
    private Screen trackedScreen;
    private int trackTimer;

    @Override
    public void onInitializeClient() {
        SniperConfig.load();
        // Falls diese Zeile nicht kompiliert: KeyMapping.Category.register(<Identifier>) verwenden.
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.auctionsniper.open", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT,
                KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> SniperConfig.get().save());
    }

    public static void clearAll() {
        STATS.clear();
        ALERTS.clear();
        SNAPSHOT.clear();
        cheapest = List.of();
    }

    /** Neuer Scan-Durchlauf: Liste der guenstigsten Angebote beginnt von vorn (Seiten werden danach zusammengefuehrt). */
    public static void beginSnapshot() {
        SNAPSHOT.clear();
        cheapest = List.of();
    }

    private void tick(Minecraft mc) {
        tickCounter++;
        SniperConfig cfg = SniperConfig.get();

        if (mc.player == null || mc.level == null) {
            AUTO.reset();
            return;
        }

        while (openKey.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new SniperScreen());
        }

        AUTO.tick(mc);

        Screen s = mc.screen;
        if (s != trackedScreen) {
            trackedScreen = s;
            trackTimer = 0;
            if (s instanceof AbstractContainerScreen<?> n && AuctionScanner.isAuctionHouse(n) && !AUTO.isScanning()) {
                beginSnapshot(); // manuell geoeffnetes Auktionshaus
            }
        } else {
            trackTimer++;
        }
        if (s instanceof AbstractContainerScreen<?> acs && AuctionScanner.isChest(acs)) {
            if (AuctionScanner.isAuctionHouse(acs)) {
                if (!AUTO.isScanning() && trackTimer % 10 == 5) scanNow(mc, acs);
            } else if (trackTimer == 20 && AuctionScanner.rows(acs) == 6
                    && !AuctionScanner.scan(mc, acs).isEmpty()) {
                mc.player.displayClientMessage(Component.literal(
                        "[Auction Sniper] Fenster \"" + acs.getTitle().getString()
                                + "\" enthält Preise, wird aber nicht als Auktionshaus erkannt. "
                                + "Aktiviere Auto-Scan (lernt den Titel bei /ah) oder trage ein Stichwort in "
                                + "ahTitleKeywords in config/auctionsniper.json ein.")
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }

        if (tickCounter % 20 == 0) STATS.prune(System.currentTimeMillis());
        if (tickCounter % 40 == 0) cfg.saveIfDirty();
    }

    /** Liest die aktuelle Seite, fuehrt sie mit frueheren Seiten zusammen und loest Alarme aus. */
    public static void scanNow(Minecraft mc, AbstractContainerScreen<?> acs) {
        List<Offer> offers = AuctionScanner.scan(mc, acs);
        if (offers.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Offer o : offers) {
            KNOWN_NAMES.add(o.name);
            STATS.record(o, now);
        }
        while (KNOWN_NAMES.size() > 500) {
            Iterator<String> it = KNOWN_NAMES.iterator();
            it.next();
            it.remove();
        }
        ALERTS.process(mc, offers);

        for (Offer o : offers) SNAPSHOT.merge(o.groupKey, o, (a, b) -> b.unitPrice <= a.unitPrice ? b : a);
        List<Offer> list = new ArrayList<>(SNAPSHOT.values());
        list.sort(Comparator.comparing((Offer o) -> !o.alarmMatch).thenComparingDouble(o -> o.unitPrice));
        cheapest = list;
    }
}
