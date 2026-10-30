package com.auctionsniper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AlertManager {
    private static final long REMEMBER_MS = 30 * 60 * 1000L;
    private final Map<String, Long> alerted = new HashMap<>();
    public Offer lastAlert;

    public void clear() {
        alerted.clear();
        lastAlert = null;
    }

    public void process(Minecraft mc, List<Offer> offers) {
        long now = System.currentTimeMillis();
        alerted.values().removeIf(t -> now - t > REMEMBER_MS);
        SniperConfig cfg = SniperConfig.get();
        for (Offer o : offers) {
            o.alarmMatch = cfg.matches(o);
            if (!o.alarmMatch || alerted.containsKey(o.signature)) continue;
            alerted.put(o.signature, now);
            lastAlert = o;
            notify(mc, o);
        }
    }

    private void notify(Minecraft mc, Offer o) {
        if (mc.player == null) return;
        Component msg = Component.literal("[Auction Sniper] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(o.displayName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  " + PriceParser.format(o.unitPrice) + "/Stk")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
                .append(Component.literal("  (gesamt " + PriceParser.format(o.totalPrice) + ", Slot " + (o.slot + 1) + ")")
                        .withStyle(ChatFormatting.GRAY));
        mc.player.displayClientMessage(msg, false);
        mc.player.displayClientMessage(
                Component.literal("ALARM: " + o.displayName() + " für " + PriceParser.format(o.unitPrice) + "/Stk")
                        .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
    }
}
