package com.auctionsniper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Erkennt das Auktionshaus und liest Angebote ueber die Tooltip-Zeilen aus. */
public final class AuctionScanner {
    private static final Pattern ENCH = Pattern.compile("^([A-Za-z][A-Za-z' \\-]*?)(?:\\s+([IVXLC]+|\\d+))?$");
    private static final Pattern SPAWN = Pattern.compile(
            "(?i)^(?:spawner(?:\\s*type)?|mob|entity|spawns?|creature)\\s*[:=]\\s*(.+)$");

    private static final Set<String> KNOWN_ENCHANTS = Set.of(
            "protection", "fire protection", "feather falling", "blast protection", "projectile protection",
            "respiration", "aqua affinity", "thorns", "depth strider", "frost walker", "soul speed",
            "swift sneak", "sharpness", "smite", "bane of arthropods", "knockback", "fire aspect", "looting",
            "sweeping edge", "efficiency", "silk touch", "unbreaking", "fortune", "power", "punch", "flame",
            "infinity", "luck of the sea", "lure", "loyalty", "impaling", "riptide", "channeling", "multishot",
            "quick charge", "piercing", "density", "breach", "wind burst", "mending", "lunge",
            "curse of binding", "curse of vanishing", "binding curse", "vanishing curse");

    private AuctionScanner() {}

    private static AbstractContainerMenu menuOf(AbstractContainerScreen<?> s) {
        return s.getMenu();
    }

    public static boolean isChest(AbstractContainerScreen<?> s) {
        return menuOf(s) instanceof ChestMenu;
    }

    public static int rows(AbstractContainerScreen<?> s) {
        return menuOf(s) instanceof ChestMenu c ? c.getRowCount() : 0;
    }

