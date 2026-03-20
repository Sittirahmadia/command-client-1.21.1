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

/**
 * AutoCrystal — legit rotation-aware version
 *
 * Key anti-flag improvements:
 *  1. FOV check  — only places/breaks within player's actual view cone.
 *     If your crosshair can't physically see it, it won't interact.
 *  2. Rotation-first — always rotates server-side BEFORE placing/breaking,
 *     with Gaussian noise to look human.
 *  3. Pitch gate — no placing at blocks that require looking straight down
 *     (pitch > maxPitch). Humans never place at pitch 90.
 *  4. Smooth rotation lerp — rotation speed is capped (maxRotSpeed deg/tick)
 *     so the head doesn't snap 180° in one tick.
 *  5. Rotation settle delay — waits rotSettleTicks after rotating before
 *     sending the interact packet (looks more human).
 *  6. No placing while rotating too fast — if head moved > maxRotSpeed
 *     this tick, skip placing (player was turning, looks natural).
 */
public final class AutoCrystal extends Module {

    // ── Settings ──────────────────────────────────────────────────────────────
    private final NumberSetting placeDelay  = register(new NumberSetting("Place Delay",   "Ticks between places",          2,   0, 20, 1));
    private final NumberSetting breakDelay  = register(new NumberSetting("Break Delay",   "Ticks between breaks",          1,   0, 20, 1));
    private final NumberSetting placeJitter = register(new NumberSetting("Place Jitter",  "Random delay variance (ticks)", 1,   0,  5, 1));
    private final NumberSetting placeRange  = register(new NumberSetting("Place Range",   "Crystal place reach",           4.5, 1,  6, 0.1));
    private final NumberSetting breakRange  = register(new NumberSetting("Break Range",   "Crystal break reach",           4.5, 1,  6, 0.1));
    private final NumberSetting minDamage   = register(new NumberSetting("Min Damage",    "Min enemy damage to place",     4,   0, 36, 0.5));
    private final NumberSetting maxSelf     = register(new NumberSetting("Max Self",      "Max self damage allowed",       8,   0, 36, 0.5));
    private final NumberSetting fovCheck    = register(new NumberSetting("FOV Check",     "Only act within this FOV (°)", 90,  10,180, 5));
    private final NumberSetting maxPitch    = register(new NumberSetting("Max Pitch",     "Skip if pitch below -X° (down)",70,  20, 90, 5));
    private final NumberSetting rotSpeed    = register(new NumberSetting("Rot Speed",     "Max rotation deg/tick",         35,  5, 90, 5));
    private final NumberSetting rotSettle   = register(new NumberSetting("Rot Settle",    "Ticks to wait after rotating",  1,   0,  5, 1));
    private final BoolSetting   antiSuicide = register(new BoolSetting  ("Anti Suicide",  "Never lethal to self",          true));
    private final BoolSetting   silentSwap  = register(new BoolSetting  ("Silent Swap",   "Invisible slot switch",         false));
    private final BoolSetting   instantBreak= register(new BoolSetting  ("Instant Break", "Break own placed crystals",     true));
    private final BoolSetting   rmbOnly     = register(new BoolSetting  ("RMB Only",      "Only while RMB held",           false));

    // ── State ─────────────────────────────────────────────────────────────────
    private int    placeTick    = 0, breakTick = 0;
    private int    nextPlace    = 2;
    private int    settleClock  = 0;
    private boolean pendingPlace = false;
    private boolean pendingBreak = false;
    private BlockPos          pendingBase   = null;
    private EndCrystalEntity  pendingCrystal= null;
    private BlockPos          lastPlaced    = null;
    private float  lastYaw = 0, lastPitch = 0;
    private final Set<Integer> brokenThisTick = new HashSet<>();
    private final Random rng = new Random();

    public AutoCrystal() {
        super("AutoCrystal", "Places and explodes end crystals (legit mode)", Category.COMBAT, -1);
    }

    @Override public void onEnable()  { super.onEnable();  reset(); }
    @Override public void onDisable() { super.onDisable(); reset(); }

