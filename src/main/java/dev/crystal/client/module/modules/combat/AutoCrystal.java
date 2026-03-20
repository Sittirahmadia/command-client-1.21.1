package dev.crystal.client.module.modules.combat;

import dev.crystal.client.event.EventHandler;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Category;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.setting.Setting.*;
import dev.crystal.client.util.BlockUtil;
import dev.crystal.client.util.DamageUtil;
import dev.crystal.client.util.InventoryUtil;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class AutoCrystal extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    private final NumberSetting placeDelay  = register(new NumberSetting("Place Delay",  "Ticks between places",   2,   0,20,1));
    private final NumberSetting breakDelay  = register(new NumberSetting("Break Delay",  "Ticks between breaks",   1,   0,20,1));
    private final NumberSetting placeJitter = register(new NumberSetting("Place Jitter", "Random delay variance",  1,   0, 5,1));
    private final NumberSetting placeRange  = register(new NumberSetting("Place Range",  "Crystal place reach",    4.5, 1, 6,0.1));
    private final NumberSetting breakRange  = register(new NumberSetting("Break Range",  "Crystal break reach",    4.5, 1, 6,0.1));
    private final NumberSetting minDamage   = register(new NumberSetting("Min Damage",   "Min enemy damage",       4,   0,36,0.5));
    private final NumberSetting maxSelf     = register(new NumberSetting("Max Self",     "Max self damage",        8,   0,36,0.5));
    private final BoolSetting   antiSuicide = register(new BoolSetting  ("Anti Suicide", "Never lethal to self",   true));
    private final BoolSetting   silentSwap  = register(new BoolSetting  ("Silent Swap",  "Invisible slot switch",  false));
    private final BoolSetting   rotate      = register(new BoolSetting  ("Rotate",       "Server-side rotation",   true));
    private final BoolSetting   instantBreak= register(new BoolSetting  ("Instant Break","Break own placed crystals instantly", true));
    private final BoolSetting   rmbOnly     = register(new BoolSetting  ("RMB Only",     "Only while holding RMB", false));

    // ── State ─────────────────────────────────────────────────────────────────
    private int placeTick = 0, breakTick = 0;
    private int nextPlace = 2;
    private BlockPos lastPlaced = null;
    private final Set<Integer> brokenThisTick = new HashSet<>();
    private final Random rng = new Random();

    public AutoCrystal() {
        super("AutoCrystal", "Places and explodes end crystals", Category.COMBAT, -1);
    }

    @Override public void onEnable()  { super.onEnable();  reset(); }
    @Override public void onDisable() { super.onDisable(); reset(); }

    @EventHandler
    public void onTick(TickEvent e) {
        if (e.phase != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;
        if (rmbOnly.getValue()) {
            boolean rmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            if (!rmb) return;
        }

        brokenThisTick.clear();

        boolean holding   = mc.player.getMainHandStack().isOf(Items.END_CRYSTAL);
        boolean canSilent = silentSwap.getValue() && InventoryUtil.findInHotbar(Items.END_CRYSTAL) != -1;
        if (!holding && !canSilent) return;

        PlayerEntity target = bestTarget();
        float selfHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        // ── INSTANT BREAK own crystal ─────────────────────────────────────────
        if (instantBreak.getValue() && lastPlaced != null) {
            Vec3d crystalPos = Vec3d.ofCenter(lastPlaced.up());
            for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                    new Box(crystalPos.subtract(1,1,1), crystalPos.add(1,1,1)), x -> true)) {
                if (brokenThisTick.contains(c.getId())) continue;
                if (antiSuicide.getValue() && DamageUtil.crystalDamage(mc.player, c.getPos()) >= selfHp) continue;
                if (rotate.getValue()) faceVec(c.getPos().add(0,0.5,0));
                mc.interactionManager.attackEntity(mc.player, c);
                mc.player.swingHand(Hand.MAIN_HAND);
                brokenThisTick.add(c.getId());
                lastPlaced = null;
                breakTick = 0;
            }
        }

        // ── BREAK ─────────────────────────────────────────────────────────────
        if (breakTick >= breakDelay.intValue()) {
            EndCrystalEntity best = bestBreak(target, selfHp);
            if (best != null && !brokenThisTick.contains(best.getId())) {
                if (rotate.getValue()) faceVec(best.getPos().add(0,0.5,0));
                mc.interactionManager.attackEntity(mc.player, best);
                mc.player.swingHand(Hand.MAIN_HAND);
                brokenThisTick.add(best.getId());
                lastPlaced = null;
                breakTick = 0;
            }
        } else breakTick++;

        // ── PLACE ─────────────────────────────────────────────────────────────
        if (placeTick >= nextPlace) {
            BlockPos base = bestPlace(target, selfHp);
            if (base != null) {
                Vec3d face = Vec3d.ofCenter(base).add(0, 0.5, 0);
                if (rotate.getValue()) faceVec(face);
                int saved = mc.player.getInventory().selectedSlot;
                if (canSilent && !holding) InventoryUtil.switchToItem(Items.END_CRYSTAL);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                        new BlockHitResult(face, Direction.UP, base, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastPlaced = base;
                if (canSilent && !holding) InventoryUtil.switchTo(saved);
                placeTick = 0;
                int jit = placeJitter.intValue();
                nextPlace = placeDelay.intValue() + (jit > 0 ? rng.nextInt(jit * 2 + 1) - jit : 0);
            }
        } else placeTick++;
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    private BlockPos bestPlace(PlayerEntity target, float selfHp) {
        BlockPos pp = mc.player.getBlockPos(); Vec3d eye = mc.player.getEyePos();
        double range = placeRange.getValue();
        BlockPos best = null; double bestScore = -Double.MAX_VALUE;

        for (int dx=-5;dx<=5;dx++) for (int dz=-5;dz<=5;dz++) for (int dy=-2;dy<=3;dy++) {
            BlockPos base = pp.add(dx,dy,dz);
            if (!BlockUtil.canPlaceCrystal(base)) continue;
            Vec3d face = Vec3d.ofCenter(base).add(0,0.5,0);
            if (eye.distanceTo(face) > range) continue;

            Vec3d boom = Vec3d.ofCenter(base.up());
            float selfDmg = DamageUtil.crystalDamage(mc.player, boom);
            if (antiSuicide.getValue() && selfDmg >= selfHp) continue;
            if (selfDmg > maxSelf.getValue()) continue;

            double score;
            if (target == null) {
                score = -mc.player.squaredDistanceTo(face);
            } else {
                float eDmg = DamageUtil.crystalDamage(target, boom);
                if (eDmg < minDamage.getValue()) continue;
                score = eDmg * 2.0 - selfDmg;
            }
            if (score > bestScore) { bestScore = score; best = base; }
        }
        return best;
    }

    private EndCrystalEntity bestBreak(PlayerEntity target, float selfHp) {
        Vec3d eye = mc.player.getEyePos(); double range = breakRange.getValue();
        EndCrystalEntity best = null; float bestDmg = -1;
        for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                new Box(eye.subtract(range,range,range), eye.add(range,range,range)),
                en -> mc.player.distanceTo(en) <= range)) {
            if (antiSuicide.getValue() && DamageUtil.crystalDamage(mc.player, c.getPos()) >= selfHp) continue;
            float dmg = target != null ? DamageUtil.crystalDamage(target, c.getPos()) : 1f;
            if (dmg > bestDmg) { bestDmg = dmg; best = c; }
        }
        return best;
    }

    private PlayerEntity bestTarget() {
        if (mc.world == null) return null;
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive())
                .min(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p)))
                .orElse(null);
    }

    private void faceVec(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();
        double dx=target.x-eye.x, dy=target.y-eye.y, dz=target.z-eye.z, dist=Math.sqrt(dx*dx+dz*dz);
        mc.player.setYaw((float)(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz,dx))-90)+rng.nextGaussian()*0.7));
        mc.player.setPitch(MathHelper.clamp((float)(-Math.toDegrees(Math.atan2(dy,dist))+rng.nextGaussian()*0.4),-90,90));
    }

    private void reset() { placeTick = breakTick = 0; lastPlaced = null; nextPlace = placeDelay.intValue(); }
}
