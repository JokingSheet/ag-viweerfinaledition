package com.auctionsniper;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Modernes GUI: Die Vanilla-Widgets (Buttons, Textfelder) uebernehmen Klicks und Eingaben,
 * das Aussehen wird darueber selbst gezeichnet. Kein Blur -> kein "blur once per frame"-Absturz.
 */
public class SniperScreen extends Screen {
    private static final int C_DIM = 0xB0080A10;
    private static final int C_BORDER = 0xFF2B3245;
    private static final int C_BG = 0xFF141821;
    private static final int C_HEADER = 0xFF1B2030;
    private static final int C_CARD = 0xFF1A1F2C;
    private static final int C_ACCENT = 0xFF4C8DFF;
    private static final int C_TEXT = 0xFFE8EAF2;
    private static final int C_MUTED = 0xFF8A90A5;
    private static final int C_GOOD = 0xFF4ADE80;
    private static final int C_BAD = 0xFFFF6B6B;
    private static final int C_WARN = 0xFFFFD866;

    private static final int ROW_SUG = 14;

    private EditBox filterBox;
    private EditBox priceBox;
    private Button alarmBtn, encBtn, autoBtn, pagesBtn, clearBtn, closeBtn;
    private final Button[] sugBtns = new Button[5];
    private final String[] sugText = new String[5];
    private boolean suppressSuggestions;
    private boolean priceValid = true;
    private int x0, y0, w, h, lx, lw, rx, rw;

    public SniperScreen() {
        super(Component.literal("Auction Sniper"));
    }

    @Override
    protected void init() {
        SniperConfig cfg = SniperConfig.get();
        w = Math.min(width - 12, 470);
        h = Math.min(height - 12, 196);
        x0 = (width - w) / 2;
        y0 = (height - h) / 2;
        lx = x0 + 10;
        lw = Math.max(130, Math.min(176, w * 40 / 100));
        rx = lx + lw + 10;
        rw = Math.max(40, x0 + w - 10 - rx);
        int hw = (lw - 4) / 2;

        // Vorschlaege zuerst: bekommen bei Ueberlappung die Klicks vor den Widgets darunter
        for (int i = 0; i < sugBtns.length; i++) {
            final int idx = i;
            sugBtns[i] = Button.builder(Component.empty(), b -> pickSuggestion(idx))
                    .bounds(lx, y0 + 56 + i * ROW_SUG, lw, ROW_SUG).build();
            sugBtns[i].visible = false;
            addRenderableWidget(sugBtns[i]);
        }

        filterBox = new EditBox(font, lx, y0 + 38, lw, 16, Component.literal("Item-Filter"));
        filterBox.setMaxLength(64);
        filterBox.setValue(cfg.itemFilter);
        filterBox.setHint(Component.literal("z.B. gil, mac, dia"));
        filterBox.setResponder(this::onFilterChanged);
        addRenderableWidget(filterBox);

        priceBox = new EditBox(font, lx, y0 + 72, lw, 16, Component.literal("Max. Stückpreis"));
        priceBox.setMaxLength(24);
        priceBox.setValue(cfg.maxPriceText);
        priceBox.setHint(Component.literal("z.B. 700K, 1.5M, 2B"));
        priceBox.setResponder(this::onPriceChanged);
        addRenderableWidget(priceBox);
        priceValid = cfg.maxPriceText.isBlank() || PriceParser.parse(cfg.maxPriceText) >= 0;

        alarmBtn = toggle(cfg, lx, y0 + 96, hw, () -> cfg.alarmEnabled = !cfg.alarmEnabled);
        encBtn = toggle(cfg, lx + hw + 4, y0 + 96, hw, () -> cfg.withEnchants = !cfg.withEnchants);
        autoBtn = toggle(cfg, lx, y0 + 118, hw, () -> cfg.autoScan = !cfg.autoScan);
        pagesBtn = toggle(cfg, lx + hw + 4, y0 + 118, hw, () -> cfg.scanAllPages = !cfg.scanAllPages);
        clearBtn = Button.builder(Component.literal("Angebote leeren"), b -> AuctionSniperMod.clearAll())
                .bounds(lx, y0 + 140, lw, 18).build();
        closeBtn = Button.builder(Component.literal("Schließen"), b -> onClose())
                .bounds(lx, y0 + 162, lw, 18).build();
        addRenderableWidget(clearBtn);
        addRenderableWidget(closeBtn);
        setInitialFocus(filterBox);
    }

