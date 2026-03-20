package dev.crystal.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class RenderUtil {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    // ── 2D ───────────────────────────────────────────────────────────────────

    public static void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + h, color);
    }

    public static void gradientRect(DrawContext ctx, int x, int y, int w, int h, int top, int bot) {
        ctx.fillGradient(x, y, x + w, y + h, top, bot);
    }

    public static void text(DrawContext ctx, String text, int x, int y, int color, boolean shadow) {
        TextRenderer tr = MC.textRenderer;
        if (shadow) ctx.drawTextWithShadow(tr, text, x, y, color);
        else        ctx.drawText(tr, text, x, y, color, false);
    }

    public static int textWidth(String text) { return MC.textRenderer.getWidth(text); }
    public static int textHeight()           { return MC.textRenderer.fontHeight; }

    // ── Color helpers ─────────────────────────────────────────────────────────

    public static int argb(int a, int r, int g, int b) { return (a<<24)|(r<<16)|(g<<8)|b; }
    public static int withAlpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    public static int lerpColor(int c1, int c2, float t) {
        int a=(int)(((c1>>24)&0xFF)+(((c2>>24)&0xFF)-((c1>>24)&0xFF))*t);
        int r=(int)(((c1>>16)&0xFF)+(((c2>>16)&0xFF)-((c1>>16)&0xFF))*t);
        int g=(int)(((c1>> 8)&0xFF)+(((c2>> 8)&0xFF)-((c1>> 8)&0xFF))*t);
        int b=(int)(( c1     &0xFF)+((c2      &0xFF)-( c1     &0xFF))*t);
        return argb(a,r,g,b);
    }

    // ── 3D entity box ─────────────────────────────────────────────────────────

    public static void drawEntityBox(MatrixStack matrices, Entity entity, float delta, int color) {
        if (MC.gameRenderer == null) return;
        Vec3d cam = MC.gameRenderer.getCamera().getPos();
        Vec3d pos = entity.getLerpedPos(delta);
        float hw = entity.getWidth() / 2f + 0.05f;
        float h  = entity.getHeight() + 0.1f;

        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        float rv=((color>>16)&0xFF)/255f, gv=((color>>8)&0xFF)/255f, bv=(color&0xFF)/255f, av=((color>>24)&0xFF)/255f;

        float[][] corners = {{-hw,0,-hw},{hw,0,-hw},{hw,0,hw},{-hw,0,hw},{-hw,h,-hw},{hw,h,-hw},{hw,h,hw},{-hw,h,hw}};
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] e : edges) {
            float[] p1=corners[e[0]], p2=corners[e[1]];
            buf.vertex(mat,p1[0],p1[1],p1[2]).color(rv,gv,bv,av);
            buf.vertex(mat,p2[0],p2[1],p2[2]).color(rv,gv,bv,av);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        matrices.pop();
    }
}
