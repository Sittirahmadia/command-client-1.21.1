package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.event.EventHandler;
import dev.crystal.client.event.events.RenderHudEvent;
import dev.crystal.client.module.Module;
import dev.crystal.client.util.RenderUtil;
import net.minecraft.client.MinecraftClient;

import java.util.Comparator;
import java.util.List;

/**
 * HudRenderer
 * • Watermark top-centre
 * • ArrayList right side — sorted by name length, animated width
 * • Info bar bottom-left — FPS, ping, coords
 */
public final class HudRenderer {

    private static final int PAD    = 4;
    private static final int ITEM_H = 11;
    private static final int BG     = 0xAA0B0B0F;

    private float[] animW = new float[64];
    private float   hue   = 0f;

    public HudRenderer() {
        CrystalClient.INSTANCE.bus.subscribe(this);
    }

    @EventHandler
    public void onRenderHud(RenderHudEvent e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        int W = mc.getWindow().getScaledWidth();
        int H = mc.getWindow().getScaledHeight();
        hue = (hue + 0.6f) % 360f;

        renderWatermark(e, W);
        renderArrayList(e, W);
        renderInfoBar(e, H, mc);
    }

    private void renderWatermark(RenderHudEvent e, int W) {
        String name = "Crystal";
        String ver  = " v1.0";
        int nw  = RenderUtil.textWidth(name);
        int tot = nw + RenderUtil.textWidth(ver) + PAD * 2;
        int x   = (W - tot) / 2, y = 4;

        RenderUtil.rect(e.ctx, x - PAD, y - 2, tot + PAD * 2, RenderUtil.textHeight() + 4, BG);
        RenderUtil.rect(e.ctx, x - PAD, y - 2, 2, RenderUtil.textHeight() + 4, hsvRgb(hue));
        RenderUtil.text(e.ctx, name, x, y, hsvRgb(hue), true);
        RenderUtil.text(e.ctx, ver,  x + nw, y, 0xFF888899, false);
    }

    private void renderArrayList(RenderHudEvent e, int W) {
        List<Module> enabled = CrystalClient.INSTANCE.modules.getEnabled();
        enabled.sort(Comparator.comparingInt(m -> -RenderUtil.textWidth(m.getName())));

        if (animW.length < enabled.size()) animW = new float[enabled.size() + 16];

        int y = 4;
        for (int i = 0; i < enabled.size(); i++) {
            Module mod = enabled.get(i);
            float target = RenderUtil.textWidth(mod.getName()) + PAD * 2f;
            animW[i] += (target - animW[i]) * 0.2f;
            int w = (int) animW[i], x = W - w;

            RenderUtil.rect(e.ctx, x, y, w, ITEM_H, BG);
            RenderUtil.rect(e.ctx, x, y, 2, ITEM_H, mod.getCategory().color);
            RenderUtil.text(e.ctx, mod.getName(),
                    x + PAD, y + (ITEM_H - RenderUtil.textHeight()) / 2,
                    0xFFDDDDEE, false);
            y += ITEM_H + 1;
        }
    }

    private void renderInfoBar(RenderHudEvent e, int H, MinecraftClient mc) {
        int fps  = mc.getCurrentFps();
        int ping = 0;
        var entry = mc.getNetworkHandler() != null
                ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) : null;
        if (entry != null) ping = entry.getLatency();

        var pos = mc.player.getBlockPos();
        String info = String.format("FPS %d  Ping %dms  %d / %d / %d",
                fps, ping, pos.getX(), pos.getY(), pos.getZ());

        int tw = RenderUtil.textWidth(info) + PAD * 2;
        int y  = H - RenderUtil.textHeight() - 6;
        RenderUtil.rect(e.ctx, 0, y - 2, tw, RenderUtil.textHeight() + 4, BG);
        RenderUtil.text(e.ctx, info, PAD, y, 0xFFAAAAAA, false);
    }

    private int hsvRgb(float h) {
        float c=1f, x=c*(1-Math.abs((h/60f)%2-1));
        float r,g,b;
        if(h<60){r=c;g=x;b=0;}else if(h<120){r=x;g=c;b=0;}else if(h<180){r=0;g=c;b=x;}
        else if(h<240){r=0;g=x;b=c;}else if(h<300){r=x;g=0;b=c;}else{r=c;g=0;b=x;}
        return 0xFF000000|((int)(r*255)<<16)|((int)(g*255)<<8)|(int)(b*255);
    }
}
