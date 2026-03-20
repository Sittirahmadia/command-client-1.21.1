package dev.crystal.client.module.modules.combat;

import dev.crystal.client.event.EventHandler;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Category;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.setting.Setting.*;
import dev.crystal.client.util.InventoryUtil;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;

import java.util.Comparator;

/**
 * CartAssist — rewrite
 *
 * Approach: no packet dependency, purely tick-based.
 *
 * IDLE     — watch player holding bow/crossbow and looking at a solid block
 * ARMED    — player is charging the bow (RMB held)
 * FIRED    — detected bow release, snapshot aim position, scan for arrow near target
 * PLACE_RAIL — place rail on the ground at arrow land spot
 * PLACE_CART — place TNT minecart on the rail (same tick or next tick)
 * RESTORE  — restore slot, ready for next shot
 *
 * Arrow detection: every tick in FIRED state we scan all projectile entities
 * near the target position. When one is found on the ground (inGround), trigger.
 * Timeout after maxWait ticks if no arrow found.
 */
public final class CartAssist extends Module {

    private final NumberSetting scanRadius  = register(new NumberSetting("Scan Radius", "Block radius to scan for landed arrow", 3.0, 0.5, 8, 0.5));
    private final NumberSetting cartDelay   = register(new NumberSetting("Cart Delay",  "Ticks between rail and cart (0=same tick)", 1, 0, 10, 1));
    private final NumberSetting maxWait     = register(new NumberSetting("Max Wait",    "Ticks to wait for arrow before reset",  80, 10,200, 5));
    private final BoolSetting   crossbow    = register(new BoolSetting  ("Crossbow",    "Also trigger on crossbow fire",         false));
    private final BoolSetting   autoBack    = register(new BoolSetting  ("Auto Back",   "Restore slot after placing cart",       true));
    private final BoolSetting   requireAim  = register(new BoolSetting  ("Require Aim", "Only arm when aiming at solid block",   true));

    private enum Phase { IDLE, ARMED, FIRED, PLACE_RAIL, PLACE_CART, RESTORE }

    private Phase    phase       = Phase.IDLE;
    private BlockPos aimPos      = null;   // block aimed at when bow was charged
    private BlockPos railPos     = null;   // where we placed the rail
    private int      clock       = 0;
    private int      savedSlot   = -1;
    private boolean  wasCharging = false;

    public CartAssist() {
        super("CartAssist", "Places rail + TNT cart where your arrow lands", Category.COMBAT, -1);
    }

    @Override public void onEnable()  { super.onEnable();  resetAll(); }
    @Override public void onDisable() { super.onDisable(); restoreSlot(); resetAll(); }

