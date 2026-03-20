package dev.crystal.client.gui;

import dev.crystal.client.CrystalClient;
import dev.crystal.client.module.Category;
import dev.crystal.client.module.Module;
import dev.crystal.client.module.setting.Setting;
import dev.crystal.client.util.RenderUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Crystal ClickGUI — minimal single-window design
 *
 * Layout:
 *   ┌──────────────────────────────────────────────┐
 *   │  crystal               [Combat][Move][...] │
 *   ├─────────────────┬────────────────────────────┤
 *   │  module list    │  settings panel            │
 *   │  left click=on  │  (slides in on right click)│
 *   │  right click=⚙  │                            │
 *   └─────────────────┴────────────────────────────┘
 */
public final class ClickGUI extends Screen {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int BG        = 0xF0080810;
    private static final int SIDE      = 0xF00A0A14;
    private static final int SEP       = 0x18FFFFFF;
    private static final int HOV       = 0x10FFFFFF;
    private static final int ACT       = 0x16B0C8FF;
    private static final int TEXT      = 0xFFCCCCDD;
    private static final int TEXT_DIM  = 0xFF555566;
    private static final int TEXT_ON   = 0xFFB0C8FF;
    private static final int ACCENT    = 0xFF4A90D9;
    private static final int GREEN     = 0xFF3DBA6E;
    private static final int DARK      = 0xFF111118;

    // ── Sizes ─────────────────────────────────────────────────────────────────
    private static final int TAB_H     = 22;
    private static final int MOD_H     = 18;
    private static final int SET_H     = 15;
    private static final int LIST_W    = 160;
    private static final int SETT_W    = 190;
    private static final int PAD       = 6;
    private static final int MAX_H     = 360;

    // ── State ─────────────────────────────────────────────────────────────────
    private Category   activeTab  = Category.COMBAT;
    private Module     activeMod  = null;   // module whose settings are shown
    private int        listScroll = 0;
    private int        setScroll  = 0;

    // slider drag
    private Setting.NumberSetting dragging = null;
    private int dragBarX, dragBarW;

    public ClickGUI() { super(Text.empty()); }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private int gx() { return (width  - gw()) / 2; }
    private int gy() { return (height - gh()) / 2; }
    private int gw() { return activeMod != null ? LIST_W + 1 + SETT_W : LIST_W; }
    private int gh() { return TAB_H + Math.min(MAX_H, listContentH()) + 2; }

    private int listContentH() {
        return CrystalClient.INSTANCE.modules.getByCategory(activeTab).size() * MOD_H + 4;
    }

    private int listH()  { return gh() - TAB_H; }
    private int listX()  { return gx(); }
    private int listY()  { return gy() + TAB_H; }
    private int settX()  { return gx() + LIST_W + 1; }
    private int settY()  { return gy() + TAB_H; }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Backdrop
        ctx.fill(0, 0, width, height, 0x60000000);

        int gx = gx(), gy = gy(), gw = gw(), gh = gh();

        // Window background
        ctx.fill(gx, gy, gx + gw, gy + gh, BG);

        // Left accent edge (category colour)
        ctx.fill(gx, gy, gx + 2, gy + gh, activeTab.color);

        // ── Header bar ────────────────────────────────────────────────────────
        ctx.fill(gx, gy, gx + gw, gy + TAB_H, DARK);
        ctx.fill(gx, gy + TAB_H - 1, gx + gw, gy + TAB_H, SEP);

        // Wordmark
        RenderUtil.text(ctx, "crystal", gx + PAD + 2, gy + (TAB_H - RenderUtil.textHeight()) / 2, ACCENT, false);

        // Category tabs (right-aligned in header)
        renderTabs(ctx, gx, gy, gw, mx, my);

        // ── Module list ───────────────────────────────────────────────────────
        renderModuleList(ctx, mx, my);

