package de.lyricsky;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

public final class SkyRenderer {
    private static final float ANIM_MS = 450f;

    private static boolean init;
    private static float az;           // aktuelle (geglättete) Himmelsrichtung in Grad
    private static long lastFrame;
    private static int lastIndex = -2;
    private static int prevIndex = -1;
    private static long changeNanos;

    private SkyRenderer() {}

    public static void render(WorldRenderContext ctx) {
        Config cfg = Config.get();
        if (!cfg.enabled) return;
        MatrixStack m = ctx.matrixStack();
        if (m == null) return;

        long now = System.nanoTime();
        Camera cam = ctx.camera();

        // ---- Richtung träge nachziehen (Deadzone + weiches Ease) ----
        float dt = init ? Math.min((now - lastFrame) / 1e9f, 0.1f) : 0f;
        lastFrame = now;
        float target = cfg.followView ? cam.getYaw() : cfg.azimuth;
        if (!init) { az = target; init = true; }
        float diff = MathHelper.wrapDegrees(target - az);
        float excess = Math.abs(diff) - (cfg.followView ? cfg.deadzone : 0f);
        if (excess > 0f) az += Math.signum(diff) * excess * (1f - (float) Math.exp(-dt * 2.2f));
        az = MathHelper.wrapDegrees(az);

        // ---- Zeilenwechsel erkennen ----
        LyricsService svc = LyricsService.get();
        int idx = svc.lineIndex();
        if (idx != lastIndex) {
            prevIndex = lastIndex >= 0 ? lastIndex : -1;
            lastIndex = idx;
            changeNanos = now;
        }
        float e = Math.min((now - changeNanos) / 1e6f / ANIM_MS, 1f);
        float ease = 1f - (1f - e) * (1f - e) * (1f - e);

        String cur = idx >= 0 ? svc.textAt(idx) : "";
        String next = idx >= 0 ? svc.textAt(idx + 1) : "";
        String old = (e < 1f && prevIndex >= 0) ? svc.textAt(prevIndex) : "";
        if (cur.isBlank() && next.isBlank() && old.isBlank()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();

        // ---- Position am Himmel ----
        double azRad = Math.toRadians(az);
        double el = Math.toRadians(cfg.elevation);
        double t = now / 1e9;
        double sway = Math.sin(t * 0.7) * 0.012 * cfg.distance; // leichtes Schweben
        double px = -Math.sin(azRad) * Math.cos(el) * cfg.distance;
        double pz = Math.cos(azRad) * Math.cos(el) * cfg.distance;
        double py = Math.sin(el) * cfg.distance + sway;

        float s = 0.025f * (cfg.distance / 12f) * cfg.scale;

        m.push();
        m.translate(px, py, pz);
        // Dem Spieler zugewandt, aber unabhängig von seinem Blick: nur von der eigenen Himmelsrichtung abhängig
        m.multiply(new Quaternionf().rotationYXZ((float) (Math.PI - azRad), (float) -el, 0f));
        m.scale(s, -s, s);

        if (!old.isBlank()) drawLine(m, tr, vcp, old, -18f * ease, 1f - 0.1f * ease, 1f - ease, 0xFFFFFF);
        if (!cur.isBlank()) drawLine(m, tr, vcp, cur, 14f * (1f - ease), 0.7f + 0.3f * ease, ease, 0xFFFFFF);
        if (!next.isBlank()) drawLine(m, tr, vcp, next, 14f + 8f * (1f - ease), 0.7f, 0.6f * ease, 0xCCCCCC);

        m.pop();
        vcp.draw();
    }

    private static void drawLine(MatrixStack m, TextRenderer tr, VertexConsumerProvider vcp,
                                 String text, float y, float scale, float alpha, int rgb) {
        if (alpha < 0.06f) return; // sehr kleine Alpha-Werte rendert Minecraft sonst voll deckend
        int color = (Math.round(Math.min(alpha, 1f) * 255f) << 24) | rgb;
        m.push();
        m.translate(0, y, 0);
        m.scale(scale, scale, scale);
        float w = tr.getWidth(text);
        tr.draw(text, -w / 2f, 0, color, false, m.peek().getPositionMatrix(), vcp,
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        m.pop();
    }
}
