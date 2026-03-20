package dev.crystal.client;

import dev.crystal.client.event.EventBus;
import dev.crystal.client.gui.ChatCommandHandler;
import dev.crystal.client.gui.HudRenderer;
import dev.crystal.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrystalClient implements ClientModInitializer {

    public static final String  NAME    = "Crystal";
    public static final String  VERSION = "1.0.0";
    public static final Logger  LOG     = LoggerFactory.getLogger(NAME);

    public static CrystalClient INSTANCE;
    public static MinecraftClient MC = MinecraftClient.getInstance();

    public EventBus            bus;
    public ModuleManager       modules;
    public ChatCommandHandler  commands;
    public HudRenderer         hud;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;

        bus      = new EventBus();
        modules  = new ModuleManager();
        commands = new ChatCommandHandler();
        hud      = new HudRenderer();

        LOG.info("[{}] Loaded {} modules", NAME, modules.getModules().size());
    }
}