    /** Eindeutig erkannt = Kisten-Menue UND Titel passt zu Stichwort oder gelerntem Titel. */
    public static boolean isAuctionHouse(AbstractContainerScreen<?> s) {
        if (!isChest(s)) return false;
        String t = s.getTitle().getString().toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) return false;
        SniperConfig cfg = SniperConfig.get();
        for (String learned : cfg.learnedTitles) {
            if (t.equals(learned.toLowerCase(Locale.ROOT).trim())) return true;
        }
        for (String kw : cfg.ahTitleKeywords) {
            String k = kw.toLowerCase(Locale.ROOT).trim();
            if (k.isEmpty()) continue;
            if (k.length() <= 2) {
                if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(k) + "(?![a-z0-9])").matcher(t).find()) return true;
            } else if (t.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public static List<Offer> scan(Minecraft mc, AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = menuOf(screen);
        if (!(menu instanceof ChestMenu chest) || mc.player == null) return Collections.emptyList();
        SniperConfig cfg = SniperConfig.get();
        int limit = Math.min(chest.getRowCount() * 9, menu.slots.size());
        List<Offer> result = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            Offer o = parse(mc, stack, i, cfg);
            if (o != null) result.add(o);
        }
        return result;
    }


    /** Hash der sichtbaren Angebote, um Seitenwechsel zu erkennen. 0 = Seite leer/noch nicht geladen. */
    public static int pageHash(Minecraft mc, AbstractContainerScreen<?> s) {
        List<String> sigs = new ArrayList<>();
        for (Offer o : scan(mc, s)) sigs.add(o.signature);
        return sigs.isEmpty() ? 0 : sigs.hashCode();
    }

    /** Sucht den Weiter-Button: Papier in der letzten Reihe (ausser "zurueck"-Buttons). -1 wenn keiner. */
    public static int findNextPageSlot(Minecraft mc, AbstractContainerScreen<?> s) {
        AbstractContainerMenu menu = menuOf(s);
        if (!(menu instanceof ChestMenu chest)) return -1;
        int rows = chest.getRowCount();
        int override = SniperConfig.get().nextPageSlot;
        if (override >= 0 && override < rows * 9 && override < menu.slots.size()
                && !menu.slots.get(override).getItem().isEmpty()) {
            return override;
        }
        int start = (rows - 1) * 9;
        int fallback = -1;
        for (int i = start; i < start + 9 && i < menu.slots.size(); i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty() || !st.is(Items.PAPER)) continue;
            String n = strip(st.getHoverName().getString()).toLowerCase(Locale.ROOT);
            if (n.contains("prev") || n.contains("back") || n.contains("zur") || n.contains("vorher")) continue;
            if (n.contains("next") || n.contains("weiter") || n.contains("nächste") || n.contains("naechste")) return i;
            fallback = i; // rechtestes Papier
        }
        return fallback;
    }

    /** Linksklick auf einen Slot des offenen Menues (nur fuer den Weiter-Button benutzt). */
    public static boolean clickSlot(Minecraft mc, AbstractContainerScreen<?> s, int slot) {
        AbstractContainerMenu menu = menuOf(s);
        if (mc.gameMode == null || mc.player == null) return false;
        if (slot < 0 || slot >= menu.slots.size() || !menu.getCarried().isEmpty()) return false;
        mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, mc.player);
        return true;
    }

    private static Offer parse(Minecraft mc, ItemStack stack, int slot, SniperConfig cfg) {
        List<Component> tip = stack.getTooltipLines(Item.TooltipContext.EMPTY, mc.player, TooltipFlag.NORMAL);
        if (tip.size() < 2) return null;

        List<String> lines = new ArrayList<>();
        for (Component c : tip) lines.add(strip(c.getString()));

        double price = -1;
        for (int i = 1; i < lines.size() && price < 0; i++) price = PriceParser.fromLine(lines.get(i));
        if (price < 0) return null;

        int count = Math.max(1, stack.getCount());
        double total = cfg.priceIsTotal ? price : price * count;
        double unit = cfg.priceIsTotal ? price / count : price;

        String name = strip(stack.getHoverName().getString());
        if (name.isEmpty()) name = lines.get(0);
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String itemId = key.getPath();

        List<String> enchants = new ArrayList<>();
        String spawner = "";
        for (int i = 1; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.isEmpty()) continue;
            if (isEnchantLine(l, cfg)) enchants.add(l);
            Matcher sm = SPAWN.matcher(l);
            if (spawner.isEmpty() && sm.matches()) spawner = sm.group(1).trim().toLowerCase(Locale.ROOT);
        }
        if (spawner.isEmpty() && name.toLowerCase(Locale.ROOT).endsWith(" spawner")) {
            spawner = name.substring(0, name.length() - " spawner".length()).trim().toLowerCase(Locale.ROOT);
        }
        Collections.sort(enchants);
        boolean hasEnchants = !enchants.isEmpty() || stack.isEnchanted();

        return new Offer(name, itemId, category(name, itemId), spawner, enchants, hasEnchants, total, unit, count, slot);
    }

    private static boolean isEnchantLine(String line, SniperConfig cfg) {
        Matcher m = ENCH.matcher(line);
        if (!m.matches()) return false;
        String n = m.group(1).trim().toLowerCase(Locale.ROOT);
        if (KNOWN_ENCHANTS.contains(n)) return true;
        for (String extra : cfg.extraEnchantNames) {
            if (n.equals(extra.toLowerCase(Locale.ROOT).trim())) return true;
        }
        return false;
    }

    /** Custom-Items: Name enthaelt "mace" -> Mace, "armor"/"armour" -> Armor. */
    static String category(String name, String itemId) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("mace") || itemId.contains("mace")) return "mace";
        if (n.contains("armor") || n.contains("armour")
                || itemId.endsWith("_helmet") || itemId.endsWith("_chestplate")
                || itemId.endsWith("_leggings") || itemId.endsWith("_boots")) return "armor";
        if (n.contains("spawner") || itemId.contains("spawner")) return "spawner";
        return "";
    }

    private static String strip(String s) {
        return s.replaceAll("§.", "").trim();
    }
}
