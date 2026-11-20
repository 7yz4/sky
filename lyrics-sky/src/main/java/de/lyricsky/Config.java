package de.lyricsky;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Wird in .minecraft/config/lyricssky.json gespeichert (Tokens im Klartext!). */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public String clientId = "";
    public String accessToken = "";
    public String refreshToken = "";
    public long expiresAtMs = 0;

    public boolean enabled = true;
    public int offsetMs = 0;        // + = Lyrics früher, - = später
    public float azimuth = 0f;      // Blickrichtung (Yaw) in Grad, 0 = Süden
    public float elevation = 35f;  // Höhe über dem Horizont in Grad
    public float distance = 60f;   // Abstand in Blöcken
    public float scale = 1f;

    public static Config get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("lyricssky.json");
    }

    public static synchronized void load() {
        try {
            Path p = file();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) instance = c;
            } else {
                save();
            }
        } catch (Exception e) {
            LyricsSkyMod.LOGGER.error("Config konnte nicht geladen werden", e);
        }
    }

    public static synchronized void save() {
        try {
            Files.writeString(file(), GSON.toJson(instance));
        } catch (Exception e) {
            LyricsSkyMod.LOGGER.error("Config konnte nicht gespeichert werden", e);
        }
    }
}
