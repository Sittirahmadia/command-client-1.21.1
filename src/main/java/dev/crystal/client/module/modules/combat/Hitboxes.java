package dev.crystal.client.module.modules.combat;

import dev.crystal.client.event.EventHandler;
import dev.crystal.client.event.events.TickEvent;
import dev.crystal.client.module.Category;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.setting.Setting.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;

import java.util.Random;

public final class Hitboxes extends Module {

    private final NumberSetting expand     = register(new NumberSetting("Expand",     "Hitbox expansion (blocks)", 0.08, 0, 0.5, 0.01));
    private final BoolSetting   playersOnly= register(new BoolSetting  ("Players Only","Only expand players",      true));
    private final BoolSetting   weaponOnly = register(new BoolSetting  ("Weapon Only", "Only with sword/axe",      false));
    private final BoolSetting   pulse      = register(new BoolSetting  ("Pulse",       "Pulse on/off cycle",       true));
    private final NumberSetting pulseOn    = register(new NumberSetting("Pulse On",    "Ticks expanded",           8,   1,20,1));
    private final NumberSetting pulseOff   = register(new NumberSetting("Pulse Off",   "Ticks collapsed",          4,   1,20,1));
    private final BoolSetting   jitter     = register(new BoolSetting  ("Jitter",      "Random variance",          true));

    private int     pulseTick = 0;
    private boolean pulseOn2  = true;
    private final Random rng  = new Random();

    public Hitboxes() { super("Hitboxes", "Expands entity hitboxes for easier hitting", Category.COMBAT, -1); }

    @Override public void onEnable()  { super.onEnable();  pulseTick = 0; pulseOn2 = true; }

    @EventHandler
    public void onTick(TickEvent e) {
        if (e.phase != TickEvent.Phase.PRE) return;
        if (pulse.getValue()) {
            if (++pulseTick >= (pulseOn2 ? pulseOn.intValue() : pulseOff.intValue())) {
                pulseOn2 = !pulseOn2;
                pulseTick = 0;
            }
        } else pulseOn2 = true;
    }

    public double getExpansion(Entity entity) {
        if (!isEnabled() || entity == mc.player) return 0;
        if (playersOnly.getValue() && !(entity instanceof PlayerEntity)) return 0;
        if (weaponOnly.getValue()) {
            var item = mc.player.getMainHandStack().getItem();
            if (!(item instanceof SwordItem) && !(item instanceof AxeItem)) return 0;
        }
        if (!pulseOn2) return 0;
        double base = expand.getValue();
        if (jitter.getValue()) base += (rng.nextDouble() * 2 - 1) * 0.012;
        return Math.max(0, base);
    }
}
