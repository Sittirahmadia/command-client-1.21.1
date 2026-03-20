package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;

/**
 * ChatCommandHandler
 *
 * Commands (prefix: ;;):
 *   ;; <module>              — toggle module on/off
 *   ;;list                   — list all modules and status
 *   ;;settings <module>      — show all settings of a module
 *   ;;set <module> <setting> <value> — change a setting value
 *   ;;help                   — show command list
 */
public final class ChatCommandHandler {

    private static final String PREFIX    = ";;";
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    /**
     * Process a raw chat message.
     * @return true if the message was a command (cancel sending to server)
     */
    public boolean handle(String message) {
        if (!message.startsWith(PREFIX)) return false;
        String input = message.substring(PREFIX.length()).trim();

        if (input.isEmpty()) {
            send("§6§lCrystal Client §7— type §f;;help §7for commands");
            return true;
        }

        String[] parts = input.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help"     -> cmdHelp();
            case "list"     -> cmdList();
            case "settings" -> cmdSettings(parts);
            case "set"      -> cmdSet(parts);
            default         -> cmdToggle(cmd);
        }
        return true;
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    private void cmdHelp() {
        send("§6§lCrystal Client §7— Commands (prefix §f;;§7):");
        send("§f;; <module>                §7— Toggle module");
        send("§f;;list                     §7— Show all modules");
        send("§f;;settings <module>        §7— Show module settings");
        send("§f;;set <module> <setting> <value> §7— Change setting");
        send("§f;;help                     §7— Show this message");
        send("");
        send("§7Example: §f;;AutoCrystal");
        send("§7Example: §f;;settings AutoCrystal");
        send("§7Example: §f;;set AutoCrystal Place Delay 3");
    }

    private void cmdList() {
        send("§6Modules:");
        for (Module m : CrystalClient.INSTANCE.modules.getModules()) {
            String status = m.isEnabled() ? "§a✔ ON" : "§c✘ OFF";
            send("  " + status + " §f" + m.getName() + " §7[" + m.getCategory().name + "]");
        }
    }

    private void cmdSettings(String[] parts) {
        if (parts.length < 2) { send("§cUsage: ;;settings <module>"); return; }
        String name = join(parts, 1);
        Module mod  = CrystalClient.INSTANCE.modules.get(name);
        if (mod == null) { send("§cModule not found: §f" + name); return; }

        send("§6Settings for §f" + mod.getName() + "§6:");
        List<Setting<?>> settings = mod.getSettings();
        if (settings.isEmpty()) { send("  §7(no settings)"); return; }

        for (Setting<?> s : settings) {
            String val = formatValue(s);
            send("  §f" + s.getName() + " §7= §e" + val
                    + " §8— " + s.getDescription());
        }
        send("§7Use §f;;set " + mod.getName() + " <setting> <value> §7to change.");
    }

    private void cmdSet(String[] parts) {
        // ;;set <module> <setting...> <value>
        if (parts.length < 4) { send("§cUsage: ;;set <module> <setting> <value>"); return; }

        // Try each split: parts[1]=module, parts[2..n-1]=setting, parts[n]=value
        String valueStr = parts[parts.length - 1];
        String moduleName = parts[1];
        Module mod = CrystalClient.INSTANCE.modules.get(moduleName);
        if (mod == null) { send("§cModule not found: §f" + moduleName); return; }

        String settingName = join(parts, 2, parts.length - 1);
        Setting<?> setting = mod.getSetting(settingName);
        if (setting == null) { send("§cSetting not found: §f" + settingName + " §cin §f" + mod.getName()); return; }

        if (!applySetting(setting, valueStr)) {
            send("§cInvalid value §f" + valueStr + " §cfor §f" + setting.getName());
            return;
        }
        send("§aSet §f" + mod.getName() + " / " + setting.getName() + " §a= §e" + formatValue(setting));
    }

    private void cmdToggle(String name) {
        Module mod = CrystalClient.INSTANCE.modules.get(name);
        if (mod == null) { send("§cModule not found: §f" + name + ". Use §f;;list §cto see all."); return; }
        mod.toggle();
        String status = mod.isEnabled() ? "§aON" : "§cOFF";
        send("§f" + mod.getName() + " §7is now " + status);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private boolean applySetting(Setting<?> setting, String value) {
        try {
            if (setting instanceof Setting.BoolSetting bs) {
                if (value.equalsIgnoreCase("true")  || value.equalsIgnoreCase("on")  || value.equals("1")) { bs.setValue(true);  return true; }
                if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equals("0")) { bs.setValue(false); return true; }
                return false;
            }
            if (setting instanceof Setting.NumberSetting ns) {
                ns.setValue(Double.parseDouble(value));
                return true;
            }
            if (setting instanceof Setting.ModeSetting ms) {
                for (var enumVal : ms.getValues()) {
                    if (enumVal.name().equalsIgnoreCase(value)) {
                        //noinspection unchecked
                        ms.setValue(enumVal);
                        return true;
                    }
                }
                return false;
            }
        } catch (NumberFormatException ignored) {}
        return false;
    }

    private String formatValue(Setting<?> s) {
        if (s instanceof Setting.NumberSetting ns) {
            double v = ns.getValue();
            return ns.getStep() >= 1 ? String.valueOf(ns.intValue()) : String.format("%.2f", v);
        }
        return String.valueOf(s.getValue());
    }

    private void send(String message) {
        if (MC.player == null) return;
        MC.player.sendMessage(Text.literal("§8[§6CC§8] §r" + message), false);
    }

    private String join(String[] parts, int from) {
        return join(parts, from, parts.length);
    }

    private String join(String[] parts, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) sb.append(" ");
            sb.append(parts[i]);
        }
        return sb.toString();
    }
}