    private Button toggle(SniperConfig cfg, int x, int y, int bw, Runnable flip) {
        Button b = Button.builder(Component.empty(), btn -> {
            flip.run();
            cfg.markDirty();
        }).bounds(x, y, bw, 18).build();
        addRenderableWidget(b);
        return b;
    }

    private void onFilterChanged(String text) {
        SniperConfig cfg = SniperConfig.get();
        cfg.itemFilter = text;
        cfg.markDirty();
        List<String> sug = suppressSuggestions ? List.of() : Suggestions.suggest(text);
        for (int i = 0; i < sugBtns.length; i++) {
            if (i < sug.size()) {
                sugText[i] = sug.get(i);
                sugBtns[i].setMessage(Component.literal(sug.get(i)));
                sugBtns[i].visible = true;
            } else {
                sugText[i] = null;
                sugBtns[i].visible = false;
            }
        }
    }

    private void onPriceChanged(String text) {
        SniperConfig cfg = SniperConfig.get();
        cfg.maxPriceText = text;
        cfg.markDirty();
        priceValid = text.isBlank() || PriceParser.parse(text) >= 0;
    }

    private void pickSuggestion(int idx) {
        String chosen = sugText[idx];
        if (chosen == null) return;
        suppressSuggestions = true;
        filterBox.setValue(chosen); // Responder speichert den Filter
        suppressSuggestions = false;
        for (Button b : sugBtns) b.visible = false;
        setFocused(filterBox);
    }

