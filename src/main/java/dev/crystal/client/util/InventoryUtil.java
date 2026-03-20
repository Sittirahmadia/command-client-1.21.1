package dev.crystal.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class InventoryUtil {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    public static int findInHotbar(Item item) {
        for (int i = 0; i < 9; i++)
            if (MC.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static int findInInventory(Item item) {
        for (int i = 0; i < 36; i++)
            if (MC.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static void switchTo(int slot) {
        if (slot >= 0 && slot <= 8) MC.player.getInventory().selectedSlot = slot;
    }

    public static boolean switchToItem(Item item) {
        int slot = findInHotbar(item);
        if (slot == -1) return false;
        switchTo(slot);
        return true;
    }

    public static boolean isHolding(Item item) {
        return MC.player.getMainHandStack().isOf(item);
    }
}