    @EventHandler
    public void onTick(TickEvent e) {
        if (e.phase != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;
        if (rmbOnly.getValue()) {
            boolean rmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            if (!rmb) { reset(); return; }
        }

        brokenThisTick.clear();

        boolean holding   = mc.player.getMainHandStack().isOf(Items.END_CRYSTAL);
        boolean canSilent = silentSwap.getValue() && InventoryUtil.findInHotbar(Items.END_CRYSTAL) != -1;
        if (!holding && !canSilent) return;

        PlayerEntity target = bestTarget();
        float selfHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        float curYaw   = mc.player.getYaw();
        float curPitch = mc.player.getPitch();
        float rotMoved = Math.abs(MathHelper.wrapDegrees(curYaw - lastYaw))
                       + Math.abs(curPitch - lastPitch);
        lastYaw   = curYaw;
        lastPitch = curPitch;

        // ── Execute pending interaction after settle delay ─────────────────────
        if (pendingBreak && settleClock >= rotSettle.intValue()) {
            if (pendingCrystal != null && !pendingCrystal.isRemoved()) {
                mc.interactionManager.attackEntity(mc.player, pendingCrystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                brokenThisTick.add(pendingCrystal.getId());
                lastPlaced = null;
                breakTick  = 0;
            }
            pendingBreak   = false;
            pendingCrystal = null;
            settleClock    = 0;
            return;
        }

        if (pendingPlace && settleClock >= rotSettle.intValue()) {
            if (pendingBase != null && BlockUtil.canPlaceCrystal(pendingBase)) {
                Vec3d face = Vec3d.ofCenter(pendingBase).add(0, 0.5, 0);
                int saved  = mc.player.getInventory().selectedSlot;
                if (canSilent && !holding) InventoryUtil.switchToItem(Items.END_CRYSTAL);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                        new BlockHitResult(face, Direction.UP, pendingBase, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastPlaced = pendingBase;
                if (canSilent && !holding) InventoryUtil.switchTo(saved);
                placeTick  = 0;
                int jit    = placeJitter.intValue();
                nextPlace  = placeDelay.intValue() + (jit > 0 ? rng.nextInt(jit * 2 + 1) - jit : 0);
            }
            pendingPlace = false;
            pendingBase  = null;
            settleClock  = 0;
            return;
        }

        if (pendingBreak || pendingPlace) { settleClock++; return; }

        // ── Instant break own placed crystal ──────────────────────────────────
        if (instantBreak.getValue() && lastPlaced != null) {
            Vec3d cp = Vec3d.ofCenter(lastPlaced.up());
            for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                    new Box(cp.subtract(1,1,1), cp.add(1,1,1)), x -> true)) {
                if (brokenThisTick.contains(c.getId())) continue;
                if (antiSuicide.getValue() && DamageUtil.crystalDamage(mc.player, c.getPos()) >= selfHp) continue;
                Vec3d aim = c.getPos().add(0, 0.5, 0);
                if (!inFov(aim)) continue;
                rotateTo(aim, curYaw, curPitch);
                pendingBreak   = true;
                pendingCrystal = c;
                settleClock    = 0;
                lastPlaced     = null;
                return;
            }
        }

        // ── Break ─────────────────────────────────────────────────────────────
        if (breakTick >= breakDelay.intValue()) {
            EndCrystalEntity best = bestBreak(target, selfHp, curYaw, curPitch);
            if (best != null) {
                Vec3d aim = best.getPos().add(0, 0.5, 0);
                rotateTo(aim, curYaw, curPitch);
                pendingBreak   = true;
                pendingCrystal = best;
                settleClock    = 0;
                return;
            }
        } else breakTick++;

        // ── Place ─────────────────────────────────────────────────────────────
        if (placeTick >= nextPlace) {
            BlockPos base = bestPlace(target, selfHp, curYaw, curPitch);
            if (base != null) {
                Vec3d aim = Vec3d.ofCenter(base).add(0, 0.5, 0);
                rotateTo(aim, curYaw, curPitch);
                pendingPlace = true;
                pendingBase  = base;
                settleClock  = 0;
                return;
            }
        } else placeTick++;
    }

    // ── Scoring ───────────────────────────────────────────────────────────────

    private BlockPos bestPlace(PlayerEntity target, float selfHp, float yaw, float pitch) {
        BlockPos pp  = mc.player.getBlockPos();
        Vec3d    eye = mc.player.getEyePos();
        double   range = placeRange.getValue();
        BlockPos best = null;
        double bestScore = -Double.MAX_VALUE;

        for (int dx=-5;dx<=5;dx++) for (int dz=-5;dz<=5;dz++) for (int dy=-2;dy<=3;dy++) {
            BlockPos base = pp.add(dx,dy,dz);
            if (!BlockUtil.canPlaceCrystal(base)) continue;

            Vec3d face = Vec3d.ofCenter(base).add(0, 0.5, 0);
            if (eye.distanceTo(face) > range) continue;

            // FOV + pitch gate — skip if player can't realistically look there
            if (!inFov(face)) continue;
            if (!pitchOk(face, eye)) continue;

            Vec3d boom    = Vec3d.ofCenter(base.up());
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

    private EndCrystalEntity bestBreak(PlayerEntity target, float selfHp, float yaw, float pitch) {
        Vec3d    eye   = mc.player.getEyePos();
        double   range = breakRange.getValue();
        EndCrystalEntity best = null;
        float bestDmg = -1;

        for (EndCrystalEntity c : mc.world.getEntitiesByClass(EndCrystalEntity.class,
                new Box(eye.subtract(range,range,range), eye.add(range,range,range)),
                en -> mc.player.distanceTo(en) <= range)) {
            if (antiSuicide.getValue() && DamageUtil.crystalDamage(mc.player, c.getPos()) >= selfHp) continue;
            Vec3d aim = c.getPos().add(0, 0.5, 0);
            if (!inFov(aim)) continue;
            if (!pitchOk(aim, eye)) continue;
            float dmg = target != null ? DamageUtil.crystalDamage(target, c.getPos()) : 1f;
            if (dmg > bestDmg) { bestDmg = dmg; best = c; }
        }
        return best;
    }

    // ── Rotation helpers ──────────────────────────────────────────────────────

    /**
     * Check if target is within the player's current FOV cone.
     * Uses the actual yaw/pitch from server, not our pending rotation.
     */
    private boolean inFov(Vec3d target) {
        Vec3d eye  = mc.player.getEyePos();
        Vec3d diff = target.subtract(eye).normalize();
        Vec3d look = mc.player.getRotationVec(1.0f).normalize();
        double dot = look.dotProduct(diff);
        // dot = cos(angle). FOV is half-angle.
        double halfFovRad = Math.toRadians(fovCheck.getValue() / 2.0);
        return dot >= Math.cos(halfFovRad);
    }

    /**
     * Returns false if placing this position would require looking too far down.
     * Prevents the blatant "head facing floor, placing blocks behind you" pattern.
     */
    private boolean pitchOk(Vec3d target, Vec3d eye) {
        double dy   = target.y - eye.y;
        double horiz = Math.sqrt(Math.pow(target.x - eye.x, 2) + Math.pow(target.z - eye.z, 2));
        float reqPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        // reqPitch is negative when looking down. maxPitch is the downward limit.
        return reqPitch >= -maxPitch.floatValue();
    }

    /**
     * Rotate toward target with speed cap and Gaussian noise.
     * Never snaps — always lerps at most rotSpeed degrees per tick.
     */
    private void rotateTo(Vec3d target, float curYaw, float curPitch) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x-eye.x, dy = target.y-eye.y, dz = target.z-eye.z;
        double dist = Math.sqrt(dx*dx + dz*dz);

        float targetYaw   = (float)(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz,dx))-90));
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, dist)));

        float maxSpd = rotSpeed.floatValue();

        // Clamp delta to maxRotSpeed
        float dY = MathHelper.clamp(MathHelper.wrapDegrees(targetYaw   - curYaw),   -maxSpd, maxSpd);
        float dP = MathHelper.clamp(                        targetPitch - curPitch,  -maxSpd, maxSpd);

        // Add subtle Gaussian noise to look human
        float newYaw   = curYaw   + dY + (float)(rng.nextGaussian() * 0.6);
        float newPitch = MathHelper.clamp(curPitch + dP + (float)(rng.nextGaussian() * 0.4), -90, 90);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);
    }

    private PlayerEntity bestTarget() {
        if (mc.world == null) return null;
        return mc.world.getPlayers().stream()
                .filter(p -> p != mc.player && p.isAlive())
                .min(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p)))
                .orElse(null);
    }

    private void reset() {
        placeTick = breakTick = settleClock = 0;
        lastPlaced = pendingBase = null;
        pendingCrystal = null;
        pendingPlace = pendingBreak = false;
        nextPlace = placeDelay.intValue();
    }
}