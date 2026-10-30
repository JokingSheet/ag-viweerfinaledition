package com.auctionsniper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Oeffnet /ah nur wenn kein GUI offen ist, scannt Seite fuer Seite (Klick auf das Papier in der
 * letzten Reihe) und schliesst NUR das erkannte Auktionshaus. Fremde GUIs werden nie geschlossen.
 */
public final class AutoScan {
    private static final int SETTLE_TICKS = 8;       // Wartezeit nach Oeffnen/Klick bis zum Lesen
    private static final int WAIT_CHANGE_TICKS = 30; // so lange auf eine neue Seite warten
    private static final int WAIT_FIRST_TICKS = 40;  // so lange auf Inhalt der ersten Seite warten

    private enum State { COOLDOWN, WAIT_OPEN, SCANNING }

    private State state = State.COOLDOWN;
    private int timer = 60;
    private AbstractContainerScreen<?> ahScreen;

    private int pages;
    private int settle;
    private int totalTicks;
    private boolean clicked;
    private int prevHash;
    private final Set<Integer> seen = new HashSet<>();

    public void reset() {
        state = State.COOLDOWN;
        timer = 60;
        ahScreen = null;
    }

    public boolean isScanning() {
        return state == State.SCANNING;
    }

    public String status() {
        if (!SniperConfig.get().autoScan) return "Auto-Scan aus";
        return switch (state) {
            case COOLDOWN -> "Nächster Scan in " + Math.max(0, timer / 20) + "s";
            case WAIT_OPEN -> "Öffne /ah ...";
            case SCANNING -> "Scanne Seite " + (pages + 1);
        };
    }

    public void tick(Minecraft mc) {
        SniperConfig cfg = SniperConfig.get();
        if (!cfg.autoScan || mc.player == null || mc.level == null) {
            if (state != State.COOLDOWN) reset();
            return;
        }
        switch (state) {
            case COOLDOWN -> {
                if (timer > 0) timer--;
                if (timer <= 0 && mc.screen == null) {
                    mc.player.connection.sendCommand("ah");
                    state = State.WAIT_OPEN;
                    timer = 0;
                }
            }
            case WAIT_OPEN -> {
                timer++;
                if (mc.screen instanceof AbstractContainerScreen<?> acs && AuctionScanner.isChest(acs)) {
                    if (AuctionScanner.isAuctionHouse(acs)) {
                        begin(acs);
                        return;
                    }
                    // Titel unbekannt: wir haben gerade selbst /ah gesendet und das Fenster enthaelt Preise
                    String title = acs.getTitle().getString().trim();
                    if (!title.isEmpty() && !AuctionScanner.scan(mc, acs).isEmpty()) {
                        cfg.learnedTitles.add(title);
                        cfg.save();
                        mc.player.displayClientMessage(Component.literal(
                                "[Auction Sniper] Auktionshaus-Titel gelernt: \"" + title + "\"")
                                .withStyle(ChatFormatting.YELLOW), false);
                        begin(acs);
                        return;
                    }
                }
                if (timer > 100) finish(cfg); // nichts erkannt -> nichts schliessen
            }
            case SCANNING -> crawl(mc, cfg);
        }
    }

    private void crawl(Minecraft mc, SniperConfig cfg) {
        totalTicks++;
        settle++;

        if (mc.screen != ahScreen) {
            if (mc.screen instanceof AbstractContainerScreen<?> n && AuctionScanner.isAuctionHouse(n)) {
                ahScreen = n; // Server hat das Menue fuer die neue Seite neu geoeffnet
                settle = 0;
            } else {
                finish(cfg); // Spieler hat gewechselt/geschlossen -> nichts schliessen
                return;
            }
        }
        if (totalTicks > Math.max(1, cfg.maxPages) * 80) {
            closeAndFinish(mc, cfg);
            return;
        }
        if (settle < SETTLE_TICKS) return;

        int h = AuctionScanner.pageHash(mc, ahScreen);
        if (clicked) {
            boolean changed = h != 0 && h != prevHash;
            if (!changed) {
                if (settle < WAIT_CHANGE_TICKS) return;
                closeAndFinish(mc, cfg); // keine neue Seite -> letzte Seite erreicht
                return;
            }
            if (seen.contains(h)) { // wieder bei einer bekannten Seite
                closeAndFinish(mc, cfg);
                return;
            }
        } else if (h == 0 && settle < WAIT_FIRST_TICKS) {
            return; // erste Seite noch nicht geladen
        }

        AuctionSniperMod.scanNow(mc, ahScreen);
        seen.add(h);
        prevHash = h;
        pages++;
        clicked = false;

        if (!cfg.scanAllPages || pages >= cfg.maxPages) {
            closeAndFinish(mc, cfg);
            return;
        }
        int slot = AuctionScanner.findNextPageSlot(mc, ahScreen);
        if (slot < 0 || !AuctionScanner.clickSlot(mc, ahScreen, slot)) {
            closeAndFinish(mc, cfg);
            return;
        }
        clicked = true;
        settle = 0;
    }

    private void begin(AbstractContainerScreen<?> acs) {
        ahScreen = acs;
        state = State.SCANNING;
        pages = 0;
        settle = 0;
        totalTicks = 0;
        clicked = false;
        prevHash = 0;
        seen.clear();
        AuctionSniperMod.beginSnapshot();
    }

    private void closeAndFinish(Minecraft mc, SniperConfig cfg) {
        if (ahScreen != null && mc.screen == ahScreen && AuctionScanner.isAuctionHouse(ahScreen)
                && mc.player != null) {
            mc.player.closeContainer();
        }
        finish(cfg);
    }

    private void finish(SniperConfig cfg) {
        ahScreen = null;
        state = State.COOLDOWN;
        timer = Math.max(1, cfg.cooldownSeconds) * 20;
    }
}