    /** Bewusst leer: kein Blur. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    // ---------- Zeichen-Helfer ----------

    private static void roundRect(GuiGraphics g, int x, int y, int rw_, int rh, int color) {
        g.fill(x + 1, y, x + rw_ - 1, y + rh, color);
        g.fill(x, y + 1, x + rw_, y + rh - 1, color);
    }

    private static void card(GuiGraphics g, int x, int y, int cw, int ch) {
        roundRect(g, x, y, cw, ch, C_BORDER);
        roundRect(g, x + 1, y + 1, cw - 2, ch - 2, C_CARD);
    }

    private static void outline(GuiGraphics g, int x, int y, int ow, int oh, int color) {
        g.fill(x, y, x + ow, y + 1, color);
        g.fill(x, y + oh - 1, x + ow, y + oh, color);
        g.fill(x, y, x + 1, y + oh, color);
        g.fill(x + ow - 1, y, x + ow, y + oh, color);
    }

    private static boolean over(Button b, int mx, int my) {
        return b.visible && mx >= b.getX() && mx < b.getX() + b.getWidth()
                && my >= b.getY() && my < b.getY() + b.getHeight();
    }

    private void styledToggle(GuiGraphics g, Button b, String full, String shortLabel, String state,
                              boolean on, int mx, int my) {
        boolean hov = over(b, mx, my);
        int bg = on ? (hov ? 0xFF28604A : 0xFF1F4D3A) : (hov ? 0xFF343A4E : 0xFF2A2F3F);
        int border = on ? 0xFF2E8B57 : 0xFF3A4156;
        roundRect(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), border);
        roundRect(g, b.getX() + 1, b.getY() + 1, b.getWidth() - 2, b.getHeight() - 2, bg);
        String label = font.width(full) + font.width(state) + 18 <= b.getWidth() ? full : shortLabel;
        int ty = b.getY() + (b.getHeight() - 8) / 2;
        g.fill(b.getX() + 5, ty + 2, b.getX() + 9, ty + 6, on ? C_GOOD : C_MUTED); // Status-Punkt
        g.drawString(font, label, b.getX() + 13, ty, C_TEXT);
        g.drawString(font, state, b.getX() + b.getWidth() - 5 - font.width(state), ty, on ? C_GOOD : C_MUTED);
    }

    private void styledAction(GuiGraphics g, Button b, String label, int bg, int bgHover, int border, int mx, int my) {
        boolean hov = over(b, mx, my);
        roundRect(g, b.getX(), b.getY(), b.getWidth(), b.getHeight(), border);
        roundRect(g, b.getX() + 1, b.getY() + 1, b.getWidth() - 2, b.getHeight() - 2, hov ? bgHover : bg);
        String l = font.plainSubstrByWidth(label, b.getWidth() - 8);
        g.drawString(font, l, b.getX() + (b.getWidth() - font.width(l)) / 2,
                b.getY() + (b.getHeight() - 8) / 2, C_TEXT);
    }

    // ---------- Rendern ----------

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        SniperConfig cfg = SniperConfig.get();

        g.fill(0, 0, width, height, C_DIM);
        roundRect(g, x0, y0, w, h, C_BORDER);
        roundRect(g, x0 + 1, y0 + 1, w - 2, h - 2, C_BG);

        // Kopfleiste
        g.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + 22, C_HEADER);
        g.fill(x0 + 1, y0 + 22, x0 + w - 1, y0 + 23, C_ACCENT);
        g.fill(x0 + 10, y0 + 7, x0 + 13, y0 + 16, C_ACCENT);
        g.drawString(font, "AUCTION SNIPER", x0 + 18, y0 + 8, C_TEXT);
        String status = AuctionSniperMod.AUTO.status();
        int sw = font.width(status);
        int chipX = x0 + w - 10 - sw - 10;
        roundRect(g, chipX, y0 + 5, sw + 10, 13, cfg.autoScan ? 0xFF1F4D3A : 0xFF2A2F3F);
        g.drawString(font, status, chipX + 5, y0 + 8, cfg.autoScan ? C_GOOD : C_MUTED);

        // Beschriftungen links
        g.drawString(font, "ITEM-FILTER", lx, y0 + 28, C_MUTED);
        g.drawString(font, "MAX. STÜCKPREIS", lx, y0 + 62, priceValid ? C_MUTED : C_BAD);
        if (!priceValid) {
            g.drawString(font, "ungültig", lx + lw - font.width("ungültig"), y0 + 62, C_BAD);
        }

        super.render(g, mx, my, partialTick);

        // Eingabefelder-Rahmen
        outline(g, filterBox.getX(), filterBox.getY(), filterBox.getWidth(), filterBox.getHeight(),
                filterBox.isFocused() ? C_ACCENT : C_BORDER);
        outline(g, priceBox.getX(), priceBox.getY(), priceBox.getWidth(), priceBox.getHeight(),
                !priceValid ? C_BAD : priceBox.isFocused() ? C_ACCENT : C_BORDER);

        // Buttons im modernen Stil (Vanilla-Button liegt darunter und nimmt die Klicks an)
        styledToggle(g, alarmBtn, "Alarm", "Alarm", cfg.alarmEnabled ? "AN" : "AUS", cfg.alarmEnabled, mx, my);
        styledToggle(g, encBtn, "Enchants", "Ench.", cfg.withEnchants ? "MIT" : "OHNE", cfg.withEnchants, mx, my);
        styledToggle(g, autoBtn, "Auto-Scan", "Auto", cfg.autoScan ? "AN" : "AUS", cfg.autoScan, mx, my);
        styledToggle(g, pagesBtn, "Alle Seiten", "Seiten", cfg.scanAllPages ? "AN" : "AUS", cfg.scanAllPages, mx, my);
        styledAction(g, clearBtn, "Angebote leeren", 0xFF4A2530, 0xFF633040, 0xFF7A3A4A, mx, my);
        styledAction(g, closeBtn, "Schließen", 0xFF2A2F3F, 0xFF343A4E, 0xFF3A4156, mx, my);

        drawRight(g);

        // Vorschlags-Dropdown ganz zuletzt, damit es nie verdeckt wird
        drawSuggestions(g, mx, my);
    }

    private void drawSuggestions(GuiGraphics g, int mx, int my) {
        int count = 0;
        for (Button b : sugBtns) if (b.visible) count++;
        if (count == 0) return;
        int top = y0 + 56;
        roundRect(g, lx - 1, top - 1, lw + 2, count * ROW_SUG + 2, C_ACCENT);
        roundRect(g, lx, top, lw, count * ROW_SUG, 0xFF222838);
        for (int i = 0; i < sugBtns.length; i++) {
            Button b = sugBtns[i];
            if (!b.visible) continue;
            int ry = b.getY();
            if (over(b, mx, my)) {
                g.fill(lx + 1, ry, lx + lw - 1, ry + ROW_SUG, 0xFF31405E);
                g.fill(lx + 1, ry, lx + 3, ry + ROW_SUG, C_ACCENT);
            }
            String t = font.plainSubstrByWidth(sugText[i], lw - 14);
            g.drawString(font, t, lx + 7, ry + 3, C_TEXT);
        }
    }

    /** Rechte Seite: Statistik-Karte und Angebots-Karte. */
    private void drawRight(GuiGraphics g) {
        AuctionSniperMod.STATS.prune(System.currentTimeMillis());
        List<Stats.GroupMin> mins = AuctionSniperMod.STATS.groupMins();
        final int lh = 11;
        int top = y0 + 30;
        int bottom = y0 + h - 10;
        int total = bottom - top;
        int statH = Math.max(46, total * 40 / 100);
        int offH = total - statH - 6;

        // --- Statistik ---
        card(g, rx, top, rw, statH);
        g.drawString(font, "STATISTIK · LETZTE 10 MIN", rx + 6, top + 5, C_MUTED);
        int y = top + 17;
        String a = "Angebote ";
        String an = String.valueOf(AuctionSniperMod.STATS.listingCount());
        g.drawString(font, a, rx + 6, y, C_MUTED);
        g.drawString(font, an, rx + 6 + font.width(a), y, C_ACCENT);
        String b = "Gruppen ";
        int bx = rx + 6 + font.width(a) + font.width(an) + 14;
        g.drawString(font, b, bx, y, C_MUTED);
        g.drawString(font, String.valueOf(mins.size()), bx + font.width(b), y, C_ACCENT);
        y += lh + 2;
        int lines = Math.max(0, (top + statH - 4 - y) / lh);
        int shown = Math.min(mins.size(), mins.size() > lines ? Math.max(0, lines - 1) : lines);
        for (int i = 0; i < shown; i++) {
            Stats.GroupMin m = mins.get(i);
            line(g, rx + 6, rw - 12, y, m.label(), PriceParser.format(m.minUnitPrice()), C_TEXT, C_TEXT);
            y += lh;
        }
        if (mins.size() > shown && lines > 0) {
            g.drawString(font, "+ " + (mins.size() - shown) + " weitere Gruppen", rx + 6, y, C_MUTED);
        }
        if (mins.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth("Noch keine Daten", rw - 12), rx + 6, y, C_MUTED);
        }

