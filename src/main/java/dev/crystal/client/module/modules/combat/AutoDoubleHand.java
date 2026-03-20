package dev.crystal.client.module.modules.combat;

import dev.crystal.client.event.EventHandler;
import dev.crystal.client.event.events.ReceivePacketEvent;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Category;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.setting.Setting.*;
import dev.crystal.client.util.BlockUtil;
import dev.crystal.client.util.DamageUtil;
import dev.crystal.client.util.InventoryUtil;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoDoubleHand
 *
 * Switches hotbar to totem on:
 *   1. POP       — EntityStatus 35 packet (totem consumed)
 *   2. HEALTH    — HP below threshold
 *   3. PREDICT   — any crystal/placement would deal lethal damage this tick
 */
public final class AutoDoubleHand extends Module {

    private final BoolSetting   onPop      = register(new BoolSetting  ("On Pop",      "Switch on totem pop",         true));
    private final BoolSetting   onHealth   = register(new BoolSetting  ("On Health",   "Switch below HP",             true));
    private final NumberSetting health     = register(new NumberSetting("Health",      "HP trigger",                  4,   1,20,0.5));
    private final BoolSetting   predict    = register(new BoolSetting  ("Predict",     "Pre-switch if crystal kills",  true));
    private final NumberSetting buffer     = register(new NumberSetting("Buffer",      "Damage safety margin",        1.5, 0,10,0.5));
    private final BoolSetting   predictPos = register(new BoolSetting  ("Predict Pos", "Check obsidian placements",   true));
    private final NumberSetting predRange  = register(new NumberSetting("Pred Range",  "Obsidian scan radius",         6,   1,12,0.5));
    private final BoolSetting   enemyNear  = register(new BoolSetting  ("Enemy Near",  "Only predict near enemies",   true));
    private final NumberSetting enemyDist  = register(new NumberSetting("Enemy Dist",  "Enemy trigger radius",         8,   1,20,0.5));

    private volatile boolean popPending      = false;
    private boolean          healthLatch     = false;

    public AutoDoubleHand() {
        super("AutoDoubleHand", "Switches to totem on pop, low HP, or lethal prediction", Category.COMBAT, -1);
    }

    @Override public void onEnable()  { super.onEnable();  popPending = false; healthLatch = false; }
    @Override public void onDisable() { super.onDisable(); }

    @EventHandler
    public void onPacket(ReceivePacketEvent e) {
        if (!(e.packet instanceof EntityStatusS2CPacket pkt)) return;
        if (mc.player == null || mc.world == null) return;
        if (pkt.getStatus() == 35 && pkt.getEntity(mc.world) == mc.player) popPending = true;
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (e.phase != TickEvent.Phase.PRE || mc.player == null || mc.world == null) return;

        // 1. On Pop
        if (onPop.getValue() && popPending) {
            popPending = false;
            doSwitch();
            return;
        }

        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        // 2. On Health
        if (onHealth.getValue()) {
            if (hp <= health.floatValue() && !healthLatch) {
                healthLatch = true;
                doSwitch();
                return;
            }
            if (hp > health.floatValue()) healthLatch = false;
        }

        // 3. Predict
        if (!predict.getValue()) return;

        if (enemyNear.getValue()) {
            boolean near = mc.world.getPlayers().stream()
                    .anyMatch(p -> p != mc.player && mc.player.distanceTo(p) <= enemyDist.getValue());
            if (!near) return;
        }

        float threshold = hp - buffer.floatValue();
        List<Vec3d> positions = collectCrystalPositions();

        for (Vec3d pos : positions) {
            if (DamageUtil.crystalDamage(mc.player, pos) >= threshold) {
                doSwitch();
                return;
            }
        }
    }

    private List<Vec3d> collectCrystalPositions() {
        List<Vec3d> list = new ArrayList<>();
        Vec3d pp = mc.player.getPos();

        // Existing crystals in world
        mc.world.getEntitiesByClass(EndCrystalEntity.class,
                new Box(pp.subtract(8,8,8), pp.add(8,8,8)), x -> true)
                .forEach(c -> list.add(c.getPos()));

        // Predicted placements on obsidian/bedrock
        if (predictPos.getValue()) {
            BlockPos bp = mc.player.getBlockPos();
            int r = predRange.intValue();
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = -2; dy <= 3; dy++) {
                        BlockPos base = bp.add(dx, dy, dz);
                        if (BlockUtil.canPlaceCrystal(base))
                            list.add(Vec3d.ofCenter(base.up()));
                    }
        }
        return list;
    }

    private void doSwitch() {
        if (mc.player == null) return;
        if (mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        InventoryUtil.switchToItem(Items.TOTEM_OF_UNDYING);
    }
}