    @EventHandler
    public void onTick(TickEvent e) {
        if (e.phase != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;

        switch (phase) {

            // ── IDLE ─────────────────────────────────────────────────────────
            case IDLE -> {
                boolean holdingBow = mc.player.getMainHandStack().getItem() instanceof BowItem
                        || (crossbow.getValue() && mc.player.getMainHandStack().getItem() instanceof CrossbowItem);
                if (!holdingBow) { wasCharging = false; return; }

                boolean charging = mc.player.isUsingItem();

                if (charging && !wasCharging) {
                    // Just started charging — snapshot aim
                    if (requireAim.getValue()) {
                        if (mc.crosshairTarget instanceof BlockHitResult bhr
                                && bhr.getType() == HitResult.Type.BLOCK) {
                            aimPos = bhr.getBlockPos();
                        } else {
                            wasCharging = true;
                            return; // not aiming at block, don't arm
                        }
                    } else {
                        aimPos = mc.crosshairTarget instanceof BlockHitResult bhr
                                ? bhr.getBlockPos() : mc.player.getBlockPos().offset(mc.player.getHorizontalFacing(), 5);
                    }
                    phase = Phase.ARMED;
                }
                wasCharging = charging;
            }

            // ── ARMED: update aim pos while still charging ────────────────────
            case ARMED -> {
                boolean stillCharging = mc.player.isUsingItem();

                // Update aim while charging
                if (stillCharging) {
                    if (mc.crosshairTarget instanceof BlockHitResult bhr
                            && bhr.getType() == HitResult.Type.BLOCK) {
                        aimPos = bhr.getBlockPos();
                    }
                    wasCharging = true;
                    return;
                }

                // Bow released — start scanning for arrow
                if (wasCharging && aimPos != null) {
                    clock = 0;
                    phase = Phase.FIRED;
                } else {
                    resetAll();
                }
                wasCharging = false;
            }

            // ── FIRED: scan for arrow landing near aim position ───────────────
            case FIRED -> {
                if (++clock > maxWait.intValue()) { resetAll(); return; }
                if (aimPos == null) { resetAll(); return; }

                // Scan all projectiles near aimPos for one that has landed (inGround)
                double r = scanRadius.getValue();
                Vec3d center = Vec3d.ofCenter(aimPos);

                var landed = mc.world.getEntitiesByClass(
                        PersistentProjectileEntity.class,
                        new Box(center.subtract(r, r + 3, r), center.add(r, r, r)),
                        proj -> proj.isOnGround()
                                && proj.getPos().distanceTo(center) <= r + 2.0
                ).stream().min(Comparator.comparingDouble(
                        proj -> proj.getPos().squaredDistanceTo(center))
                ).orElse(null);

                if (landed == null) return;

                // Found landed arrow — place rail at ground beneath it
                Vec3d arrowPos = landed.getPos();
                BlockPos groundPos = findGround(BlockPos.ofFloored(arrowPos));
                if (groundPos == null) { resetAll(); return; }

                aimPos   = groundPos;
                savedSlot = mc.player.getInventory().selectedSlot;
                phase    = Phase.PLACE_RAIL;
                // fall through to PLACE_RAIL immediately this tick
                placeRail();
            }

            // ── PLACE_RAIL ────────────────────────────────────────────────────
            case PLACE_RAIL -> {
                // Already handled in FIRED fall-through for first tick.
                // This case handles the cartDelay == 0 continuation.
                if (cartDelay.intValue() == 0) {
                    placeCart();
                } else {
                    clock = 0;
                    phase = Phase.PLACE_CART;
                }
            }

            // ── PLACE_CART: wait cartDelay then spawn cart ────────────────────
            case PLACE_CART -> {
                if (++clock < cartDelay.intValue()) return;
                placeCart();
            }

            // ── RESTORE ───────────────────────────────────────────────────────
            case RESTORE -> {
                if (autoBack.getValue()) restoreSlot();
                resetAll();
            }
        }
    }

    // ── Place helpers ─────────────────────────────────────────────────────────

    private void placeRail() {
        if (aimPos == null) { resetAll(); return; }

        if (!InventoryUtil.switchToItem(Items.RAIL)) {
            restoreSlot(); resetAll(); return;
        }

        Vec3d hit = Vec3d.ofCenter(aimPos).add(0, 0.5, 0);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, aimPos, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        // Find where rail landed
        railPos = findRailNear(aimPos);

        if (cartDelay.intValue() == 0) {
            phase = Phase.PLACE_RAIL; // will immediately fall to placeCart
        } else {
            clock = 0;
            phase = Phase.PLACE_CART;
        }
    }

    private void placeCart() {
        // Re-find rail in case block updated
        BlockPos rail = railPos != null ? railPos : findRailNear(aimPos);
        if (rail == null) { restoreSlot(); resetAll(); return; }

        if (!InventoryUtil.switchToItem(Items.TNT_MINECART)) {
            restoreSlot(); resetAll(); return;
        }

        Vec3d hit = Vec3d.ofCenter(rail).add(0, 0.5, 0);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, rail, false));
        mc.player.swingHand(Hand.MAIN_HAND);

        phase = Phase.RESTORE;
    }

    // ── Block helpers ─────────────────────────────────────────────────────────

    /** Walk down from pos until we hit a solid block, return its top face. */
    private BlockPos findGround(BlockPos from) {
        for (int dy = 0; dy >= -4; dy--) {
            BlockPos check = from.add(0, dy, 0);
            if (!mc.world.isAir(check)) return check;
        }
        return null;
    }

    /** Scan a small area around pos for any rail block. */
    private BlockPos findRailNear(BlockPos pos) {
        if (pos == null) return null;
        for (int dx=-2;dx<=2;dx++) for (int dz=-2;dz<=2;dz++) for (int dy=0;dy<=2;dy++) {
            BlockPos p = pos.add(dx, dy, dz);
            if (mc.world.getBlockState(p).getBlock() instanceof AbstractRailBlock) return p;
        }
        return null;
    }

    private void restoreSlot() {
        if (savedSlot != -1 && mc.player != null)
            mc.player.getInventory().selectedSlot = savedSlot;
        savedSlot = -1;
    }

    private void resetAll() {
        phase       = Phase.IDLE;
        aimPos      = null;
        railPos     = null;
        clock       = 0;
        wasCharging = false;
    }
}
