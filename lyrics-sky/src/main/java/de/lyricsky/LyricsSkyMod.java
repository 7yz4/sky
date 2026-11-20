package de.lyricsky;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LyricsSkyMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("lyricssky");

    @Override
    public void onInitializeClient() {
        Config.load();
        LyricsService.get().start();
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SkyRenderer::render);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> ModCommands.register(dispatcher));
    }

    /** Thread-sicher eine Chatnachricht an den Spieler schicken. */
    public static void chat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(Text.literal("[Lyrics] " + msg), false);
        });
    }
}
