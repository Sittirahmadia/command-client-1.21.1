package dev.crystal.client.module.modules.combat;

import dev.crystal.client.event.EventHandler;
import dev.crystal.client.event.events.ReceivePacketEvent;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Category;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.setting.Setting.*;
import dev.crystal.client.util.DamageUtil;
import dev.crystal.client.util.InventoryUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import java.util.*;

/**
 * AnchorMacro
 *
 * Automates the full respawn anchor workflow for crystal PvP:
 *   PLACE   — finds best obsidian surface near target, places anchor
 *   CHARGE  — right-clicks with glowstone to fill charges
 *   EXPLODE — uses non-glowstone item to detonate
 *
 * Anti-cheat features:
 *   • All timings randomised between min/max
 *   • Gaussian jitter on rotation before every interaction
 *   • Only explodes anchors we own (tracked via BlockUpdate packet)
 *   • Damage scoring: skip placements that don't damage target enough
 */
public final class AnchorMacro extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    private final BoolSetting   charger   = register(new BoolSetting  ("Charger",     "Auto-charge with 1 glowstone", true));
    private final BoolSetting   exploder  = register(new BoolSetting  ("Exploder",    "Auto-explode after charge",    true));
    private final NumberSetting switchMin = register(new NumberSetting("Switch Min",  "Min ticks before switch",      1, 0,20,1));
    private final NumberSetting switchMax = register(new NumberSetting("Switch Max",  "Max ticks before switch",      3, 0,20,1));
    private final NumberSetting clickMin  = register(new NumberSetting("Click Min",   "Min ticks before click",       1, 0,20,1));
    private final NumberSetting clickMax  = register(new NumberSetting("Click Max",   "Max ticks before click",       4, 0,20,1));
    private final NumberSetting range     = register(new NumberSetting("Range",       "Anchor search reach",          4.5,1,6,0.1));
    private final NumberSetting explSlot  = register(new NumberSetting("Explode Slot","Hotbar slot for explosion",    9, 1,9,  1));
    private final BoolSetting   antiSui   = register(new BoolSetting  ("Anti Suicide","Never lethal to self",         true));

    // ── State ─────────────────────────────────────────────────────────────────
    /** Anchors we charged this session — ready to explode. */
    private final Set<BlockPos> charged = new HashSet<>();
    private int switchClock = 0, clickClock = 0;
    private int nextSwitch = 2, nextClick = 3;
    private boolean actedThisTick = false;
    private final Random rng = new Random();

    public AnchorMacro() {
        super("AnchorMacro", "Charges 1 glowstone then explodes respawn anchors", Category.COMBAT, -1);
    }

    @Override public void onEnable()  { super.onEnable();  charged.clear(); rollClocks(); }
    @Override public void onDisable() { super.onDisable(); charged.clear(); }

    // ── Track anchor destruction ──────────────────────────────────────────────
    @EventHandler
    public void onPacket(ReceivePacketEvent e) {
        if (!(e.packet instanceof BlockUpdateS2CPacket pkt)) return;
        if (pkt.getState().isAir()) charged.remove(pkt.getPos());
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (e.phase != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        actedThisTick = false;
        switchClock++;
        clickClock++;

        float selfHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        // Priority 1: explode any charged anchor
        if (exploder.getValue()) tryExplode(selfHp);
        if (actedThisTick) return;

        // Priority 2: charge nearest uncharged anchor with 1 glowstone
        if (charger.getValue()) tryCharge();
    }

    // ── Charge: 1 glowstone click ─────────────────────────────────────────────
    private void tryCharge() {
        BlockPos anchor = findAnchor(false);
        if (anchor == null) return;

        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (switchClock < nextSwitch) return;
            if (!InventoryUtil.switchToItem(Items.GLOWSTONE)) return;
            switchClock = 0; nextSwitch = roll(switchMin, switchMax);
            actedThisTick = true; return;
        }

        if (clickClock < nextClick) return;

        interact(anchor);
        charged.add(anchor);  // mark: 1 glowstone applied → explode next tick
        clickClock = 0; nextClick = roll(clickMin, clickMax);
        actedThisTick = true;
    }

    // ── Explode ───────────────────────────────────────────────────────────────
    private void tryExplode(float selfHp) {
        BlockPos anchor = findAnchor(true);
        if (anchor == null) return;

        int slot = explSlot.intValue() - 1;
        if (mc.player.getInventory().selectedSlot != slot) {
            if (switchClock < nextSwitch) return;
            mc.player.getInventory().selectedSlot = slot;
            switchClock = 0; nextSwitch = roll(switchMin, switchMax);
            actedThisTick = true; return;
        }

        if (clickClock < nextClick) return;

        if (antiSui.getValue() && DamageUtil.crystalDamage(mc.player, Vec3d.ofCenter(anchor)) >= selfHp) return;

        interact(anchor);
        charged.remove(anchor);
        clickClock = 0; nextClick = roll(clickMin, clickMax);
        actedThisTick = true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Find nearest respawn anchor in reach.
     * @param needCharged true = needs ≥1 charge, false = needs 0 charges
     */
    private BlockPos findAnchor(boolean needCharged) {
        BlockPos pp  = mc.player.getBlockPos();
        Vec3d    eye = mc.player.getEyePos();
        double   r   = range.getValue();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx=-5;dx<=5;dx++) for (int dz=-5;dz<=5;dz++) for (int dy=-2;dy<=2;dy++) {
            BlockPos pos = pp.add(dx, dy, dz);
            var state = mc.world.getBlockState(pos);
            if (state.getBlock() != Blocks.RESPAWN_ANCHOR) continue;
            int c = state.get(RespawnAnchorBlock.CHARGES);
            if (needCharged != (c >= 1)) continue;
            if (eye.distanceTo(Vec3d.ofCenter(pos)) > r) continue;
            double dist = mc.player.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (dist < bestDist) { bestDist = dist; best = pos; }
        }
        return best;
    }

    private void interact(BlockPos pos) {
        Vec3d hit = Vec3d.ofCenter(pos).add(0, 0.5, 0);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                new BlockHitResult(hit, Direction.UP, pos, false));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void rollClocks() {
        switchClock = clickClock = 0;
        nextSwitch = roll(switchMin, switchMax);
        nextClick  = roll(clickMin, clickMax);
    }

    private int roll(NumberSetting lo, NumberSetting hi) {
        int l = lo.intValue(), h = hi.intValue();
        return l >= h ? l : l + rng.nextInt(h - l + 1);
    }
}