        // --- Angebote ---
        int oy = top + statH + 6;
        card(g, rx, oy, rw, offH);
        g.drawString(font, "GÜNSTIGSTE ANGEBOTE", rx + 6, oy + 5, C_MUTED);
        int cnt = AuctionSniperMod.cheapest.size();
        String cs = cnt + " Gruppen";
        g.drawString(font, cs, rx + rw - 6 - font.width(cs), oy + 5, C_MUTED);
        y = oy + 17;
        int footer = AuctionSniperMod.ALERTS.lastAlert != null ? lh + 2 : 0;
        int limit = oy + offH - 4 - footer;
        List<Offer> offers = AuctionSniperMod.cheapest;
        if (offers.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth("Auktionshaus öffnen oder Auto-Scan starten", rw - 12),
                    rx + 6, y, C_MUTED);
        }
        for (Offer o : offers) {
            if (y + lh > limit) break;
            if (o.alarmMatch) {
                g.fill(rx + 2, y - 1, rx + rw - 2, y + lh - 1, 0x4022AA55);
                g.fill(rx + 2, y - 1, rx + 4, y + lh - 1, C_GOOD);
            }
            int color = o.alarmMatch ? C_GOOD : C_TEXT;
            line(g, rx + 8, rw - 14, y, o.displayName(), PriceParser.format(o.unitPrice) + "/Stk", color, color);
            y += lh;
        }
        Offer last = AuctionSniperMod.ALERTS.lastAlert;
        if (last != null) {
            int fy = oy + offH - 4 - lh;
            g.fill(rx + 4, fy - 3, rx + rw - 4, fy - 2, C_BORDER);
            line(g, rx + 6, rw - 12, fy, "Letzter Alarm: " + last.displayName(),
                    PriceParser.format(last.unitPrice) + "/Stk", C_WARN, C_WARN);
        }
    }

    /** Text links, Preis rechtsbuendig; der Text wird gekuerzt, damit nichts ueberlappt. */
    private void line(GuiGraphics g, int x, int width_, int y, String label, String price,
                      int labelColor, int priceColor) {
        int pw = font.width(price);
        g.drawString(font, price, x + width_ - pw, y, priceColor);
        g.drawString(font, font.plainSubstrByWidth(label, Math.max(10, width_ - pw - 8)), x, y, labelColor);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        SniperConfig.get().save();
        super.onClose();
    }
}
