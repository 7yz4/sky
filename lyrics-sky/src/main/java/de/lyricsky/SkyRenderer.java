package de.lyricsky;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

public final class SkyRenderer {
    private SkyRenderer() {}

    public static void render(WorldRenderContext ctx) {
        Config cfg = Config.get();
        if (!cfg.enabled) return;

        LyricsService svc = LyricsService.get();
        String cur = svc.currentLine();
        String next = svc.nextLine();
        boolean hasCur = cur != null && !cur.isBlank();
        boolean hasNext = next != null && !next.isBlank();
        if (!hasCur && !hasNext) return;

        MatrixStack m = ctx.matrixStack();
        if (m == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        Camera cam = ctx.camera();
        Vec3d c = cam.getPos();

        // Position am Himmel: feste Himmelsrichtung (azimuth) + Höhenwinkel (elevation)
        double yaw = Math.toRadians(cfg.azimuth);
        double el = Math.toRadians(cfg.elevation);
        double dx = -Math.sin(yaw) * Math.cos(el);
        double dz = Math.cos(yaw) * Math.cos(el);
        double dy = Math.sin(el);
        double px = dx * cfg.distance;
        double py = dy * cfg.distance;
        double pz = dz * cfg.distance;

        float s = 0.025f * (cfg.distance / 12f) * cfg.scale;
        VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();

        m.push();
        // Kamera-relativ: Weltposition = Kameraposition + Offset  =>  Translation = Offset
        m.translate(px, py, pz);
        m.multiply(cam.getRotation()); // Billboard: immer dem Spieler zugewandt
        m.scale(s, -s, s);

        if (hasCur) {
            float w = tr.getWidth(cur);
            tr.draw(cur, -w / 2f, 0, 0xFFFFFFFF, false, m.peek().getPositionMatrix(), vcp,
                    TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        }
        if (hasNext) {
            m.push();
            m.translate(0, 14, 0);
            m.scale(0.7f, 0.7f, 0.7f);
            float w = tr.getWidth(next);
            tr.draw(next, -w / 2f, 0, 0xAAAAAAAA, false, m.peek().getPositionMatrix(), vcp,
                    TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
            m.pop();
        }
        m.pop();
        vcp.draw();
    }
}
