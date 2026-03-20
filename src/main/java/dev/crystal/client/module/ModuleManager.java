package dev.crystal.client.module;

import dev.crystal.client.module.modules.combat.*;

import java.util.*;
import java.util.stream.Collectors;

public final class ModuleManager {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        register(new AutoCrystal());
        register(new AutoInventoryTotem());
        register(new AutoDoubleHand());
        register(new Hitboxes());
        register(new AnchorMacro());
        register(new CartAssist());
    }

    private void register(Module m) { modules.add(m); }

    public List<Module> getModules()              { return modules; }
    public List<Module> getEnabled()              { return modules.stream().filter(Module::isEnabled).collect(Collectors.toList()); }
    public List<Module> getByCategory(Category c) { return modules.stream().filter(m -> m.getCategory() == c).collect(Collectors.toList()); }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> cls) {
        return (T) modules.stream().filter(cls::isInstance).findFirst().orElse(null);
    }

    public Module get(String name) {
        return modules.stream()
                .filter(m -> m.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }
}
