package dev.crystal.client.module.modules.combat;

import dev.crystal.client.event.EventHandler;
import dev.crystal.client.event.events.ReceivePacketEvent;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Category;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.setting.Setting.*;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoInventoryTotem — instant, no delay
 *
 * Every tick the inventory screen is open:
 *   → scan inventory for a totem
 *   → if offhand is empty: swap instantly (button 40 = F-key swap with offhand)
 *   → if main hand also needs totem (Force Main): swap that too same tick
 *
 * On pop (EntityStatus 35): cache invalidated so next tick re-scans and swaps.
 * No open delay, no swap delay, no random order — pure speed.
 *
 * Screen slot map (1.21 player inventory):
 *   Slots  9–35 → main inventory rows
 *   Slots 36–44 → hotbar (hotbar index i → screen slot i+36)
 */
public final class AutoInventoryTotem extends Module {

    private final BoolSetting   hotbarFB  = register(new BoolSetting("Hotbar FB",  "Also search hotbar slots",  true));
    private final BoolSetting   forceMain = register(new BoolSetting("Force Main", "Also fill main hand slot",  false));

    private volatile boolean popPending = false;
    private boolean cacheValid = false;
    private int cachedOff  = -1;
    private int cachedMain = -1;

    public AutoInventoryTotem() {
        super("AutoInventoryTotem", "Instantly swaps totem into offhand — no delay", Category.COMBAT, -1);
    }

    @Override public void onEnable()  { super.onEnable();  reset(); }
    @Override public void onDisable() { super.onDisable(); reset(); }

    // ── Pop detection: invalidate cache so next tick re-scans ─────────────────
    @EventHandler
    public void onPacket(ReceivePacketEvent e) {
        if (!(e.packet instanceof EntityStatusS2CPacket pkt)) return;
        if (mc.player == null || mc.world == null) return;
        if (pkt.getStatus() == 35 && pkt.getEntity(mc.world) == mc.player) {
            cacheValid = false;
            popPending = true;
        }
    }

    // ── Every tick: swap immediately, no waiting ───────────────────────────────
    @EventHandler
    public void onTick(TickEvent e) {
        if (e.phase != TickEvent.Phase.PRE || mc.player == null) return;

        // Must have inventory screen open
        if (!(mc.currentScreen instanceof InventoryScreen inv)) {
            if (popPending || cacheValid) reset();
            return;
        }

        popPending = false;

        // Rebuild cache once per need
        if (!cacheValid) {
            buildCache();
            cacheValid = true;
        }

        var inventory = mc.player.getInventory();
        int syncId = inv.getScreenHandler().syncId;

        // Fill offhand instantly
        if (!inventory.offHand.get(0).isOf(Items.TOTEM_OF_UNDYING)) {
            if (cachedOff != -1) {
                mc.interactionManager.clickSlot(syncId, cachedOff, 40, SlotActionType.SWAP, mc.player);
                cacheValid = false;   // slot changed, rescan next tick
                return;
            }
        }

        // Fill main hand instantly (same tick if offhand already full)
        if (forceMain.getValue()
                && !inventory.main.get(inventory.selectedSlot).isOf(Items.TOTEM_OF_UNDYING)
                && cachedMain != -1) {
            mc.interactionManager.clickSlot(syncId, cachedMain,
                    inventory.selectedSlot, SlotActionType.SWAP, mc.player);
            cacheValid = false;
        }
    }

    // ── Cache: find totem slots once, reuse until inventory changes ────────────
    private void buildCache() {
        var inv = mc.player.getInventory();
        int sel = inv.selectedSlot;
        List<Integer> candidates = new ArrayList<>();

        // Main inventory rows first — won't disturb hotbar layout
        for (int i = 9; i < 36; i++)
            if (inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING))
                candidates.add(i);

        // Hotbar fallback — skip currently selected slot
        if (hotbarFB.getValue())
            for (int i = 0; i < 9; i++)
                if (i != sel && inv.main.get(i).isOf(Items.TOTEM_OF_UNDYING))
                    candidates.add(i + 36);

        cachedOff = candidates.isEmpty() ? -1 : candidates.get(0);

        if (forceMain.getValue()) {
            boolean offFull = inv.offHand.get(0).isOf(Items.TOTEM_OF_UNDYING);
            cachedMain = offFull && !candidates.isEmpty() ? candidates.get(0)
                       : candidates.size() >= 2           ? candidates.get(1) : -1;
        } else {
            cachedMain = -1;
        }
    }

    private void reset() {
        cacheValid = false;
        cachedOff  = -1;
        cachedMain = -1;
        popPending = false;
    }
}