        // ── Settings panel ────────────────────────────────────────────────────
        if (activeMod != null) {
            ctx.fill(settX() - 1, settY(), settX(), settY() + listH(), SEP);
            renderSettings(ctx, mx, my);
        }
    }

    private void renderTabs(DrawContext ctx, int gx, int gy, int gw, int mx, int my) {
        Category[] cats = Category.values();
        int totalW = 0;
        int[] widths = new int[cats.length];
        for (int i = 0; i < cats.length; i++) {
            widths[i] = RenderUtil.textWidth(cats[i].name) + PAD * 2;
            totalW += widths[i];
        }

        int tx = gx + gw - totalW - 2;
        for (int i = 0; i < cats.length; i++) {
            Category cat = cats[i];
            int tw = widths[i];
            boolean sel = cat == activeTab;
            boolean hov = mx >= tx && mx < tx + tw && my >= gy && my < gy + TAB_H;

            if (sel) {
                // Underline indicator
                ctx.fill(tx, gy + TAB_H - 2, tx + tw, gy + TAB_H - 1, cat.color);
                RenderUtil.text(ctx, cat.name, tx + PAD, gy + (TAB_H - RenderUtil.textHeight()) / 2, TEXT, false);
            } else {
                if (hov) ctx.fill(tx, gy, tx + tw, gy + TAB_H - 1, HOV);
                RenderUtil.text(ctx, cat.name, tx + PAD, gy + (TAB_H - RenderUtil.textHeight()) / 2, TEXT_DIM, false);
            }
            tx += tw;
        }
    }

    private void renderModuleList(DrawContext ctx, int mx, int my) {
        List<Module> mods = CrystalClient.INSTANCE.modules.getByCategory(activeTab);
        int x = listX(), y = listY(), w = LIST_W, h = listH();
        int cy = y + 2 - listScroll;

        for (Module mod : mods) {
            if (cy + MOD_H > y + h + MOD_H) break;
            if (cy + MOD_H < y) { cy += MOD_H; continue; }

            boolean en  = mod.isEnabled();
            boolean hov = mx >= x && mx < x + w && my >= cy && my < cy + MOD_H;
            boolean sel = mod == activeMod;

            if (sel) ctx.fill(x + 2, cy, x + w, cy + MOD_H, ACT);
            else if (hov) ctx.fill(x + 2, cy, x + w, cy + MOD_H, HOV);

            // Left bar — enabled indicator
            ctx.fill(x + 2, cy + 3, x + 4, cy + MOD_H - 3, en ? activeTab.color : 0x22FFFFFF);

            // Name
            RenderUtil.text(ctx, mod.getName(),
                    x + PAD + 2, cy + (MOD_H - RenderUtil.textHeight()) / 2,
                    en ? TEXT_ON : (hov ? TEXT : TEXT_DIM), false);

            // Toggle pill (right side)
            drawPill(ctx, x + w - 20, cy + (MOD_H - 8) / 2, en);

            cy += MOD_H;
        }

        // Scrollbar
        int total = mods.size() * MOD_H;
        if (total > h) {
            int sbH = Math.max(16, h * h / total);
            int sbY = y + listScroll * (h - sbH) / Math.max(1, total - h);
            ctx.fill(x + w - 2, sbY, x + w, sbY + sbH, 0x44AAAACC);
        }
    }

    private void renderSettings(DrawContext ctx, int mx, int my) {
        int x = settX(), y = settY(), w = SETT_W, h = listH();
        List<Setting<?>> settings = activeMod.getSettings();

        // Module name header
        ctx.fill(x, y, x + w, y + MOD_H + 2, DARK);
        RenderUtil.text(ctx, activeMod.getName(), x + PAD, y + (MOD_H - RenderUtil.textHeight()) / 2 + 1, TEXT_ON, true);
        ctx.fill(x, y + MOD_H + 2, x + w, y + MOD_H + 3, SEP);

        if (settings.isEmpty()) {
            RenderUtil.text(ctx, "no settings", x + PAD, y + MOD_H + 8, TEXT_DIM, false);
            return;
        }

        int cy = y + MOD_H + 4 - setScroll;

        for (Setting<?> s : settings) {
            int rowH = s instanceof Setting.NumberSetting ? SET_H + 8 : SET_H;
            if (cy + rowH < y || cy > y + h) { cy += rowH; continue; }

            boolean hov = mx >= x && mx < x + w && my >= cy && my < cy + rowH;
            if (hov) ctx.fill(x, cy, x + w, cy + rowH, HOV);

            if (s instanceof Setting.BoolSetting bs) {
                boolean val = bs.getValue();
                RenderUtil.text(ctx, s.getName(), x + PAD, cy + (SET_H - RenderUtil.textHeight()) / 2, TEXT, false);
                drawPill(ctx, x + w - 22, cy + (SET_H - 8) / 2, val);
                cy += SET_H;

            } else if (s instanceof Setting.NumberSetting ns) {
                // Label + value on one line
                String valStr = ns.getStep() >= 1
                        ? String.valueOf(ns.intValue())
                        : String.format("%.2f", ns.getValue());
                RenderUtil.text(ctx, s.getName(), x + PAD, cy + 2, TEXT, false);
                RenderUtil.text(ctx, valStr, x + w - RenderUtil.textWidth(valStr) - PAD, cy + 2, ACCENT, false);

                // Slim slider bar
                int bx = x + PAD, bw = w - PAD * 2, by2 = cy + SET_H + 1;
                ctx.fill(bx, by2, bx + bw, by2 + 3, 0x22FFFFFF);
                double pct = (ns.getValue() - ns.getMin()) / (ns.getMax() - ns.getMin());
                int fill = Math.max(4, (int)(bw * pct));
                // Gradient fill
                RenderUtil.gradientRect(ctx, bx, by2, fill, 3, 0xFF3A7BD5, 0xFF5BACF0);
                // Knob
                int kx = bx + fill - 2;
                ctx.fill(kx, by2 - 1, kx + 4, by2 + 4, 0xFFFFFFFF);
                cy += SET_H + 8;

            } else if (s instanceof Setting.ModeSetting<?> ms) {
                RenderUtil.text(ctx, s.getName(), x + PAD, cy + (SET_H - RenderUtil.textHeight()) / 2, TEXT, false);
                String mode = ms.getValue().toString();
                int mw = RenderUtil.textWidth(mode);
                ctx.fill(x + w - mw - PAD * 2 - 4, cy + 2, x + w - PAD, cy + SET_H - 2, DARK);
                RenderUtil.text(ctx, mode, x + w - mw - PAD, cy + (SET_H - RenderUtil.textHeight()) / 2, ACCENT, false);
                cy += SET_H;
            }
        }
    }

    // ── Toggle pill ───────────────────────────────────────────────────────────

    private void drawPill(DrawContext ctx, int x, int y, boolean on) {
        int w = 18, h = 8;
        ctx.fill(x, y, x + w, y + h, on ? 0xFF2A5A3A : 0xFF1A1A26);
        // Knob
        int kx = on ? x + w - 8 : x + 2;
        ctx.fill(kx, y + 1, kx + 6, y + h - 1, on ? GREEN : 0xFF444455);
    }

    // ── Mouse events ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int imx = (int) mx, imy = (int) my;
        clickTabs(imx, imy, btn);
        clickModules(imx, imy, btn);
        if (activeMod != null) clickSettings(imx, imy, btn);
        return true;
    }

    private void clickTabs(int mx, int my, int btn) {
        if (btn != 0) return;
        Category[] cats = Category.values();
        int gx = gx(), gy = gy(), gw = gw();
        int totalW = 0;
        int[] widths = new int[cats.length];
        for (int i = 0; i < cats.length; i++) { widths[i] = RenderUtil.textWidth(cats[i].name) + PAD * 2; totalW += widths[i]; }
        int tx = gx + gw - totalW - 2;
        for (int i = 0; i < cats.length; i++) {
            if (mx >= tx && mx < tx + widths[i] && my >= gy && my < gy + TAB_H) {
                activeTab = cats[i]; listScroll = 0; activeMod = null; return;
            }
            tx += widths[i];
        }
    }

    private void clickModules(int mx, int my, int btn) {
        List<Module> mods = CrystalClient.INSTANCE.modules.getByCategory(activeTab);
        int x = listX(), y = listY(), w = LIST_W;
        int cy = y + 2 - listScroll;
        for (Module mod : mods) {
            if (my >= cy && my < cy + MOD_H && mx >= x && mx < x + w) {
                if (btn == 0) mod.toggle();
                else if (btn == 1) { activeMod = (activeMod == mod) ? null : mod; setScroll = 0; }
                return;
            }
            cy += MOD_H;
        }
    }

    private void clickSettings(int mx, int my, int btn) {
        if (btn != 0 || activeMod == null) return;
        List<Setting<?>> settings = activeMod.getSettings();
        int x = settX(), y = settY(), w = SETT_W;
        int cy = y + MOD_H + 4 - setScroll;

        for (Setting<?> s : settings) {
            int rowH = s instanceof Setting.NumberSetting ? SET_H + 8 : SET_H;
            if (my >= cy && my < cy + rowH && mx >= x && mx < x + w) {
                if (s instanceof Setting.BoolSetting bs) bs.toggle();
                else if (s instanceof Setting.NumberSetting ns) {
                    int bx = x + PAD, bw = w - PAD * 2;
                    int by2 = cy + SET_H + 1;
                    if (my >= by2 - 2 && my <= by2 + 5) {
                        float pct = Math.max(0, Math.min(1, (float)(mx - bx) / bw));
                        ns.setValue(ns.getMin() + (ns.getMax() - ns.getMin()) * pct);
                        dragging = ns; dragBarX = bx; dragBarW = bw;
                    }
                } else if (s instanceof Setting.ModeSetting<?> ms) ms.cycle();
                return;
            }
            cy += rowH;
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (btn == 0 && dragging != null) {
            float pct = Math.max(0, Math.min(1, (float)(mx - dragBarX) / dragBarW));
            dragging.setValue(dragging.getMin() + (dragging.getMax() - dragging.getMin()) * pct);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = null;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vx) {
        int imx = (int) mx, imy = (int) my;
        int x = listX(), y = listY();

        if (activeMod != null && imx >= settX()) {
            setScroll = Math.max(0, setScroll - (int)(vx * SET_H));
        } else if (imx >= x && imx < x + LIST_W && imy >= y) {
            int maxScroll = Math.max(0,
                    CrystalClient.INSTANCE.modules.getByCategory(activeTab).size() * MOD_H - listH() + 4);
            listScroll = Math.max(0, Math.min(listScroll - (int)(vx * MOD_H), maxScroll));
        }
        return true;
    }

    @Override public boolean shouldPause() { return false; }
}
